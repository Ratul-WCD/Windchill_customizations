package custom;

import java.util.List;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.LifeCycleManaged;
import wt.lifecycle.State;
import wt.ownership.Ownership;
import wt.pds.StatementSpec;
import wt.pom.Transaction;
import wt.session.SessionServerHelper;
import wt.type.Typed;
import wt.util.WTException;
import wt.vc.wip.WorkInProgressException;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.forms.FeedbackMessage;
import com.ptc.core.components.forms.MessageType;
import com.ptc.netmarkets.util.beans.NmCommandBean;

public class ReleaseDocumentFormProcessorSRDocument extends DefaultObjectFormProcessor {

    @Override
    public FormResult doOperation(NmCommandBean commandBean, List<ObjectBean> objectBeans) throws WTException {
        FormResult result = new FormResult();
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();

        try {
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

            if (!(target instanceof WTDocument)) {
                result.setStatus(FormProcessingStatus.FAILURE);
                result.addFeedbackMessage(new FeedbackMessage(MessageType.ERROR, null, null,
                        new String[] { "Selected object is not a WTDocument." }));
                return result;
            }

            WTDocument doc = (WTDocument) target;

            String softType = ((Typed) doc).getType().getInternalName();
            String currentState = doc.getState().getState().getDisplay();

            if ("RELEASED".equalsIgnoreCase(currentState)) {
                result.setStatus(FormProcessingStatus.FAILURE);
                result.addFeedbackMessage(new FeedbackMessage(MessageType.INFO, null, null,
                        new String[] { "Document is already Released." }));
                return result;
            }

            if (!"SR_Document.ITC".equalsIgnoreCase(softType)) {
                result.setStatus(FormProcessingStatus.FAILURE);
                result.addFeedbackMessage(new FeedbackMessage(MessageType.INFO, null, null,
                        new String[] { "Only SR Documents can be Released Internally." }));
                return result;
            }

            trx.start();
            LifeCycleHelper.service.setLifeCycleState((LifeCycleManaged) doc, State.toState("RELEASED"));
            trx.commit();

            result.setStatus(FormProcessingStatus.SUCCESS);
            result.addFeedbackMessage(new FeedbackMessage(MessageType.SUCCESS, null, null,
                    new String[] { "Document released successfully." }));
            return result;

        } catch (WorkInProgressException wip) {
            try { trx.rollback(); } catch (Exception ignore) {}
            result.setStatus(FormProcessingStatus.FAILURE);
            result.addFeedbackMessage(new FeedbackMessage(MessageType.ERROR, null, null,
                    new String[] { "Object cannot be modified (Work In Progress): " + wip.getMessage() }));
            return result;

        } catch (Exception e) {
            try { trx.rollback(); } catch (Exception ignore) {}
            result.setStatus(FormProcessingStatus.FAILURE);
            result.addFeedbackMessage(new FeedbackMessage(MessageType.ERROR, null, null,
                    new String[] { "Unexpected error while releasing document: " + e.getMessage() }));
            return result;

        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
        }
    }
}
