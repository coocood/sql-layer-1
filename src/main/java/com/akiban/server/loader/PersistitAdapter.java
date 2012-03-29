/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.loader;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.akiban.ais.model.Column;
import com.akiban.ais.model.HKey;
import com.akiban.ais.model.HKeyColumn;
import com.akiban.ais.model.HKeySegment;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.Table;
import com.akiban.ais.model.UserTable;
import com.akiban.server.rowdata.FieldDef;
import com.akiban.server.rowdata.RowData;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.rowdata.RowDefCache;
import com.akiban.server.service.session.Session;
import com.akiban.server.service.session.SessionService;
import com.akiban.server.store.PersistitStore;
import com.persistit.Exchange;
import com.persistit.exception.PersistitException;

public class PersistitAdapter
{
    public PersistitAdapter(PersistitStore store, GenerateFinalTask task, Tracker tracker, SessionService sessionService)
        throws PersistitException
    {
        this.tracker = tracker;
        this.store = store;
        this.task = task;
        this.sessionService = sessionService;
        this.session = sessionService.createSession();
        UserTable leafTable = task.table();
        hKeyFieldDefs = new FieldDef[leafTable.hKey().nColumns()];
        hKey = new Object[leafTable.hKey().nColumns()];
        HKey leafHKey = leafTable.hKey();
        int nHKeySegments = leafHKey.segments().size();
        ordinals = new int[nHKeySegments];
        nKeyColumns = new int[nHKeySegments];
        RowDefCache rowDefCache = store.getRowDefCache();
        leafRowDef = rowDefCache.getRowDef(leafTable.getTableId());
        int segmentCount = 0;
        int hKeyColumnCount = 0;
        for (HKeySegment segment : leafHKey.segments()) {
            RowDef segmentRowDef = rowDefCache.getRowDef(segment.table().getTableId());
            ordinals[segmentCount] = segmentRowDef.getOrdinal();
            nKeyColumns[segmentCount] = segment.columns().size();
            for (HKeyColumn hKeyColumn : segment.columns()) {
                Column column = hKeyColumn.column();
                Table columnTable = column.getTable();
                RowDef columnRowDef = rowDefCache.getRowDef(columnTable.getTableId());
                hKeyFieldDefs[hKeyColumnCount] = columnRowDef.getFieldDef(column.getPosition());
                hKeyColumnCount++;
            }
            segmentCount++;
        }
        hKeyColumnPositions = task.hKeyColumnPositions();
        columnPositions = task.columnPositions();
        dbRow = new Object[leafTable.getColumns().size()];
        rowData = new RowData(new byte[ROW_DATA_BUFFER_SIZE]);
        exchange = store.getExchange(session, leafRowDef);
        logState();
    }

    public void handleRow(ResultSet resultSet) throws Exception
    {
        // Populate rowData
        for (int i = 0; i < dbRow.length; i++) {
            dbRow[i] = resultSet.getObject(columnPositions[i] + 1);
            if (resultSet.wasNull()) {
                dbRow[i] = null;
            }
        }
        rowData.createRow(leafRowDef, dbRow);
        rowCount++;
        // Populate hkey
        int i = 0;
        for (int hKeyColumnPosition : hKeyColumnPositions) {
            hKey[i] = resultSet.getObject(hKeyColumnPosition + 1);
            if (resultSet.wasNull()) {
                hKey[i] = null;
            }
            i++;
        }
        // Insert row
        store.writeRowForBulkLoad(session, exchange, leafRowDef, rowData, ordinals, nKeyColumns, hKeyFieldDefs, hKey);
    }

    public void close() throws Exception
    {
        store.flushIndexes(session);
        store.updateTableStats(session, leafRowDef, rowCount);
        store.releaseExchange(session, exchange);
        session.close();
        session = null;
    }

    // parentColumns are columns that may be present in
    // join.parent. Return the corresponding columns in join.child. If
    // a column is not present in join.parent, it is not represented
    // in the output.
    public static List<Column> columnsInChild(List<Column> parentColumns, Join join)
    {
        List<Column> childColumns = new ArrayList<Column>();
        for (Column parentColumn : parentColumns) {
            Column childColumn = join.getMatchingChild(parentColumn);
            if (childColumn != null) {
                childColumns.add(childColumn);
            }
        }
        return childColumns;
    }

    private void logState()
    {
        StringBuilder ordinalBuffers = new StringBuilder();
        StringBuilder nKeyColumnsBuffer = new StringBuilder();
        StringBuilder fieldDefsBuffer = new StringBuilder();
        ordinalBuffers.append('[');
        nKeyColumnsBuffer.append('[');
        fieldDefsBuffer.append('[');
        int n = ordinals.length;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                ordinalBuffers.append(", ");
                nKeyColumnsBuffer.append(", ");
            }
            ordinalBuffers.append(ordinals[i]);
            nKeyColumnsBuffer.append(nKeyColumns[i]);
        }
        n = hKeyFieldDefs.length;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                fieldDefsBuffer.append(", ");
            }
            FieldDef field = hKeyFieldDefs[i];
            fieldDefsBuffer.append(String.format("%s.%s", field.getRowDef().getTableName(), field.getName()));
        }
        ordinalBuffers.append(']');
        nKeyColumnsBuffer.append(']');
        fieldDefsBuffer.append(']');
        tracker.info(String.format("ordinals: %s", ordinalBuffers.toString()));
        tracker.info(String.format("nKeyColumns: %s", nKeyColumnsBuffer.toString()));
        tracker.info(String.format("fieldDefsBuffer: %s", fieldDefsBuffer.toString()));
    }

    private static final int ROW_DATA_BUFFER_SIZE = 1 << 16; // 64k

    private final PersistitStore store;
    private final GenerateFinalTask task;
    private final RowDef leafRowDef;
    private final FieldDef[] hKeyFieldDefs;
    // The hkey consists of ordinals and key column values. ordinal[i] is followed by nKeyColumns[i] key column values.
    private final int[] ordinals;
    private final int[] nKeyColumns;
    private final int[] hKeyColumnPositions;
    private final int[] columnPositions;
    private final Object[] dbRow;
    private final Object[] hKey;
    private final RowData rowData;
    private final Exchange exchange;
    private final Tracker tracker;
    private final SessionService sessionService;
    private long rowCount = 0;
    private Session session;
}
