package custom;

import java.util.List;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.util.FeedbackMessage;
import com.ptc.core.ui.resources.FeedbackType;
import com.ptc.netmarkets.util.beans.NmCommandBean;

import jakarta.servlet.http.HttpServletRequest;
import wt.session.SessionHelper;
import wt.util.WTException;

public class ValFormProcessor extends DefaultObjectFormProcessor {

    @Override

public FormResult preProcess(NmCommandBean commandBean, List<ObjectBean> objectBean) throws WTException {
        System.out.println("Pre Process");
        FormResult formResult = super.preProcess(commandBean, objectBean);
      HttpServletRequest httpRequest   = commandBean.getRequest();
        String partName = httpRequest.getParameter("objectNameValue");
        System.out.println("docname :: " + partName);
        if (partName.length() > 6) {
            FeedbackMessage feedbackMessage = new FeedbackMessage(FeedbackType.FAILURE, SessionHelper.getLocale(),
                    "Name Length Cant be greater than six", null, "");
            formResult.addFeedbackMessage(feedbackMessage);
            formResult.setStatus(FormProcessingStatus.FAILURE);
        }
        return formResult;
    }

}