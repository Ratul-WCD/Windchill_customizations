package custom;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import wt.fc.PersistenceHelper;
import wt.fc.Persistable;
import wt.pom.Transaction;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.vc.VersionControlHelper;
import wt.vc.wip.WorkInProgressException;
import wt.doc.WTDocument;
import wt.lifecycle.LifeCycleHelper;
import wt.lifecycle.LifeCycleManaged;
import wt.lifecycle.State;

/**
 * Excel -> Windchill importer (template).
 *
 * WARNING:
 *  - Test in dev. This template uses common Windchill patterns but you must adapt
 *    the document creation call (createDocumentFromRow) to match your APIs.
 *  - Run on MethodServer (not local IDE) or via a customization job so Windchill classes are available.
 *
 * Usage: call ExcelToWindchillImporter.importFromExcel("/mnt/data/Documents to be uploaded.xlsx");
 */
public class ExcelToWindchillImporter {

    // Path to your uploaded Excel (change if needed)
    private static final String DEFAULT_EXCEL_PATH = "\"C:\\ptc\\Windchill_13.0\\Windchill\\Documents to be uploaded.xlsx\"";

    public static void importFromExcel(String filePath) throws Exception {
        if (filePath == null) filePath = DEFAULT_EXCEL_PATH;
        File f = new File(filePath);
        if (!f.exists()) throw new IllegalArgumentException("Excel file not found: " + filePath);

        FileInputStream fis = null;
        Workbook wb = null;
        try {
            fis = new FileInputStream(f);
            wb = WorkbookFactory.create(fis);
            Sheet sheet = wb.getSheetAt(0); // first sheet

            // Read header row to determine columns
            Iterator<Row> rowIt = sheet.rowIterator();
            if (!rowIt.hasNext()) {
                System.out.println("Excel is empty");
                return;
            }
            Row header = rowIt.next();
            Map<Integer,String> colIndexToName = new HashMap<>();
            for (Cell c : header) {
                colIndexToName.put(c.getColumnIndex(), c.getStringCellValue().trim());
            }

            int success = 0;
            int failures = 0;
            while (rowIt.hasNext()) {
                Row row = rowIt.next();
                Map<String,String> rowData = new HashMap<>();
                for (int ci=0; ci<header.getLastCellNum(); ci++) {
                    Cell cell = row.getCell(ci);
                    String val = cellToString(cell);
                    String colName = colIndexToName.get(ci);
                    if (colName != null) rowData.put(colName, val);
                }

                try {
                    boolean ok = createDocumentFromRow(rowData);
                    if (ok) success++;
                    else failures++;
                } catch (Throwable t) {
                    failures++;
                    System.err.println("Row create failed: " + t);
                    t.printStackTrace();
                }
            }

            System.out.println("Import done. success=" + success + " failures=" + failures);

        } finally {
            if (wb != null) try { wb.close(); } catch (Exception ignore) {}
            if (fis != null) try { fis.close(); } catch (Exception ignore) {}
        }
    }

    private static String cellToString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); } catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            case BLANK:
            default: return "";
        }
    }

    /**
     * Create a Windchill document from a parsed row.
     *
     * NOTE: This method contains a robust transaction wrapper and sample logic:
     *  - YOU MUST adapt the actual creation implementation to your Windchill APIs.
     *  - I show two approaches (commented): direct WTDocument creation vs. calling a helper/service.
     *
     * Expected input columns in the excel (example): NUMBER, NAME, DESCRIPTION, PRODUCT, CONTAINER_OID, PRIMARY_CONTENT_PATH, SOFTTYPE, LIFECYCLE (optional)
     *
     * Return true if created successfully.
     */
    private static boolean createDocumentFromRow(Map<String,String> rowData) throws WTException {
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        Transaction trx = new Transaction();
        try {
            trx.start();

            // ---------- APPLY YOUR CREATION LOGIC HERE ----------
            // Example variables extracted from Excel:
            String number = safeTrim(rowData.get("NUMBER"));
            String name = safeTrim(rowData.get("NAME"));
            String description = safeTrim(rowData.get("DESCRIPTION"));
            String product = safeTrim(rowData.get("PRODUCT"));
            String containerOid = safeTrim(rowData.get("CONTAINER_OID"));
            String primaryContent = safeTrim(rowData.get("PRIMARY_CONTENT_PATH"));
            String softtype = safeTrim(rowData.get("SOFTTYPE")); // e.g. "SR_Document.ITC"
            String lifecycle = safeTrim(rowData.get("LIFECYCLE")); // optional

            // --------- EXAMPLE A: naive WTDocument creation (MUST ADAPT) ----------
            // The actual API to construct and persist a WTDocument varies by site.
            // Below is a GENERIC/EXAMPLE approach — replace with your Document creation helper.
            WTDocument doc = null;

            // Option 1 (example): use a factory/new instance + set fields + persist
            // (NOTE: this code might not compile on your Windchill version; adapt accordingly)
            try {
                // Many implementations use WTDocument.newWTDocument() or DocumentHelper.service.newDocument(...)
                // Here is a reflective, best-effort approach — you should replace it with your site API.
                Class<?> docClass = WTDocument.class;
                doc = (WTDocument) docClass.getDeclaredConstructor().newInstance();

                // set commonly present fields using reflection in a defensive way:
                try { doc.getClass().getMethod("setName", String.class).invoke(doc, name); } catch (Throwable ignore) {}
                try { doc.getClass().getMethod("setNumber", String.class).invoke(doc, number); } catch (Throwable ignore) {}
                try { doc.getClass().getMethod("setDescription", String.class).invoke(doc, description); } catch (Throwable ignore) {}

                // If you have a soft-type API, set it here (pseudocode)
                // e.g. SoftTypeIdentifierHelper.service.setSoftTypeIdentifier(doc, softtype)
            } catch (Throwable t) {
                // If the naive creation above fails or is not supported, you must replace with your system's helper:
                // e.g. WTDocument doc = DocumentHelper.service.newDocument(...);
                // we'll throw to show the user must adapt
                throw new WTException("Please replace the creation block with your Windchill-specific document creation API. Error: " + t.getMessage());
            }

            // Persist — recommended: use PersistenceHelper.manager.save/modify as required by your site.
            try {
                PersistenceHelper.manager.save((Persistable) doc);
            } catch (Throwable t) {
                // some lifecycle helpers persist internally — if save fails, we leave it to your API.
                System.out.println("Warning: PersistenceHelper.save failed: " + t.getMessage());
            }

            // Optionally set lifecycle if provided (and your doc object supports it)
            if (lifecycle != null && !lifecycle.isEmpty()) {
                try {
                    LifeCycleHelper.service.setLifeCycleState((LifeCycleManaged) doc, State.toState(lifecycle));
                } catch (Throwable ignore) {
                    // ignore - will require proper adaptation in many sites
                }
            }

            // commit transaction
            trx.commit();
            System.out.println("Created document (best-effort): " + name + " / " + number);
            return true;

        } catch (WorkInProgressException wip) {
            try { trx.rollback(); } catch (Exception ignore) {}
            System.err.println("WIP problem creating doc: " + wip.getMessage());
            return false;

        } catch (Throwable t) {
            try { trx.rollback(); } catch (Exception ignore) {}
            System.err.println("Error creating doc row: " + t.getMessage());
            t.printStackTrace();
            return false;

        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
        }
    }

    private static String safeTrim(String s) {
        if (s == null) return "";
        return s.trim();
    }

    // quick main to run from Java launcher on MethodServer (if you can run as Java class)
    public static void main(String[] args) {
        try {
            String path = (args != null && args.length > 0) ? args[0] : DEFAULT_EXCEL_PATH;
            importFromExcel(path);
        } catch (Throwable t) {
            System.err.println("Import failed: " + t);
            t.printStackTrace();
        }
    }
}
