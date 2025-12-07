package custom;

import java.util.Locale;

import wt.doc.WTDocument;
import wt.util.WTException;
import wt.session.SessionServerHelper;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.factory.dataUtilities.DefaultDataUtility;
import com.ptc.core.components.rendering.AbstractGuiComponent;
import com.ptc.core.components.rendering.guicomponents.TextDisplayComponent;

import oracle.sql.ArrayDescriptor;

/**
 * ITCAttributeVisibilityUtility
 * - Show attribute "datau.net.capdir.ITC" only when WTDocument is inside Product "Design Hub"
 * - Otherwise return an empty TextDisplayComponent (effectively hides)
 *
 * NOTE: adjust imports (ArrayDescriptor, DefaultDataUtility) if your Windchill version uses different packages.
 */
public class ProductDataUtility extends DefaultDataUtility {

    private static final String ATTR_INTERNAL_NAME = "datau.net.capdir.ITC";
    private static final String TARGET_PRODUCT = "Design Hub";

    /**
     * Common `getDataValue` callback signature used by many Windchill versions:
     * (String componentId, Object datum, ArrayDescriptor descriptor, Locale locale)
     *
     * If your Windchill uses a slightly different signature, adapt accordingly.
     */
    public AbstractGuiComponent getDataValue(String componentId, Object datum,
                                             ArrayDescriptor descriptor, Locale locale) throws WTException {

        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        try {
            // 1) datum is usually an ObjectBean (UI wrapper). Try to extract domain object robustly.
            if (!(datum instanceof ObjectBean)) {
                // not a domain wrapper -> default behavior (return empty to hide or delegate)
                return new TextDisplayComponent(""); // hide
            }

            ObjectBean bean = (ObjectBean) datum;
            Object raw = bean.getObject(); // may return WTDocument, WTPart, etc.

            if (!(raw instanceof WTDocument)) {
                // not a document -> hide attribute
                return new TextDisplayComponent("");
            }

            WTDocument doc = (WTDocument) raw;

            // 2) find container / product name
            String containerName = null;
            try {
                if (doc.getContainer() != null && doc.getContainer().getName() != null) {
                    containerName = doc.getContainer().getName();
                }
            } catch (Exception e) {
                containerName = null;
            }

            // 3) If not target product -> hide
            if (containerName == null || !TARGET_PRODUCT.equalsIgnoreCase(containerName)) {
                return new TextDisplayComponent("");
            }

            // 4) If here -> doc is in "Design Hub", show the attribute value.
            //    How to fetch the attribute value depends on how attribute is stored (IBA or custom attribute).
            //    We'll try a simple approach: use ObjectBean.getValue(componentId) if available, otherwise fallback to empty.
            try {
                // Many UI data utilities expect that the attribute value is already available in the descriptor/context.
                // If not, you can load the attribute via IBA APIs or by using PersistenceHelper to refresh the object.
                Object val = null;
                try {
                } catch (Throwable ignore) {
                    // fallback: try reflection or IBA access if you have it
                    val = null;
                }

                String display = (val != null) ? val.toString() : "";

                // return a simple text component with attribute value (empty string will show nothing)
                return new TextDisplayComponent(display);

            } catch (Exception ex) {
                // if fetching value fails, return empty (do not crash UI)
                return new TextDisplayComponent("");
            }

        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
        }
    }

    // If your DefaultDataUtility declares other overloaded getDataValue signatures,
    // you may implement them similarly (mirror the above logic).
}
