package esypsydb.index.planner;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import esypsydb.index.Index;
import esypsydb.metadata.IndexInfo;
import esypsydb.plan.TablePlan;
import esypsydb.query.Constant;
import esypsydb.query.UpdateScan;
import esypsydb.record.RID;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

// 同一フィールドに Hash と BTree の index がある表で、更新と backfill の後も
// どの index の検索結果も表の走査結果と一致するか
public class MultiIndexTest extends TestCase {
    private File root;
    private EasyPeasyDB db;

    protected void setUp() throws Exception {
        root = Files.createTempDirectory("multiindextest").toFile();
        db = new EasyPeasyDB(new File(root, "db").getPath());
        Transaction tx = db.newTx();
        update(tx, "create table t (a int, b int)");
        update(tx, "create index ia_hash on t(a)");
        update(tx, "create index ia_btree on t(a) using btree");
        for (int i = 0; i < 60; i++)
            update(tx, "insert into t (a, b) values (" + (i % 10) + ", " + i + ")");
        tx.commit();
    }

    protected void tearDown() throws Exception {
        deleteAll(root);
    }

    public void testInsertedRowsAreInEveryIndex() {
        Transaction tx = db.newTx();
        assertEveryIndexMatchesTable(tx, "a", 0, 10);
        tx.commit();
    }

    public void testDeleteRemovesRowsFromEveryIndex() {
        Transaction tx = db.newTx();
        update(tx, "delete from t where a = 3");
        assertEquals(0, ridsFromTable(tx, "a", 3).size());
        assertEveryIndexMatchesTable(tx, "a", 0, 10);
        tx.commit();
    }

    public void testModifyMovesRowsInEveryIndex() {
        Transaction tx = db.newTx();
        update(tx, "update t set a = 99 where a = 5");
        assertEquals(6, ridsFromTable(tx, "a", 99).size());
        assertEveryIndexMatchesTable(tx, "a", 5, 6);
        assertEveryIndexMatchesTable(tx, "a", 99, 100);
        tx.commit();
    }

    public void testBackfillFillsIndexCreatedOnNonEmptyTable() {
        Transaction tx = db.newTx();
        update(tx, "create index ib on t(b) using btree");
        tx.commit();

        Transaction tx2 = db.newTx();
        IndexInfo ib = null;
        for (IndexInfo ii : db.mdMgr().getAllIndexInfo("t", tx2))
            if (ii.indexName().equals("ib"))
                ib = ii;
        assertNotNull(ib);
        for (int k = 0; k < 60; k++)
            assertEquals("b=" + k, ridsFromTable(tx2, "b", k), ridsFromIndex(ib, k));
        tx2.commit();
    }

    public void testGetAllIndexInfoReturnsEveryIndex() {
        Transaction tx = db.newTx();
        assertEquals(2, db.mdMgr().getAllIndexInfo("t", tx).size());
        tx.commit();
    }

    public void testGetIndexInfoReturnsOneIndexPerField() {
        Transaction tx = db.newTx();
        Map<String, IndexInfo> best = db.mdMgr().getIndexInfo("t", tx);
        assertEquals(1, best.size());
        assertTrue(best.containsKey("a"));
        tx.commit();
    }

    public void testSmallTableKeepsHashBecauseOfTie() {
        Transaction tx = db.newTx();
        assertEquals("HashIndex", db.mdMgr().getIndexInfo("t", tx).get("a").indexType());
        tx.commit();
    }

    public void testDuplicateIndexNameIsRejected() {
        Transaction tx = db.newTx();
        try {
            update(tx, "create index ia_hash on t(b)");
            fail("duplicate index name must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        tx.commit();
    }

    public void testSameFieldAndTypeTwiceIsRejected() {
        Transaction tx = db.newTx();
        try {
            update(tx, "create index another on t(a)");
            fail("a second hash index on the same field must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        tx.commit();
    }

    public void testIndexOnMissingFieldIsRejectedBeforeAnythingIsStored() {
        Transaction tx = db.newTx();
        try {
            update(tx, "create index ix on t(zzz)");
            fail("index on a missing field must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(2, db.mdMgr().getAllIndexInfo("t", tx).size());
        tx.commit();
    }

    private void assertEveryIndexMatchesTable(Transaction tx, String fld, int fromKey, int toKey) {
        List<IndexInfo> all = db.mdMgr().getAllIndexInfo("t", tx);
        for (IndexInfo ii : all) {
            if (!ii.fieldName().equals(fld))
                continue;
            for (int k = fromKey; k < toKey; k++)
                assertEquals("index=" + ii.indexName() + " key=" + k,
                             ridsFromTable(tx, fld, k), ridsFromIndex(ii, k));
        }
    }

    private Set<String> ridsFromTable(Transaction tx, String fld, int key) {
        Set<String> found = new HashSet<>();
        UpdateScan s = (UpdateScan) new TablePlan(tx, "t", db.mdMgr()).open();
        while (s.next())
            if (s.getInt(fld) == key)
                found.add(label(s.getRid()));
        s.close();
        return found;
    }

    private Set<String> ridsFromIndex(IndexInfo ii, int key) {
        Set<String> found = new HashSet<>();
        Index idx = ii.open();
        idx.beforeFirst(new Constant(key));
        while (idx.next())
            found.add(label(idx.getDataRid()));
        idx.close();
        return found;
    }

    private String label(RID rid) {
        return rid.blockNumber() + ":" + rid.slot();
    }

    private void update(Transaction tx, String sql) {
        db.planner().executeUpdate(sql, tx);
    }

    private void deleteAll(File f) {
        File[] children = f.listFiles();
        if (children != null)
            for (File c : children)
                deleteAll(c);
        f.delete();
    }
}
