package esypsydb.opt;

import junit.framework.TestCase;

// seq scan と index scan のコスト比較。期待値は設計doc（コスト式: seq = 1.0 × T、
// index = 1.0 × max(1, index ページ) + 4.0 × 行数）から手で出した
public class TablePlannerCostTest extends TestCase {

    public void testTinyTableUsesSeqScan() {
        // T=1, index ページ 0（下限 1）, 行数 1: index 1+4=5 > seq 1
        assertFalse(TablePlanner.preferIndex(1, 0, 1));
    }

    public void testSmallTableWithManyMatchesUsesSeqScan() {
        // T=10, 行数 10: index 1+40=41 > seq 10
        assertFalse(TablePlanner.preferIndex(10, 0, 10));
    }

    public void testLargeTableWithFewMatchesUsesIndex() {
        // T=100, 行数 10: index 41 <= seq 100
        assertTrue(TablePlanner.preferIndex(100, 0, 10));
    }

    public void testVeryLargeTableWithSelectiveKeyUsesIndex() {
        // T=300, index ページ 1, 行数 3: index 1+12=13 <= seq 300
        assertTrue(TablePlanner.preferIndex(300, 1, 3));
    }

    public void testTieGoesToIndex() {
        // T=41, 行数 10: index 41 == seq 41
        assertTrue(TablePlanner.preferIndex(41, 0, 10));
    }

    public void testSeqScanWinsByOneBlock() {
        // T=40, 行数 10: index 41 > seq 40
        assertFalse(TablePlanner.preferIndex(40, 0, 10));
    }

    public void testEmptyStatisticsKeepIndex() {
        // 統計が空（T=0）のときは比較せず、従来どおり index
        assertTrue(TablePlanner.preferIndex(0, 0, 0));
    }

    public void testIndexPagesCountAtLeastOne() {
        // Hash の searchCost は小さい表で 0 を返すが、index を引けば最低 1 ブロック読む
        assertEquals(TablePlanner.indexCost(1, 10), TablePlanner.indexCost(0, 10), 0.0);
        assertEquals(41.0, TablePlanner.indexCost(0, 10), 0.0);
    }
}
