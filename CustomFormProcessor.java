package custom;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormResult;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.State;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.util.WTException;

public class CustomFormProcessor extends DefaultObjectFormProcessor {

    @Override
    public FormResult doOperation(NmCommandBean commandBean, List<ObjectBean> objectBeans) throws WTException {
        System.out.println("Inside doOperation for ReleaseDocFormProcessor");

        FormResult formResult = super.doOperation(commandBean, objectBeans);

        HttpServletRequest request = commandBean.getRequest();
        String docName = request.getParameter("objectNameValue"); // Input from JSP
        System.out.println("Document Name from UI: " + docName);

        if (docName != null && !docName.isEmpty()) {
            // Find WTDocument by name
            WTDocument doc = findDocumentByName(docName);
            if (doc != null) {
                // Change state to RELEASED
                LifeCycleHelper.service.setLifeCycleState(doc, State.toState("RELEASED"));
                System.out.println("Document moved to RELEASED state.");

                // Save changes
                PersistenceHelper.manager.save(doc);
                System.out.println("Document saved successfully.");
            } else {
                System.out.println("Document not found for name: " + docName);
            }
        }

        return formResult;
    }


private WTDocument findDocumentByName(String name) throws WTException {
    QuerySpec qs = new QuerySpec(WTDocument.class);
    qs.appendWhere(new SearchCondition(WTDocument.class, WTDocument.NAME, SearchCondition.EQUAL, name), new int[]{0});

    QueryResult qr = PersistenceHelper.manager.find(qs);
    if (qr.hasMoreElements()) {
        return (WTDocument) qr.nextElement();
    }
    return null;
}

}