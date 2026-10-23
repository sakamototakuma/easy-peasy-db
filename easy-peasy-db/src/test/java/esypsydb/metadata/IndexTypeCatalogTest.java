package esypsydb.metadata;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;

import esypsydb.index.Index;
import esypsydb.index.btree.BTreeIndex;
import esypsydb.index.hash.HashIndex;
import esypsydb.parse.BadSyntaxException;
import esypsydb.plan.Planner;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

// CREATE INDEX の種別が idxcat に保存され、getIndexInfo / open で復元されるか
public class IndexTypeCatalogTest extends TestCase {
    private File root;
    private EasyPeasyDB db;

    protected void setUp() throws Exception {
        root = Files.createTempDirectory("indextypetest").toFile();
        db = new EasyPeasyDB(new File(root, "db").getPath());
        Transaction tx = db.newTx();
        Planner planner = db.planner();
        planner.executeUpdate("create table t (a int, b int)", tx);
        planner.executeUpdate("create index ia on t(a) using btree", tx);
        planner.executeUpdate("create index ib on t(b)", tx);
        tx.commit();
    }

    protected void tearDown() throws Exception {
        deleteAll(root);
    }

    public void testBtreeTypeIsStored() {
        Transaction tx = db.newTx();
        Map<String, IndexInfo> infos = db.mdMgr().getIndexInfo("t", tx);
        assertEquals("BTreeIndex", infos.get("a").indexType());
        tx.commit();
    }

    public void testOmittedTypeIsStoredAsHash() {
        Transaction tx = db.newTx();
        Map<String, IndexInfo> infos = db.mdMgr().getIndexInfo("t", tx);
        assertEquals("HashIndex", infos.get("b").indexType());
        tx.commit();
    }

    public void testOpenReturnsBtreeIndexForBtreeType() {
        Transaction tx = db.newTx();
        Index idx = db.mdMgr().getIndexInfo("t", tx).get("a").open();
        assertTrue(idx instanceof BTreeIndex);
        idx.close();
        tx.commit();
    }

    public void testOpenReturnsHashIndexForHashType() {
        Transaction tx = db.newTx();
        Index idx = db.mdMgr().getIndexInfo("t", tx).get("b").open();
        assertTrue(idx instanceof HashIndex);
        idx.close();
        tx.commit();
    }

    public void testUnknownTypeIsRejectedBeforeAnythingIsStored() {
        Transaction tx = db.newTx();
        try {
            db.planner().executeUpdate("create index ic on t(a) using foo", tx);
            fail("unknown index type must be rejected");
        } catch (BadSyntaxException expected) {
        }
        tx.commit();
    }

    private void deleteAll(File f) {
        File[] children = f.listFiles();
        if (children != null)
            for (File c : children)
                deleteAll(c);
        f.delete();
    }
}
