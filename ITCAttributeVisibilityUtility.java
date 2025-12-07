package custom;

import org.apache.logging.log4j.Logger;

import com.ptc.core.components.descriptor.ModelContext;
import com.ptc.core.components.factory.dataUtilities.DefaultDataUtility;
import com.ptc.core.components.rendering.guicomponents.AttributeInputCompositeComponent;
import com.ptc.core.components.rendering.guicomponents.StringInputComponent;
import com.ptc.core.meta.type.common.impl.DefaultTypeInstance;
import com.ptc.core.ui.resources.ComponentMode;
import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.fc.WTReference;
import wt.log4j.LogR;
import wt.util.WTException;
import wt.vc.VersionControlHelper;

/**
 * This Data Utility is to hide Complexity attribute from the Edit UI,
 * 
 * Complexity attribute will be available on Edit UI for the first version of
 * SR DOcuemnt which are in In work or UnderReview state.
 * 
 * @author 51458
 *
 */
public class ITCAttributeVisibilityUtility extends DefaultDataUtility {
	private static final String INWORK_STATE = "In Work";
	private static final String UNDERREVIEW_STATE = "Under Review";
	private static final Logger LOGGER = LogR.getLogger(ITCAttributeVisibilityUtility.class.getName());
	
	/**
	 * 
	 * @param componentId
	 * @param object
	 * @param mc
	 * @return obj
	 * @throws WTException
	 * 
	 */

	@Override
	public Object getDataValue(String componentId, Object object, ModelContext mc) throws WTException {
		LOGGER.debug("==> ITCEditComplexityDataUtility::getDataValue()");
		Object obj = super.getDataValue(componentId, object, mc);
		ComponentMode mode = mc.getDescriptorMode();

		if (obj instanceof AttributeInputCompositeComponent && mode.equals(ComponentMode.EDIT)) {
			DefaultTypeInstance typeIns = (DefaultTypeInstance) object;
			ReferenceFactory ref = new ReferenceFactory();
			WTReference wtref = ref.getReference(typeIns.getPersistenceIdentifier());
			Persistable per = wtref.getObject();
			if (per instanceof WTDocument) {
				WTDocument doc = (WTDocument) per;
				QueryResult allVersions = VersionControlHelper.service.allVersionsFrom(doc);
				int noOfVersion = allVersions.size();
				LOGGER.debug("Number of Revisions: %s", noOfVersion);
				String state = doc.getState().getState().getDisplay();
				if (!((state.equalsIgnoreCase(INWORK_STATE) || state.equalsIgnoreCase(UNDERREVIEW_STATE))
						&& noOfVersion == 2)) {
					AttributeInputCompositeComponent comp = (AttributeInputCompositeComponent) obj;
					StringInputComponent stringComponent = (StringInputComponent) comp.getValueInputComponent();
					stringComponent.setEditable(false);
					LOGGER.debug("Complexity Attribute is not editable");
					obj = stringComponent;
				}
			}
		}
		LOGGER.debug("<== ITCEditComplexityDataUtility::getDataValue()");
		return obj;
	}

}
