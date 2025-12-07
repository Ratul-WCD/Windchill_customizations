package custom.formprocessor;

import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import wt.doc.WTDocument;
import wt.log4j.LogR;
import wt.session.SessionHelper;
import wt.util.WTException;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.forms.FormResultAction;
import com.ptc.core.components.util.FeedbackMessage;
import com.ptc.core.ui.resources.FeedbackType;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import com.ptc.windchill.enterprise.object.ReIdentifyConstants;
import com.ptc.windchill.enterprise.object.forms.RenameObjectFormProcessor;

import com.ptc.windchill.enterprise.object.util.ReIdentifyHelper;

public class ModifiedRenameObjectFormProcessor extends RenameObjectFormProcessor {

    private static final Logger LOGGER =
            LogR.getLogger(ModifiedRenameObjectFormProcessor.class.getName());

   
	@Override
    public FormResult preProcess(NmCommandBean clientData, List<ObjectBean> objectList) throws WTException {

        // SOP-1: Entry log for processor
        System.out.println("==== SOP-1: Rename PreProcess START ====");
        LOGGER.info("SOP-1: Rename PreProcess started.");

        // SOP-2: Resolve target object from NmCommandBean (correct way in preProcess)
        Object obj = null;
        try {
            obj = clientData.getPrimaryOid().getRef();
            System.out.println("SOP-2: Object reference resolved from NmCommandBean.");
            LOGGER.info("SOP-2: Object reference resolved.");
        } catch (Exception e) {
            System.out.println("SOP-2(ERROR): Failed to resolve object from NmCommandBean.");
            LOGGER.error("SOP-2(ERROR): Unable to resolve object from NmCommandBean.", e);
        }

        // SOP-3: Validate object existence
        if (obj == null) {
            System.out.println("SOP-3: Object is NULL → skipping custom validation.");
            LOGGER.warn("SOP-3: Target object is NULL in preProcess.");
            return super.preProcess(clientData, objectList);
        }

        System.out.println("SOP-3: Object class = " + obj.getClass().getName());
        LOGGER.info("SOP-3: Target object class = {}", obj.getClass().getName());

        // SOP-4: Apply validation only for WTDocument
        if (obj instanceof WTDocument) {

            System.out.println("SOP-4: Object is WTDocument → applying rename validation.");
            LOGGER.info("SOP-4: WTDocument identified for rename validation.");

            WTDocument doc = (WTDocument) obj;

            // SOP-5: Fetch old (existing) name from DB
            String oldName = doc.getName();
            System.out.println("SOP-5: Old name from DB = " + oldName);
            LOGGER.info("SOP-5: Old name from DB = {}", oldName);

            // SOP-6: Fetch NEW name entered by user from Rename Wizard UI
            String newName = ReIdentifyHelper.getSelectedTextBoxValueFromForm(
                    clientData,
                    ReIdentifyConstants.ColumnIdentifiers.RENAME_PART_NAME
            );

            System.out.println("SOP-6: New name from UI = " + newName);
            LOGGER.info("SOP-6: New name from UI = {}", newName);

            // SOP-7: Validate null safety
            if (oldName == null || newName == null) {
                System.out.println("SOP-7: Either oldName or newName is NULL → skipping prefix check.");
                LOGGER.warn("SOP-7: oldName or newName is NULL, validation skipped.");
                return super.preProcess(clientData, objectList);
            }

            // SOP-8: Trim and normalize values
            String oldTrim = oldName.trim();
            String newTrim = newName.trim();

            System.out.println("SOP-8: oldTrim = [" + oldTrim + "]");
            System.out.println("SOP-8: newTrim = [" + newTrim + "]");
            LOGGER.info("SOP-8: Trimmed values → old=[{}], new=[{}]", oldTrim, newTrim);

            // SOP-9: Apply business rule - new name should NOT start with old name
            if (!oldTrim.isEmpty()
                    && newTrim.toLowerCase().startsWith(oldTrim.toLowerCase())) {

                String message =
                        "Please donot use previous name " + oldTrim + " as new name prefix";

                System.out.println("SOP-9(FAIL): Validation failed → " + message);
                LOGGER.error("SOP-9(FAIL): Rename blocked due to prefix rule. Message: {}", message);

                // SOP-10: Prepare localized error message
                Locale locale = SessionHelper.getLocale();

                FeedbackMessage fb = new FeedbackMessage(
                        FeedbackType.FAILURE,
                        locale,
                        message,
                        null,
                        null
                );

                // SOP-11: Stop rename operation with FAILURE status
                FormResult fr = new FormResult(FormProcessingStatus.FAILURE);
                fr.addFeedbackMessage(fb);
                fr.setNextAction(FormResultAction.NONE);

                System.out.println("SOP-11: Returning FAILURE → rename operation blocked.");
                LOGGER.error("SOP-11: Rename stopped by preProcess.");

                return fr;
            } else {
                // SOP-12: Validation passed
                System.out.println("SOP-12: Validation PASSED → rename allowed.");
                LOGGER.info("SOP-12: Prefix validation passed.");
            }

        } else {
            // SOP-13: Not a WTDocument → rule not applicable
            System.out.println("SOP-13: Object not WTDocument → skipping validation.");
            LOGGER.info("SOP-13: Non-WTDocument object, skipping custom rename rule.");
        }

        // SOP-14: Continue standard Windchill rename flow
        System.out.println("SOP-14: Calling super.preProcess() → proceeding with rename.");
        LOGGER.info("SOP-14: Delegating to standard RenameObjectFormProcessor.");

        FormResult result = super.preProcess(clientData, objectList);

        System.out.println("==== SOP-15: Rename PreProcess END ====");
        LOGGER.info("SOP-15: Rename PreProcess completed with status: {}", result.getStatus());

        return result;
    }
}
