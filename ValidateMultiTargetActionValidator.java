package custom;

import java.lang.reflect.Method;
import java.util.List;

import wt.doc.WTDocument;
import wt.session.SessionHelper;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.ui.validation.DefaultUIComponentValidator;
import com.ptc.core.ui.validation.UIValidationCriteria;
import com.ptc.core.ui.validation.UIValidationKey;
import com.ptc.core.ui.validation.UIValidationResult;
import com.ptc.core.ui.validation.UIValidationResultSet;
import com.ptc.core.ui.validation.UIValidationStatus;
import com.ptc.netmarkets.util.beans.NmCommandBean;

/**
 * Validator: enable action ONLY when:
 *  - exactly one WTDocument selected
 *  - document soft-type = "SR_Document.ITC"
 *  - admin cannot bypass
 *
 * Uses reflection to get selected objects safely across WC versions.
 */
public class ValidateMultiTargetActionValidator extends DefaultUIComponentValidator {

    private static final String REQUIRED_SOFTTYPE = "SR_Document.ITC";

    @Override
    public UIValidationResultSet preValidateMultiTargetAction(UIValidationKey validationKey,
                                                              UIValidationCriteria validationCriteria) {

        UIValidationResultSet resultSet = UIValidationResultSet.newInstance();
        try {
            // 1) Try to obtain NmCommandBean from validationCriteria using common method names
            NmCommandBean nmBean = null;
            try {
                Method m = validationCriteria.getClass().getMethod("getNmCommandBean");
                Object o = m.invoke(validationCriteria);
                if (o instanceof NmCommandBean) nmBean = (NmCommandBean) o;
            } catch (Exception ignore) { /* try next */ }

            if (nmBean == null) {
                try {
                    Method m = validationCriteria.getClass().getMethod("getCommandBean");
                    Object o = m.invoke(validationCriteria);
                    if (o instanceof NmCommandBean) nmBean = (NmCommandBean) o;
                } catch (Exception ignore) { /* try other fallbacks */ }
            }

            // 2) Try to get selected ObjectBeans list from either NmCommandBean or validationCriteria direct methods.
            List<ObjectBean> objectBeans = null;

            if (nmBean != null) {
                // NmCommandBean may expose getSelectedObjects() or getSelected()
                try {
                    Method ms = nmBean.getClass().getMethod("getSelectedObjects");
                    Object sel = ms.invoke(nmBean);
                    if (sel instanceof List) objectBeans = (List<ObjectBean>) sel;
                } catch (Exception ignore) {}
                if (objectBeans == null) {
                    try {
                        Method ms = nmBean.getClass().getMethod("getSelected");
                        Object sel = ms.invoke(nmBean);
                        if (sel instanceof List) objectBeans = (List<ObjectBean>) sel;
                    } catch (Exception ignore) {}
                }
            }

            // 3) If still null, try validationCriteria.getSelectedObjects() or getObjectBeans()
            if (objectBeans == null) {
                try {
                    Method m = validationCriteria.getClass().getMethod("getSelectedObjects");
                    Object res = m.invoke(validationCriteria);
                    if (res instanceof List) objectBeans = (List<ObjectBean>) res;
                } catch (Exception ignore) {}
            }
            if (objectBeans == null) {
                try {
                    Method m = validationCriteria.getClass().getMethod("getObjectBeans");
                    Object res = m.invoke(validationCriteria);
                    if (res instanceof List) objectBeans = (List<ObjectBean>) res;
                } catch (Exception ignore) {}
            }

            // If still null or empty -> disabled
            if (objectBeans == null || objectBeans.isEmpty()) {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
                return resultSet;
            }

            // Only allow single selection
            if (objectBeans.size() != 1) {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
                return resultSet;
            }

            // Verify it's a WTDocument
            ObjectBean first = objectBeans.get(0);
            Object rawObj = first == null ? null : first.getObject();
            if (!(rawObj instanceof WTDocument)) {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
                return resultSet;
            }
            WTDocument doc = (WTDocument) rawObj;

            // 4) Resolve soft-type name via common helper (reflection) - tolerant
            String softTypeName = null;
            try {
                Class<?> helperClass = Class.forName("com.ptc.core.meta.type.SoftTypeIdentifierHelper");
                Object service = helperClass.getField("service").get(null);
                Method mGet = service.getClass().getMethod("getSoftTypeIdentifier", Object.class);
                Object softTypeId = mGet.invoke(service, doc);
                Method mName = softTypeId.getClass().getMethod("getName");
                softTypeName = (String) mName.invoke(softTypeId);
            } catch (ClassNotFoundException cnf) {
                // try alternative utility
                try {
                    Class<?> util = Class.forName("com.ptc.core.meta.common.TypeIdentifierUtility");
                    Method mg = util.getMethod("getSoftTypeIdentifier", Object.class);
                    Object softTypeId = mg.invoke(null, doc);
                    Method mName = softTypeId.getClass().getMethod("getName");
                    softTypeName = (String) mName.invoke(softTypeId);
                } catch (Exception e) {
                    softTypeName = null;
                }
            } catch (Exception e) {
                softTypeName = null;
            }
            String currentUser = SessionHelper.manager.getPrincipal().getName();
            System.out.println("Current user: " + currentUser + " (No bypass for admin)");
            // 5) No admin bypass: just check soft type equality
            if (softTypeName != null && REQUIRED_SOFTTYPE.equalsIgnoreCase(softTypeName)) {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.ENABLED));
            } else {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
            }

            return resultSet;

        } catch (Throwable t) {
            // defensive: on unexpected errors -> disable action
            try {
                resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
            } catch (Throwable ignore) {}
            return resultSet;
        }
    }
}
