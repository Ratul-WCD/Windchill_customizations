package custom.utils;

import java.io.Serializable;

import org.apache.logging.log4j.Logger;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.folder.Folder;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.pom.Transaction;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.type.TypeDefinitionReference;
import wt.type.TypedUtilityServiceHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.VersionControlHelper;
import wt.vc.wip.CheckoutLink;
import wt.vc.wip.WorkInProgressHelper;
/**
 * Purpose:
 *   - This utility changes the soft type of an existing WTDocument.
 *   - It runs from Windchill shell and uses RemoteMethodServer to switch into
 *     MethodServer context (to avoid WIP / POM / method context exceptions).
 *
 *  SYNTAX:
 *      windchill custom.utils.ChangeSoftTypeUtility "<DOCUMENT_NUMBER>" -u <USERNAME> -p <PASSWORD>
 *
 *  EXAMPLE:
 *      windchill custom.utils.ChangeSoftTypeUtility "0000000058" -u orgadmin -p orgadmin
 *
 *  Notes:
 *   • DOCUMENT_NUMBER → The WTDocument number (NOT OID).
 *   • -u and -p are optional, but recommended when executing outside server user.
 */
public class ChangeSoftTypeUtility implements RemoteAccess, Serializable {

    private static final Logger LOGGER = LogR.getLogger(ChangeSoftTypeUtility.class.getName());
    private static final long serialVersionUID = 1L;

    // FIXED TARGET SOFT TYPE
    private static final String TARGET_INTERNAL_TYPE = "wt.doc.WTDocument|SR_Document.ITC";
    public static void main(String[] args) throws Exception {

        System.out.println("=== SOP-0: Utility Started ===");

        if (args == null || args.length < 1) {
            System.out.println("USAGE: windchill custom.utils.ChangeSoftTypeUtility <DOC> [-u user] [-p pass]");
            return;
        }

        String docNumber = args[0];
        String user = null, pass = null;

        // SOP Argument parsing
        System.out.println("SOP-1: Parsing arguments…");
        LOGGER.info("SOP-1: Parsing CLI args");

        for (int i = 1; i < args.length; i++) {
            if ("-u".equalsIgnoreCase(args[i])) {
                user = args[++i];
            } else if ("-p".equalsIgnoreCase(args[i])) {
                pass = args[++i];
            }
        }

        // SOP RMI check
        System.out.println("SOP-2: Checking MethodServer context…");

        if (!RemoteMethodServer.ServerFlag) {
            System.out.println("SOP-2A: Not in MethodServer → Switching to RMI mode");
            LOGGER.info("Running via RMI");

            RemoteMethodServer rms = RemoteMethodServer.getDefault();
            if (user != null) rms.setUserName(user);
            if (pass != null) rms.setPassword(pass);

            rms.invoke(
                    "execute",
                    ChangeSoftTypeUtility.class.getName(),
                    null,
                    new Class[]{String.class},
                    new Object[]{docNumber}
            );
            return;
        }

        System.out.println("SOP-2B: Running inside MethodServer");

        execute(docNumber);
    }

    // ------------------------------------------------------
    // EXECUTE (METHODSERVER ONLY)
    // ------------------------------------------------------
    public static void execute(String docNumber) throws Exception {

        System.out.println("=== SOP-3: EXECUTION START ===");
        LOGGER.info("EXECUTE START for DOC: {}", docNumber);

        Transaction trx = new Transaction();
        try {
            trx.start();
            System.out.println("SOP-3A: Transaction started");

            // STEP-1 Find Document
            System.out.println("SOP-4: Finding document by number: " + docNumber);
            LOGGER.info("Finding document {}", docNumber);

            WTDocument doc = find(docNumber);
            if (doc == null) {
                System.out.println("SOP-4(ERROR): Document Not Found!");
                LOGGER.error("Document {} not found", docNumber);
                return;
            }

            // STEP-2 Latest Iteration
            System.out.println("SOP-5: Getting Latest iteration…");
            doc = (WTDocument) VersionControlHelper.getLatestIteration(doc, false);
            System.out.println("SOP-5A: Found latest iteration = " + doc.getDisplayIdentity());
            LOGGER.info("Found latest iteration {}", doc.getDisplayIdentity());

            // STEP-3 Resolve Soft Type
            System.out.println("SOP-6: Resolving Target Soft Type = " + TARGET_INTERNAL_TYPE);
            LOGGER.info("Resolving soft type {}", TARGET_INTERNAL_TYPE);

            TypeDefinitionReference typeRef =
                    TypedUtilityServiceHelper.service.getTypeDefinitionReference(TARGET_INTERNAL_TYPE);

            if (typeRef == null) {
                System.out.println("SOP-6(ERROR): Could NOT resolve soft type!");
                LOGGER.error("Soft type {} not resolved", TARGET_INTERNAL_TYPE);
                return;
            }

            // STEP-4 Checkout
            System.out.println("SOP-7: Checking out document for modification…");
            LOGGER.info("Checking out {}", doc.getDisplayIdentity());

            WTDocument workingCopy = checkout(doc);

            System.out.println("SOP-7A: Checkout SUCCESS. WC = " + workingCopy.getDisplayIdentity());
            LOGGER.info("Checkout success");

            // STEP-5 Change Soft Type
            System.out.println("SOP-8: Changing soft type…");
            LOGGER.info("Changing soft type");

            changeType(workingCopy, typeRef);

            System.out.println("SOP-8A: Soft Type Updated!");

            // STEP-6 Checkin
            System.out.println("SOP-9: Checking in updated document…");
            LOGGER.info("Checking in");

            checkin(workingCopy);

            System.out.println("SOP-9A: Checkin SUCCESS!");
            LOGGER.info("Checkin success");

            trx.commit();
            trx = null;

            System.out.println("=== SOP-10: SOFT TYPE CHANGE SUCCESS ===");

        } catch (Exception e) {
            LOGGER.error("ERROR OCCURRED", e);
            System.out.println("SOP(ERROR): " + e.getMessage());
            throw e;
        } finally {
            if (trx != null) {
                System.out.println("SOP-11: Rolling back transaction");
                trx.rollback();
            }
            System.out.println("=== SOP-12: EXECUTION END ===");
        }
    }

    // ------------------------------------------------------
    // FIND DOCUMENT
    // ------------------------------------------------------
    private static WTDocument find(String number) throws WTException {
        System.out.println("SOP-FIND-1: Building QuerySpec…");

        QuerySpec qs = new QuerySpec(WTDocument.class);
        qs.appendWhere(new SearchCondition(
                WTDocument.class, WTDocument.NUMBER, SearchCondition.EQUAL, number),
                new int[]{0});

        QueryResult qr = PersistenceHelper.manager.find(qs);

        System.out.println("SOP-FIND-2: Query Executed. Checking result…");

        if (!qr.hasMoreElements()) {
            System.out.println("SOP-FIND-3: No Document Found");
            return null;
        }

        System.out.println("SOP-FIND-4: Document Found!");
        return (WTDocument) qr.nextElement();
    }

    // ------------------------------------------------------
    // CHECKOUT DOCUMENT
    // ------------------------------------------------------
    private static WTDocument checkout(WTDocument doc)
            throws WTException, WTPropertyVetoException {

        System.out.println("SOP-CO-1: Checking if already checked out…");

        if (WorkInProgressHelper.isCheckedOut(doc)) {
            System.out.println("SOP-CO-2: Already checked out. Returning working copy…");
            return (WTDocument) WorkInProgressHelper.service.workingCopyOf(doc);
        }

        System.out.println("SOP-CO-3: Getting default checkout folder…");

        Folder checkoutFolder = WorkInProgressHelper.service.getCheckoutFolder();

        System.out.println("SOP-CO-4: Performing checkout…");

        CheckoutLink cl = WorkInProgressHelper.service.checkout(
                doc,
                checkoutFolder,
                "Checkout for soft type update"
        );

        System.out.println("SOP-CO-5: Checkout SUCCESS!");

        return (WTDocument) cl.getWorkingCopy();
    }

    // ------------------------------------------------------
    // CHECKIN DOCUMENT
    // ------------------------------------------------------
    private static void checkin(WTDocument workingCopy)
            throws WTException, WTPropertyVetoException {

        System.out.println("SOP-CI-1: Starting checkin…");
        WorkInProgressHelper.service.checkin(workingCopy, "Soft type update by utility");
        System.out.println("SOP-CI-2: Checkin completed.");
    }

    // ------------------------------------------------------
    // CHANGE SOFT TYPE
    // ------------------------------------------------------
    private static void changeType(WTDocument doc, TypeDefinitionReference typeRef)
            throws WTException, WTPropertyVetoException {

        System.out.println("SOP-TYPE-1: Current Type = " + doc.getTypeDefinitionReference());
        System.out.println("SOP-TYPE-2: New Type     = " + typeRef);

        doc.setTypeDefinitionReference(typeRef);

        System.out.println("SOP-TYPE-3: Saving updated object…");

        PersistenceHelper.manager.modify(doc);

        System.out.println("SOP-TYPE-4: Modify SUCCESS!");
    }
}
