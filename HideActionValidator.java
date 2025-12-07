package custom;

import java.lang.reflect.Method;
import java.util.List;

import wt.doc.WTDocument;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.ui.validation.DefaultUIComponentValidator;
import com.ptc.core.ui.validation.UIValidationCriteria;
import com.ptc.core.ui.validation.UIValidationKey;
import com.ptc.core.ui.validation.UIValidationResult;
import com.ptc.core.ui.validation.UIValidationResultSet;
import com.ptc.core.ui.validation.UIValidationStatus;

public class HideActionValidator extends DefaultUIComponentValidator {

    private static final String REQUIRED_SOFTTYPE = "SR_Document.ITC";

  
    public UIValidationStatus preValidateAction(UIValidationKey validationKey, UIValidationCriteria validationCriteria) {
        System.out.println(">>> SR_Document.ITCTypeValidator.preValidateAction START");

     //   UIValidationResultSet resultSet = UIValidationResultSet.newInstance();

        try {
            // Get selected objects
            List<ObjectBean> objectBeans = (List<ObjectBean>) validationCriteria.getObjectBeans();

            if (objectBeans == null || objectBeans.isEmpty()) {
         //       System.out resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
                return UIValidationStatus.DISABLED;
            }

            // Allow only one selection
            if (objectBeans.size() != 1) {
                System.out.println("Multiple selection -> DISABLED");
            //    resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
              return UIValidationStatus.DISABLED;
            }

            // Check if selected object is WTDocument
            ObjectBean first = objectBeans.get(0);
            Object raw = first.getObject();
            if (!(raw instanceof WTDocument)) {
                System.out.println("Selected object not a WTDocument -> DISABLED");
        //        resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
            return UIValidationStatus.DISABLED;
            }

            WTDocument doc = (WTDocument) raw;

            // Get soft type name using reflection
            String softTypeName = null;
            try {
                Class<?> helperClass = Class.forName("com.ptc.core.meta.type.SoftTypeIdentifierHelper");
                Object service = helperClass.getField("service").get(null);
                Method mGetId = service.getClass().getMethod("getSoftTypeIdentifier", Object.class);
                Object softTypeId = mGetId.invoke(service, doc);
                Method mGetName = softTypeId.getClass().getMethod("getName");
                softTypeName = (String) mGetName.invoke(softTypeId);
            } catch (Exception e) {
                softTypeName = null;
            }

            System.out.println("SoftType detected: " + softTypeName);

            // Enable only if soft type matches "SR_Document.ITC"
            if (softTypeName != null && REQUIRED_SOFTTYPE.equalsIgnoreCase(softTypeName)) {
                System.out.println("Document matches SR_Document.ITC -> ENABLED");
                return UIValidationStatus.ENABLED;
            } else {
                System.out.println("Invalid softtype or null -> DISABLED");
                return UIValidationStatus.DISABLED;
            }

//            return resultSet;

        } catch (Throwable t) {
            System.out.println("Validator error -> DISABLED : " + t);
     //       resultSet.addResult(UIValidationResult.newInstance(validationKey, UIValidationStatus.DISABLED));
            return UIValidationStatus.DISABLED;
        } finally {
            System.out.println("<<< SR_Document.ITCTypeValidator.preValidateAction END");
        }
    }


	private void addResult(UIValidationResult newInstance) {
		// TODO Auto-generated method stub
		
	}
}