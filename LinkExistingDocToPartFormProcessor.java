package custom.formprocessor;

import java.util.List;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.pom.Transaction;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.vc.wip.WorkInProgressHelper;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.netmarkets.util.beans.NmCommandBean;

public class LinkExistingDocToPartFormProcessor extends DefaultObjectFormProcessor {

    @Override
    public FormResult doOperation(NmCommandBean commandBean, List<ObjectBean> objectBeans)
            throws WTException {

        System.out.println("---- LinkExistingDocToPartFormProcessor START ----");

        FormResult result = new FormResult();
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();

        try {
            // 1) Context se PART nikaalo
            Persistable target = null;

            if (commandBean != null && commandBean.getActionOid() != null) {
                target = (Persistable) commandBean.getActionOid().getRefObject();
            } else if (commandBean != null && commandBean.getPrimaryOid() != null) {
                target = (Persistable) commandBean.getPrimaryOid().getRefObject();
            } else if (objectBeans != null && !objectBeans.isEmpty()) {
                Object o = objectBeans.get(0).getObject();
                if (o instanceof Persistable) {
                    target = (Persistable) o;
                }
            }

            if (!(target instanceof WTPart)) {
                System.out.println("Context object is NOT WTPart");
                result.setStatus(FormProcessingStatus.FAILURE);
                result.setMessageLocation("Selected object is not a WTPart");
                return result;
            }

            WTPart part = (WTPart) target;
            System.out.println("PART FOUND : " + part.getNumber());

            // 2) User se docNumber / docName lo (JSP form se)
            String docNumber = commandBean.getTextParameter("docNumber");
            String docName   = commandBean.getTextParameter("docName"); // optional, mainly for display

            if (docNumber == null || docNumber.trim().length() == 0) {
                System.out.println("Doc number empty aaya");
                result.setStatus(FormProcessingStatus.FAILURE);
                result.setMessageLocation("Please enter Document Number.");
                return result;
            }
            docNumber = docNumber.trim();
            System.out.println("INPUT DOC NUMBER : " + docNumber);
            if (docName != null) {
                System.out.println("INPUT DOC NAME    : " + docName);
            }

            // 3) WTDocument ko search karo (NUMBER se)
            WTDocument doc = null;

            QuerySpec qs = new QuerySpec(WTDocument.class);
            SearchCondition sc = new SearchCondition(
                    WTDocument.class,
                    WTDocument.NUMBER,
                    SearchCondition.EQUAL,
                    docNumber);
            qs.appendWhere(sc, new int[] { 0 });

            System.out.println("Searching document in DB...");
            QueryResult qr = PersistenceHelper.manager.find(qs);

            if (!qr.hasMoreElements()) {
                System.out.println("DOCUMENT NOT FOUND for number : " + docNumber);
                result.setStatus(FormProcessingStatus.FAILURE);
                result.setMessageLocation("Doc number not created on system, please create first");
                return result;
            } else {
                Object obj = qr.nextElement();
                if (obj instanceof WTDocument) {
                    doc = (WTDocument) obj;
                } else if (obj instanceof Object[]) {
                    doc = (WTDocument) ((Object[]) obj)[0];
                }
            }

            System.out.println("DOCUMENT FOUND : " + doc.getNumber());

            // 4) Check karo part working copy hai ya nahi
            if (!WorkInProgressHelper.isWorkingCopy(part)) {
                System.out.println("PART NOT CHECKED OUT - link create nahi kar sakte");
                result.setStatus(FormProcessingStatus.FAILURE);
                result.setMessageLocation(
                        "Part is not checked out and cannot be modified. Please check out the part first.");
                return result;
            }

            // 5) Ab describe link create karo
            trx.start();
            System.out.println("Transaction START - creating WTPartDescribeLink...");

            WTPartDescribeLink link = WTPartDescribeLink.newWTPartDescribeLink(part, doc);
            PersistenceHelper.manager.save(link);

            trx.commit();
            System.out.println("Transaction COMMIT - link created successfully");

            result.setStatus(FormProcessingStatus.SUCCESS);
            result.setMessageLocation("Existing document linked to part successfully");
            return result;

        } catch (Exception e) {
            System.out.println("ERROR in LinkExistingDocToPartFormProcessor : " + e.getMessage());
            e.printStackTrace();

            try {
                trx.rollback();
                System.out.println("Transaction ROLLBACK done");
            } catch (Exception x) {
                System.out.println("Rollback failed: " + x.getMessage());
            }

            result.setStatus(FormProcessingStatus.FAILURE);
            result.setMessageLocation("Error while linking existing document to part: " + e.getMessage());
            return result;

        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
            System.out.println("---- LinkExistingDocToPartFormProcessor END ----");
        }
    }
}
