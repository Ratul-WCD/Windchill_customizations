package custom.tablebuilders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.ptc.core.htmlcomp.components.AbstractConfigurableTableBuilder;
import com.ptc.core.htmlcomp.tableview.ConfigurableTable;
import com.ptc.core.lwc.server.PersistableAdapter;
import com.ptc.mvc.components.ColumnConfig;
import com.ptc.mvc.components.ComponentConfig;
import com.ptc.mvc.components.ComponentConfigFactory;
import com.ptc.mvc.components.ComponentParams;
import com.ptc.mvc.components.TableConfig;

import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.Persistable;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.fc.WTObject;
import wt.fc.WTReference;
import wt.iba.value.IBAHolder;
import wt.log4j.LogR;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.part.WTPartMaster;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.util.WTException;
import wt.vc.Iterated;
import wt.vc.VersionControlHelper;

/**
 * TableBuilder:
 *  - Context PBO: WTDocument
 *  - Shows linked WTParts
 *  - Filter: Linkage.part (internal) == "yes"
 */
public class AttributeLinkedPartsTableBuilder extends AbstractConfigurableTableBuilder {

    private static final Logger LOGGER =
            LogR.getLogger(AttributeLinkedPartsTableBuilder.class.getName());

    // attribute and enum internal name
    private static final String ATTRIBUTE_NAME = "Linkage.part";
    private static final String EXPECTED_VALUE = "yes";

    private static final String TABLE_ID = "linkedPartsTable";

	private static final String IBA_ITEM_TYPE_PART = "Linkage.part";

    // --------------------------------------------------------------------
    // 0) REQUIRED by ConfigurableTableBuilder (stub only)
    // --------------------------------------------------------------------
    @Override
    public ConfigurableTable buildConfigurableTable(String tableId) throws WTException {
        System.out.println("AttributeLinkedPartsTableBuilder.buildConfigurableTable(String) called, tableId = " + tableId);
        // Hum MVC wale buildComponentConfig/buildComponentData use kar rahe hain,
        // isliye yahan koi special ConfigurableTable nahi bana rahe.
        return null;
    }

    // --------------------------------------------------------------------
    // 1) TABLE DATA (rows)
    // --------------------------------------------------------------------
    @Override
    public Object buildComponentData(ComponentConfig config, ComponentParams params)
            throws WTException {

        System.out.println("========== TABLE BUILDER START ==========");

        WTDocument doc = resolveDocumentFromParams(params);

        if (doc == null) {
            System.out.println("❌ Document resolve FAILED → empty table");
            return new ArrayList<WTPart>();
        }

        System.out.println("✅ WTDocument found = " + doc.getNumber());

        Collection<WTPart> linkedParts = getLinkedParts(doc);
        System.out.println("✅ Total linked parts = " + linkedParts.size());

        Collection<WTPart> filtered = new ArrayList<WTPart>();

        for (WTPart part : linkedParts) {

            System.out.println("-----------------------------------");
            System.out.println("Checking Part = " + part.getNumber());

            String value = getStringIBAValue(part, ATTRIBUTE_NAME);

            System.out.println("IBA value = [" + value + "]");

            if (value != null && EXPECTED_VALUE.equalsIgnoreCase(value.trim())) {
                System.out.println("✅ MATCH FOUND → ADDED TO TABLE");
                filtered.add(part);
            } else {
                System.out.println("❌ NOT MATCHED → SKIPPED");
            }
        }

        System.out.println("✅ FINAL COUNT AFTER FILTER = " + filtered.size());
        System.out.println("========== TABLE BUILDER END ==========");

        return filtered;
    }

    // --------------------------------------------------------------------
    // 2) TABLE CONFIG (columns)
    // --------------------------------------------------------------------
    @Override
    public ComponentConfig buildComponentConfig(ComponentParams params) throws WTException {

        System.out.println("===== AttributeLinkedPartsTableBuilder.buildComponentConfig START =====");

        ComponentConfigFactory factory = getComponentConfigFactory();

        // Table config
        TableConfig table = factory.newTableConfig();
        table.setId(TABLE_ID);
        table.setLabel("Linkage = Yes");

        table.setType(WTPart.class.getName());
        table.setShowCount(true);
        table.setSelectable(true);
        // table.setShowCheckBoxes(false);   // agar method available ho

        // ---------- Extra columns ----------

        // 1) Part icon column (type icon)
        ColumnConfig iconCol = factory.newColumnConfig("type", true);
        iconCol.setLabel("");              // usually blank label
        iconCol.setWidth(30);              // chhota sa column
        iconCol.setSortable(false);

        // 2) Info button column (opens info page)
        ColumnConfig infoCol = factory.newColumnConfig("infoPageAction", true);
        infoCol.setLabel("");              // standard Windchill style
        infoCol.setWidth(30);
        infoCol.setSortable(false);

        // ---------- Existing columns ----------

        // Number
        ColumnConfig numberCol = factory.newColumnConfig("number", true);
        numberCol.setLabel("Number");

        // Name
        ColumnConfig nameCol = factory.newColumnConfig("name", true);
        nameCol.setLabel("Name");

        // State
        ColumnConfig stateCol = factory.newColumnConfig("state", true);
        stateCol.setLabel("State");

        // 3) Revision (version)
        ColumnConfig revCol = factory.newColumnConfig("version", true);
        revCol.setLabel("Revision");

        // 4) Iteration
        ColumnConfig iterCol = factory.newColumnConfig("iteration", true);
        iterCol.setLabel("Iteration");

        // 5) Checked-out column
        ColumnConfig coCol = factory.newColumnConfig("checkoutState", true);
        coCol.setLabel("Checked Out");

        // 6) Last Modified
        ColumnConfig lastModCol = factory.newColumnConfig("modifyTimestamp", true);
        lastModCol.setLabel("Last Modified");

        // 7) IBA column (Linkage.part)
        ColumnConfig ibaCol = factory.newColumnConfig(IBA_ITEM_TYPE_PART, true);
        ibaCol.setLabel("Linkage");
   

        // ---------- Column order in table ----------
        table.addComponent(iconCol);      // part icon    
        table.addComponent(numberCol);
        table.addComponent(nameCol);        
        table.addComponent(infoCol);      // info button
        table.addComponent(ibaCol);       // IBA value (Yes/No)
        table.addComponent(revCol);
  //      table.addComponent(iterCol);
        table.addComponent(stateCol);
        table.addComponent(coCol);
        table.addComponent(lastModCol);
      

        System.out.println("===== AttributeLinkedPartsTableBuilder.buildComponentConfig END =====");
        return table;
    }

    // --------------------------------------------------------------------
    // 3) HELPERS
    // --------------------------------------------------------------------

    // WTDocument resolve from params/context
    private WTDocument resolveDocumentFromParams(ComponentParams params) {
        try {
            System.out.println("   [resolveDocumentFromParams] START");

            Object ctx = params.getContextObject();
            System.out.println("   [resolveDocumentFromParams] contextObject = "
                    + (ctx == null ? "null" : ctx.getClass().getName()));

            if (ctx instanceof WTDocument) {
                System.out.println("   [resolveDocumentFromParams] Using contextObject as WTDocument.");
                return (WTDocument) ctx;
            }

            Object objParam = params.getParameter("object");
            System.out.println("   [resolveDocumentFromParams] param 'object' = "
                    + (objParam == null ? "null" : objParam.getClass().getName()));

            if (objParam instanceof WTDocument) {
                System.out.println("   [resolveDocumentFromParams] Using 'object' param as WTDocument.");
                return (WTDocument) objParam;
            }

            Object oidParam = params.getParameter("oid");
            System.out.println("   [resolveDocumentFromParams] param 'oid' = "
                    + (oidParam == null ? "null" : oidParam.toString()));

            if (oidParam instanceof String) {
                try {
                    ReferenceFactory rf = new ReferenceFactory();
                    WTReference ref = rf.getReference((String) oidParam);
                    WTObject wto = (WTObject) ref.getObject();
                    System.out.println("   [resolveDocumentFromParams] Resolved from oid → "
                            + (wto == null ? "null" : wto.getClass().getName()));
                    if (wto instanceof WTDocument) {
                        System.out.println("   [resolveDocumentFromParams] Using WTDocument from oid.");
                        return (WTDocument) wto;
                    }
                } catch (Exception e) {
                    System.out.println("   [resolveDocumentFromParams] EXCEPTION while resolving oid: "
                            + e.getMessage());
                    LOGGER.error("Error resolving oid {} to WTDocument", oidParam, e);
                }
            }

            System.out.println("   [resolveDocumentFromParams] Could not resolve WTDocument from params.");
        } catch (Exception e) {
            System.out.println("   [resolveDocumentFromParams] EXCEPTION (outer): " + e.getMessage());
            LOGGER.error("Error resolving WTDocument from params (outer)", e);
        }
        return null;
    }

    // Linked parts from Describes link
    private Collection<WTPart> getLinkedParts(WTDocument doc) throws WTException {
        System.out.println("   [getLinkedParts] START for doc: " + doc.getNumber());
        Collection<WTPart> parts = new ArrayList<WTPart>();

        try {
            long docId = PersistenceHelper.getObjectIdentifier(doc).getId();
            System.out.println("   [getLinkedParts] Doc OID id = " + docId);

            QuerySpec qs = new QuerySpec(WTPartDescribeLink.class);

            SearchCondition sc = new SearchCondition(
                    WTPartDescribeLink.class,
                    "roleBObjectRef.key.id",
                    SearchCondition.EQUAL,
                    docId
            );
            qs.appendWhere(sc);

            QueryResult qr = PersistenceHelper.manager.find(qs);
            System.out.println("   [getLinkedParts] QueryResult hasMoreElements = " + qr.hasMoreElements());

            while (qr.hasMoreElements()) {
                Object obj = qr.nextElement();
                if (obj instanceof WTPartDescribeLink) {
                    WTPartDescribeLink link = (WTPartDescribeLink) obj;

                    Persistable aObj = link.getRoleAObject(); // Part side
                    System.out.println("   [getLinkedParts] Link found, roleA = " + aObj);

                    if (aObj instanceof WTPart) {
                        parts.add((WTPart) aObj);
                    } else if (aObj instanceof WTPartMaster) {
                        WTPartMaster master = (WTPartMaster) aObj;
                        WTPart latest = (WTPart) VersionControlHelper
                                .getLatestIteration((Iterated) master);
                        if (latest != null) {
                            parts.add(latest);
                        }
                    }
                } else {
                    System.out.println("   [getLinkedParts] Unexpected object from QR: " + obj);
                }
            }

        } catch (Exception e) {
            System.out.println("   [getLinkedParts] EXCEPTION: " + e.getMessage());
            LOGGER.error("Error getting linked parts for doc {}", doc.getNumber(), e);
            throw new WTException(e);
        }

        System.out.println("   [getLinkedParts] END – total parts = " + parts.size());
        return parts;
    }

    // Read soft attribute value
    private String getStringIBAValue(IBAHolder holder, String attrName) {
        System.out.println("      [getStringIBAValue] START for attr = " + attrName);
        if (!(holder instanceof Persistable)) {
            System.out.println("      [getStringIBAValue] Holder is not Persistable → null.");
            return null;
        }

        try {
            Persistable persistable = (Persistable) holder;

            System.out.println("      [getStringIBAValue] Creating PersistableAdapter...");
            PersistableAdapter adapter =
                    new PersistableAdapter(persistable, null, Locale.getDefault(), null);

            System.out.println("      [getStringIBAValue] Loading attribute: " + attrName);
            adapter.load(attrName);

            Object value = adapter.get(attrName);
            System.out.println("      [getStringIBAValue] Raw value from adapter = " + value);
            LOGGER.debug("[getStringIBAValue] attr {} value = {}", attrName, value);

            if (value != null) {
                String result = value.toString();
                System.out.println("      [getStringIBAValue] Returning string value = " + result);
                return result;
            }

        } catch (Exception e) {
            System.out.println("      [getStringIBAValue] EXCEPTION: " + e.getMessage());
            LOGGER.error("Error reading attribute {} via PersistableAdapter", attrName, e);
        }

        System.out.println("      [getStringIBAValue] END → returning null");
        return null;
    }
}
