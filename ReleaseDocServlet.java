package custom;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import wt.fc.PersistenceHelper;
import wt.fc.ReferenceFactory;
import wt.fc.Persistable;
import wt.pom.Transaction;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.doc.WTDocument;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.LifeCycleManaged;
import wt.lifecycle.State;
import wt.vc.wip.WorkInProgressException;

public class ReleaseDocServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * POST handler - expects 'oid' param (Windchill OID like "OR:wt.doc.WTDocument:133036")
     * optional: 'comment'
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String oid = req.getParameter("oid");
        String comment = req.getParameter("comment");

        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        if (oid == null || oid.trim().isEmpty()) {
            out.println("<html><body><h3>Error: missing oid parameter</h3></body></html>");
            return;
        }

        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();

        try {
            // Resolve object from OID
            ReferenceFactory rf = new ReferenceFactory();
            Persistable target = (Persistable) rf.getReference(oid).getObject();

            if (!(target instanceof WTDocument)) {
                out.println("<html><body><h3>Error: OID does not reference a WTDocument</h3></body></html>");
                return;
            }

            WTDocument doc = (WTDocument) target;

            // Start transaction and attempt lifecycle change
            trx.start();
            out.println("<html><body>");
            out.println("<p>Attempting to set lifecycle to RELEASED for: " + doc.getNumber() + "</p>");

            // Main call - set lifecycle
            LifeCycleHelper.service.setLifeCycleState((LifeCycleManaged) doc, State.toState("RELEASED"));

            // commit
            trx.commit();

            out.println("<h3>Success: Document moved to RELEASED</h3>");
            out.println("<p>Document: " + doc.getNumber() + "</p>");
            out.println("</body></html>");
        } catch (WorkInProgressException wip) {
            try { trx.rollback(); } catch (Exception ignore) {}
            out.println("<html><body><h3>Cannot release: document is Work In Progress or requires checkout.</h3>");
            out.println("<pre>" + escape(wip.getMessage()) + "</pre></body></html>");
        } catch (WTException we) {
            try { trx.rollback(); } catch (Exception ignore) {}
            out.println("<html><body><h3>WTException occurred:</h3><pre>" + escape(we.getMessage()) + "</pre></body></html>");
        } catch (Exception e) {
            try { trx.rollback(); } catch (Exception ignore) {}
            out.println("<html><body><h3>Unexpected error:</h3><pre>" + escape(e.toString()) + "</pre></body></html>");
        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
            out.close();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
