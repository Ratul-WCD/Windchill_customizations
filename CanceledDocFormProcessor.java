package custom;

import java.util.List;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.LifeCycleManaged;
import wt.lifecycle.State;
import wt.pom.Transaction;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.vc.wip.WorkInProgressException;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.netmarkets.util.beans.NmCommandBean;

/**
 * CanceledocFormProcessor - safer version
 * - resolves the selected context object
 * - verifies it's a WTDocument
 * - attempts to set lifecycle to Canceled inside a transaction
 * - does NOT call PersistenceHelper.save(doc) (avoids WIP save errors)
 * - catches WorkInProgressException and returns FAILURE with logs
 */
public class CanceledDocFormProcessor extends DefaultObjectFormProcessor {

    @Override
    public FormResult doOperation(NmCommandBean commandBean, List<ObjectBean> objectBeans) throws WTException {

        FormResult result = new FormResult();

        // turn off access enforcement temporarily if needed
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();

        try {
            // 1) get target from context (ActionOid / PrimaryOid / objectBeans)
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

            // 2) validate type
            if (!(target instanceof WTDocument)) {
                System.out.println("[CanceledocFormProcessor] Context object is not a WTDocument -> FAIL");
                result.setStatus(FormProcessingStatus.FAILURE);
                return result;
            }

            WTDocument doc = (WTDocument) target;

            // 3) perform lifecycle change inside transaction
            trx.start();
            System.out.println("[CanceledocFormProcessor] Transaction started. Attempting lifecycle transition to Canceled for doc: " + doc.getNumber());

            // This call will perform the lifecycle transition and (internally) persist as needed.
            LifeCycleHelper.service.setLifeCycleState((LifeCycleManaged) doc, State.toState("CANCELLED"));

            trx.commit();
            System.out.println("[CanceledocFormProcessor] Lifecycle transition committed for doc: " + doc.getNumber());

            result.setStatus(FormProcessingStatus.SUCCESS);
            return result;

        } catch (WorkInProgressException wip) {
            // Common case: object not checked out / cannot be modified due to WIP rules
            try { trx.rollback(); } catch (Exception x) {}
            System.out.println("[CanceledocFormProcessor] WIP error: object not modifiable. Message: " + wip.getMessage());
            // You can extend to surface a clearer message to UI via FormResult feedback if wanted
            result.setStatus(FormProcessingStatus.FAILURE);
            return result;

        } catch (Exception e) {
            // any other unexpected errors
            try { trx.rollback(); } catch (Exception x) {}
            System.out.println("[CanceledocFormProcessor] Unexpected error while releasing document: " + e);
            result.setStatus(FormProcessingStatus.FAILURE);
            return result;

        } finally {
            // always restore ACL enforcement
            SessionServerHelper.manager.setAccessEnforced(enforce);
            System.out.println("[CanceledocFormProcessor] finished, ACL enforcement restored");
        }
    }
}
