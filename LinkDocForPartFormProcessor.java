package custom.formprocessor;

import java.util.List;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.folder.Folder;
import wt.folder.FolderEntry;
import wt.folder.FolderHelper;
import wt.inf.container.WTContainerRef;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.pom.Transaction;
import wt.session.SessionServerHelper;
import wt.util.WTException;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.netmarkets.util.beans.NmCommandBean;

public class LinkDocForPartFormProcessor extends DefaultObjectFormProcessor {

    @Override
    public FormResult doOperation(NmCommandBean commandBean, List<ObjectBean> objectBeans)
            throws WTException {

        System.out.println(">>> LinkDocForPartFormProcessor START");

        FormResult result = new FormResult();
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();

        try {
            // STEP 1 – context se WTPart nikalo
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
                System.out.println("STEP-1: Target WTPart nahi hai -> FAIL");
                result.setStatus(FormProcessingStatus.FAILURE);
                result.setMessageLocation("Selected object is not a WTPart");
                return result;
            }

            WTPart part = (WTPart) target;
            System.out.println("STEP-1: Got part = " + part.getNumber() + " , " + part.getName());

            // STEP 2 – JSP se values lo (agar diye ho)
            String docName   = commandBean.getTextParameter("docName");
            String docNumber = commandBean.getTextParameter("docNumber"); // optional

            System.out.println("STEP-2: UI docName = " + docName + ", docNumber = " + docNumber);

            trx.start();
            System.out.println("STEP-3: Transaction START");

            // STEP 3 – naya WTDocument banao
            WTDocument doc = WTDocument.newWTDocument();
            System.out.println("STEP-4: new WTDocument() created");

            // Name set karo
            if (docName == null || docName.trim().length() == 0) {
                docName = part.getName(); // fallback
            }
            doc.setName(docName);
            System.out.println("STEP-5: Document name set = " + docName);

            // Number: agar user ne diya hai to use karo, warna OIR / sequence handle karega
            if (docNumber != null && docNumber.trim().length() > 0) {
                doc.setNumber(docNumber.trim());
                System.out.println("STEP-6: Document number set from UI = " + docNumber);
            } else {
                System.out.println("STEP-6: No UI number, number will come from OIR / sequence.");
            }

            // Container same as part ka container
            WTContainerRef contRef = part.getContainerReference();
            doc.setContainerReference(contRef);
            System.out.println("STEP-7: Container copied from part");

            // STEP 4 – part ke folder ka location copy karo (optional but nice)
            try {
                Folder partFolder = FolderHelper.service.getFolder((FolderEntry) part);
                if (partFolder != null) {
                    FolderHelper.assignLocation(doc, partFolder);
                    System.out.println("STEP-8: Folder assigned = " + partFolder.getFolderPath());
                } else {
                    System.out.println("STEP-8: Part folder null, document will use default folder");
                }
            } catch (Exception fe) {
                System.out.println("STEP-8: Folder copy FAILED: " + fe);
            }

            // STEP 5 – document save karo
            doc = (WTDocument) PersistenceHelper.manager.save(doc);
            System.out.println("STEP-9: Document saved = " +
                    doc.getNumber() + " , " + doc.getName());

            // STEP 6 – Part-Document describe link banao
            WTPartDescribeLink link = WTPartDescribeLink.newWTPartDescribeLink(part, doc);
            PersistenceHelper.manager.save(link);
            System.out.println("STEP-10: WTPartDescribeLink created between part and doc");

            trx.commit();
            System.out.println("STEP-11: Transaction COMMIT");

            result.setStatus(FormProcessingStatus.SUCCESS);
            result.setMessageLocation("Document " + doc.getNumber()
                    + " created and linked to part successfully.");
            System.out.println("<<< LinkDocForPartFormProcessor END (SUCCESS)");
            return result;

        } catch (Exception e) {
            System.out.println("ERROR in LinkDocForPartFormProcessor: " + e);
            try {
                trx.rollback();
                System.out.println("STEP-ROLLBACK: Transaction rolled back");
            } catch (Exception x) {
                System.out.println("STEP-ROLLBACK: rollback also failed: " + x);
            }
            result.setStatus(FormProcessingStatus.FAILURE);
            result.setMessageLocation("Error while creating document for part: " + e.getMessage());
            System.out.println("<<< LinkDocForPartFormProcessor END (FAILURE)");
            return result;

        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
        }
    }
}
