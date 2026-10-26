package esypsydb.metadata;

import java.io.File;
import java.nio.file.Files;

import junit.framework.TestCase;

import esypsydb.record.Schema;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

// 実際の IndexInfo.blocksAccessed() を通して、行数ごとに Hash と BTree のどちらが残るか。
// 期待値は設計doc: BTree が厳密に安くなるのは numblocks が 200 以上。
// それ未満は同点で Hash（Hash は numblocks/100、BTree は 1+log_rpb(numblocks)）。
// 行数は「ブロック数 × rpb」で作り、rpb の仮定は testRecordsPerBlockAssumption で本体を通して確かめる
public class IndexInfoCostTest extends TestCase {
    // int キーの index スロット: dataRID 2 + dataval 1 + 使用中フラグ 1 の各 4 バイト
    private static final int INT_KEY_SLOT = 16;

    private File root;
    private Transaction tx;
    private Schema sch;
    private int rpb;

    protected void setUp() throws Exception {
        root = Files.createTempDirectory("indexinfocosttest").toFile();
        EasyPeasyDB db = new EasyPeasyDB(new File(root, "db").getPath());
        tx = db.newTx();
        sch = new Schema();
        sch.addIntField("a");
        rpb = tx.blockSize() / INT_KEY_SLOT;
    }

    protected void tearDown() throws Exception {
        tx.commit();
        deleteAll(root);
    }

    private IndexInfo info(String type, int numblocks) {
        int numrecs = numblocks * rpb;
        return new IndexInfo("i_" + type, "a", type, sch, tx, new StatInfo(numrecs / 50, numrecs));
    }

    private boolean btreeReplacesHash(int numblocks) {
        IndexInfo hash = info(IndexInfo.HASH, numblocks);
        IndexInfo btree = info(IndexInfo.BTREE, numblocks);
        return IndexMgr.prefer(hash.blocksAccessed(), hash.isHash(),
                               btree.blocksAccessed(), btree.isHash());
    }

    // Hash は numblocks/100 なので、仮定した rpb が本体と違えば 199 / 200 ブロックの境目がずれて失敗する
    public void testRecordsPerBlockAssumption() {
        String hint = "INT_KEY_SLOT(" + INT_KEY_SLOT + ") が index レコードの実サイズと違う可能性";
        assertEquals(hint, 1, info(IndexInfo.HASH, 199).blocksAccessed());
        assertEquals(hint, 2, info(IndexInfo.HASH, 200).blocksAccessed());
    }

    public void testFewBlocksKeepHash() {
        assertFalse(btreeReplacesHash(19));
    }

    public void testJustBelowBoundaryKeepsHash() {
        // numblocks=199: Hash 1, BTree 1 で同点
        assertFalse(btreeReplacesHash(199));
    }

    public void testBoundaryPicksBtree() {
        // numblocks=200: Hash 2, BTree 1
        assertTrue(btreeReplacesHash(200));
    }

    private void deleteAll(File f) {
        File[] children = f.listFiles();
        if (children != null)
            for (File c : children)
                deleteAll(c);
        f.delete();
    }
}
