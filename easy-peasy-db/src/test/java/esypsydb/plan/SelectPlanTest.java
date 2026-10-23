package esypsydb.plan;

import java.io.File;
import java.nio.file.Files;

import junit.framework.TestCase;

import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

// WHERE つきの DELETE / UPDATE（SelectPlan が更新可能なスキャンを返すこと）
public class SelectPlanTest extends TestCase {
    private File root;
    private EasyPeasyDB db;

    protected void setUp() throws Exception {
        root = Files.createTempDirectory("selectplantest").toFile();
        db = new EasyPeasyDB(new File(root, "db").getPath());
        Transaction tx = db.newTx();
        db.planner().executeUpdate("create table t (a int, b int)", tx);
        db.planner().executeUpdate("insert into t (a, b) values (1, 10)", tx);
        db.planner().executeUpdate("insert into t (a, b) values (2, 20)", tx);
        db.planner().executeUpdate("insert into t (a, b) values (1, 30)", tx);
        tx.commit();
    }

    protected void tearDown() throws Exception {
        deleteAll(root);
    }

    public void testDeleteWithWhereCountsOnlyMatchingRows() {
        Transaction tx = db.newTx();
        assertEquals(2, db.planner().executeUpdate("delete from t where a = 1", tx));
        tx.commit();
    }

    public void testDeleteWithWhereLeavesOtherRows() {
        Transaction tx = db.newTx();
        db.planner().executeUpdate("delete from t where a = 1", tx);
        assertEquals(1, db.planner().executeUpdate("delete from t where a = 2", tx));
        tx.commit();
    }

    public void testUpdateWithWhereCountsOnlyMatchingRows() {
        Transaction tx = db.newTx();
        assertEquals(2, db.planner().executeUpdate("update t set b = 0 where a = 1", tx));
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
