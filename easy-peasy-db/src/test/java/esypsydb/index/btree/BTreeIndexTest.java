package esypsydb.index.btree;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import junit.framework.TestCase;

import esypsydb.query.Constant;
import esypsydb.record.Layout;
import esypsydb.record.RID;
import esypsydb.record.Schema;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class BTreeIndexTest extends TestCase {
    private File root;
    private Transaction tx;

    protected void setUp() throws Exception {
        root = Files.createTempDirectory("btreetest").toFile();
        // FileMgr は存在しないディレクトリを新規 DB として作る
        EasyPeasyDB db = new EasyPeasyDB(new File(root, "db").getPath());
        tx = db.newTx();
    }

    protected void tearDown() throws Exception {
        tx.commit();
        deleteAll(root);
    }

    public void testSearchCostBoundary() {
        assertEquals(1, BTreeIndex.searchCost(0, 256));
        assertEquals(1, BTreeIndex.searchCost(1, 256));
        assertEquals(1, BTreeIndex.searchCost(255, 256));
        assertEquals(2, BTreeIndex.searchCost(256, 256));
        assertEquals(1, BTreeIndex.searchCost(-5, 256));
    }

    public void testSearchCostWithDegenerateRpb() {
        assertEquals(1, BTreeIndex.searchCost(0, 1));
        assertEquals(1, BTreeIndex.searchCost(1, 1));
        assertEquals(255, BTreeIndex.searchCost(255, 1));
        assertEquals(256, BTreeIndex.searchCost(256, 1));
        assertEquals(5, BTreeIndex.searchCost(5, 0));
    }

    public void testSearchCostNeverBelowOne() {
        int[] blocks = {Integer.MIN_VALUE, -1, 0, 1, 2, 255, 256, 65535, 65536, Integer.MAX_VALUE};
        int[] rpbs = {Integer.MIN_VALUE, 0, 1, 2, 256};
        for (int n : blocks)
            for (int rpb : rpbs)
                assertTrue("numblocks=" + n + " rpb=" + rpb, BTreeIndex.searchCost(n, rpb) >= 1);
    }

    // 400 キー × 3 件 = 1,200 件（葉が複数回分割される）を挿入順をばらして入れ、全キーが全件見つかるか
    public void testSearchFindsEveryRecordAfterLeafSplits() {
        final int keys = 400;
        final int copies = 3;
        BTreeIndex idx = new BTreeIndex(tx, "btreetest", leafLayout());

        List<int[]> entries = new ArrayList<>();
        for (int k = 0; k < keys; k++)
            for (int c = 0; c < copies; c++)
                entries.add(new int[] {k, c});
        Collections.shuffle(entries, new Random(42));
        for (int[] e : entries)
            idx.insert(new Constant(e[0]), new RID(e[0], e[1]));

        for (int k = 0; k < keys; k++)
            assertEquals("key=" + k, expected(k, copies), search(idx, k));
        idx.close();
    }

    // 同じキーが 1 ページに収まらない数（約 255 件を超える）あっても、オーバーフロー連鎖をたどって全件見つかるか
    public void testSearchFollowsOverflowChain() {
        final int duplicates = 600;
        BTreeIndex idx = new BTreeIndex(tx, "btreetest", leafLayout());
        idx.insert(new Constant(3), new RID(3, 0));
        idx.insert(new Constant(9), new RID(9, 0));
        for (int c = 0; c < duplicates; c++)
            idx.insert(new Constant(7), new RID(7, c));

        assertEquals(expected(7, duplicates), search(idx, 7));
        assertEquals(expected(3, 1), search(idx, 3));
        assertEquals(expected(9, 1), search(idx, 9));
        idx.close();
    }

    // 指定した RID だけが消え、同じキーの他の RID と他のキーは残るか
    public void testDeleteRemovesOnlyTheGivenRecord() {
        final int keys = 300;
        BTreeIndex idx = new BTreeIndex(tx, "btreetest", leafLayout());
        for (int k = 0; k < keys; k++) {
            idx.insert(new Constant(k), new RID(k, 0));
            idx.insert(new Constant(k), new RID(k, 1));
        }

        for (int k = 0; k < keys; k += 2)
            idx.delete(new Constant(k), new RID(k, 0));

        for (int k = 0; k < keys; k++) {
            Set<String> want = new HashSet<>();
            want.add(k + ":1");
            if (k % 2 == 1)
                want.add(k + ":0");
            assertEquals("key=" + k, want, search(idx, k));
        }
        idx.close();
    }

    private Layout leafLayout() {
        Schema sch = new Schema();
        sch.addIntField("block");
        sch.addIntField("id");
        sch.addIntField("dataval");
        return new Layout(sch);
    }

    private Set<String> search(BTreeIndex idx, int key) {
        Set<String> found = new HashSet<>();
        idx.beforeFirst(new Constant(key));
        while (idx.next()) {
            RID rid = idx.getDataRid();
            found.add(rid.blockNumber() + ":" + rid.slot());
        }
        idx.close();
        return found;
    }

    private Set<String> expected(int key, int copies) {
        Set<String> want = new HashSet<>();
        for (int c = 0; c < copies; c++)
            want.add(key + ":" + c);
        return want;
    }

    private void deleteAll(File f) {
        File[] children = f.listFiles();
        if (children != null)
            for (File c : children)
                deleteAll(c);
        f.delete();
    }
}
