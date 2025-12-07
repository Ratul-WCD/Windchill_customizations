package custom;

import java.util.ArrayList;
import java.util.List;

import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.part.WTPart;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.session.SessionServerHelper;
import wt.util.WTException;

import com.google.gwt.user.cellview.client.Column;
import com.ptc.core.components.beans.ObjectBean;

// NOTE: these are the HTML component/table classes typically present in Windchill 12/13
import com.ptc.core.htmlcomp.tableview.ConfigurableTable;

import com.ptc.core.htmlcomp.components.ConfigurableTableBuilder;

import com.ptc.netmarkets.util.beans.NmCommandBean;

/**
 * RelatedPartsTableBuilder - stable version for Windchill 12/13-ish.
 *
 * - buildConfigurableTable(String id) returns a ConfigurableTable describing columns and settings.
 * - getData(NmCommandBean, Object) returns List<ObjectBean> rows (WTPart objects filtered by state=RELEASED).
 *
 * If your SDK uses other column descriptor classes, swap imports:
 *  - com.ptc.core.components.descriptor.ColumnDescriptor  (then change Column instantiation accordingly)
 *
 * Make sure Windchill HTMLComp jars are on the build path.
 */
public class RelatedPartsTableBuilder implements ConfigurableTableBuilder {

    /**
     * Build the table structure (columns, width, selectable).
     * Windchill UI will call this to render the table header & layout.
     */
    @Override
    public ConfigurableTable buildConfigurableTable(String id) throws WTException {
        // id should be unique for this table instance
        ConfigurableTable table = new ConfigurableTable(id);

        // set a title (many containers will use this)
        ((Object) table).setTitle("Related Parts");

        // column: Part number (sortable)
        // Column(apiName, displayLabel)
        Column colNumber = new Column("number", "Number");
        colNumber.setSortable(true);
        colNumber.setWidth(120);
        table.addColumn(colNumber);

        // column: name
        Column colName = new Column("name", "Name");
        colName.setSortable(true);
        colName.setWidth(300);
        table.addColumn(colName);

        // column: lifecycle state
        Column colState = new Column("lifeCycleState", "State");
        colState.setSortable(true);
        colState.setWidth(120);
        table.addColumn(colState);

        // column: version (or versionInfo display)
        Column colVersion = new Column("versionInfo.identifierVersionId", "Version");
        colVersion.setWidth(100);
        table.addColumn(colVersion);

        // allow row selection (checkbox)
        table.setSelectable(true);

        // default page size (if supported)
        try {
            table.setPageSize(25);
        } catch (Throwable ignore) {
            // some SDK versions may not have setPageSize; ignore if absent
        }

        return table;
    }

    /**
     * Helper that will be called by the UI component to fetch rows.
     * Here we return List<ObjectBean> wrapping WTPart instances which are in RELEASED state.
     *
     * Note: this method name is not part of ConfigurableTableBuilder interface by default.
     * Your UI wiring must call this method via the component config (or implement the matching
     * callback signature your Windchill instance expects). Many examples expect a method named
     * 'getData' or 'buildComponentData'. If your system expects different method signature,
     * adapt accordingly.
     */
    @SuppressWarnings("deprecation")
	public List<ObjectBean> getData(NmCommandBean cmd, Object configObj) throws WTException {
        boolean enforce = SessionServerHelper.manager.setAccessEnforced(false);
        List<ObjectBean> beans = new ArrayList<>();

        try {
            // QuerySpec for WTPart where lifecycle state = 'RELEASED'
            QuerySpec qs = new QuerySpec(WTPart.class);
            SearchCondition sc = new SearchCondition(WTPart.class, WTPart.LIFE_CYCLE_STATE,
                    SearchCondition.EQUAL, "RELEASED");
            qs.appendWhere(sc, new int[] { 0 });

            QueryResult qr = PersistenceHelper.manager.find(qs);

            while (qr != null && qr.hasMoreElements()) {
                Object row = qr.nextElement();

                if (row instanceof WTPart) {
                    WTPart p = (WTPart) row;
                    // Wrap into ObjectBean for UI table row
                    try {
                        beans.add(new ObjectBean());
                    } catch (NoSuchMethodError nsme) {
                        // older/newer SDKs might not have ObjectBean(WTPart) ctor
                        ObjectBean ob = new ObjectBean();
                        ob.setObject(p);
                        beans.add(ob);
                    }
                } else if (row instanceof Object[]) {
                    // If query had joins, first element commonly the domain object
                    Object[] arr = (Object[]) row;
                    for (Object o : arr) {
                        if (o instanceof WTPart) {
                            try {
                                beans.add(new ObjectBean());
                            } catch (NoSuchMethodError nsme) {
                                ObjectBean ob = new ObjectBean();
                                ob.setObject(o);
                                beans.add(ob);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Object[] e) {
            throw new WTException("RelatedPartsTableBuilder.getData error: " + e.length, e);
        } finally {
            SessionServerHelper.manager.setAccessEnforced(enforce);
        }

        return beans;
    }

    /**
     * Some versions of the table builder interface expect a generic buildComponentData signature
     * that returns Object (component data). Provide a bridge method if required by the container.
     * Uncomment/rename if your environment expects it.
     */
    /*
    public Object buildComponentData(ComponentConfig config, ComponentParams params) throws Exception {
        NmCommandBean cmd = (params != null) ? (NmCommandBean) params.get("nmCommandBean") : null;
        return getData(cmd, null);
    }
    */
}
