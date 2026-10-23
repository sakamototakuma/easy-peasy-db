package esypsydb.metadata;

import junit.framework.TestCase;

import esypsydb.index.btree.BTreeIndex;
import esypsydb.index.hash.HashIndex;

// 同一フィールドの Hash と BTree のどちらを残すか（IndexMgr.prefer）。
// 期待値は設計doc: コストは max(1, blocksAccessed())、同点は Hash。rpb=256 を仮定する
// （Hash = numblocks/100、BTree = 1 + floor(log256 numblocks)）
public class IndexChoiceTest extends TestCase {
    private static final int RPB = 256;

    // Hash を今の候補、BTree を新しい候補として、BTree に置き換わるなら true
    private boolean btreeReplacesHash(int numblocks) {
        return IndexMgr.prefer(HashIndex.searchCost(numblocks, RPB), true,
                               BTreeIndex.searchCost(numblocks, RPB), false);
    }

    public void testTinyIndexKeepsHashBecauseOfTie() {
        // Hash は 0 を返すが下限 1 で扱う。BTree も 1 なので同点
        assertFalse(btreeReplacesHash(10));
    }

    public void testTieJustBelowBoundaryKeepsHash() {
        // numblocks=199: Hash 1, BTree 1
        assertFalse(btreeReplacesHash(199));
    }

    public void testBtreeWinsFromTwoHundredBlocks() {
        // numblocks=200: Hash 2, BTree 1
        assertTrue(btreeReplacesHash(200));
    }

    public void testBtreeStillWinsJustBelowTwoLevels() {
        // numblocks=255: Hash 2, BTree 1
        assertTrue(btreeReplacesHash(255));
    }

    public void testTieAtTwoLevelsKeepsHash() {
        // numblocks=256: Hash 2, BTree 2
        assertFalse(btreeReplacesHash(256));
    }

    public void testBtreeWinsAgainAtThreeHundredBlocks() {
        // numblocks=300: Hash 3, BTree 2
        assertTrue(btreeReplacesHash(300));
    }

    public void testHashReplacesBtreeOnTieWhateverTheOrder() {
        // idxcat の行の順が違っても同じ結果になる（BTree が先でも、同点なら Hash に置き換わる）
        assertTrue(IndexMgr.prefer(1, false, 1, true));
    }

    public void testHashDoesNotReplaceCheaperBtree() {
        assertFalse(IndexMgr.prefer(1, false, 3, true));
    }
}
