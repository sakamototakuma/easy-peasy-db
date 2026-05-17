package esypsydb.index.hash;

import esypsydb.file.BlockId;
import esypsydb.query.Constant;
import esypsydb.record.Layout;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拡張可能ハッシュテーブル (Extendible Hashing)。
 *
 * ハッシュ関数 : h(x) = x & ((1 << globalDepth) - 1)
 * ディレクトリ : int[] directory  (in-memory、グローバル深度に応じて倍増)
 * バケット    : 1ブロック = 1バケット。満杯ならスプリット。
 *
 * 用途: HashJoinScan のビルドフェーズ専用 (一時ファイル、close 時に削除)
 */
public class ExtendibleHashTable {
    private static int nextTableNum = 0;

    private final Transaction tx;
    private final String filename;
    private final Layout layout;
    private final String keyField;

    // ─── ディレクトリ (in-memory) ───
    private int[] directory;
    private int globalDepth;

    // ─── プローブ用カーソル ───
    private HashBucketPage currentBucket;
    private int currentSlot;
    private Constant searchKey;

    public ExtendibleHashTable(Transaction tx, Schema schema, String keyField) {
        this.tx        = tx;
        this.filename  = "hashtbl" + (nextTableNum++) + ".tbl";
        this.keyField  = keyField;
        this.layout    = new Layout(schema);
        this.globalDepth = 1;
        this.directory = new int[]{0, 1};

        // 初期バケット 2 個を割り当てて初期化
        for (int i = 0; i < 2; i++) {
            BlockId blk = tx.append(filename);          // block i
            HashBucketPage p = new HashBucketPage(tx, blk, layout);
            p.setLocalDepth(1);
            p.clearRecords();
            p.close();
        }
    }

    public void insert(Constant key, Map<String, Constant> row) {
        while (true) {
            int dirIdx = hashIndex(key);
            BlockId blk = new BlockId(filename, directory[dirIdx]);
            HashBucketPage page = new HashBucketPage(tx, blk, layout);
            if (!page.isFull()) {
                page.insertRecord(key, row);
                page.close();
                return;
            }
            page.close();
            split(dirIdx);
            // スプリット後に再試行
        }
    }

    public void beforeFirst(Constant key) {
        if (currentBucket != null) {
            currentBucket.close();
            currentBucket = null;
        }
        this.searchKey = key;
        int dirIdx = hashIndex(key);
        BlockId blk = new BlockId(filename, directory[dirIdx]);
        this.currentBucket = new HashBucketPage(tx, blk, layout);
        this.currentSlot = -1;
    }

    public boolean next() {
        currentSlot++;
        while (currentSlot < currentBucket.getNumRecords()) {
            if (currentBucket.getVal(currentSlot, keyField).equals(searchKey))
                return true;
            currentSlot++;
        }
        return false;
    }

    public Map<String, Constant> getRow() {
        Map<String, Constant> row = new HashMap<>();
        for (String fld : layout.schema().fields())
            row.put(fld, currentBucket.getVal(currentSlot, fld));
        return row;
    }

    public void close() {
        if (currentBucket != null) {
            currentBucket.close();
            currentBucket = null;
        }
        tx.closeFile(filename);
    }

    // スプリット
    private void split(int dirIdx) {
        int blockNum = directory[dirIdx];
        BlockId blk = new BlockId(filename, blockNum);
        HashBucketPage page = new HashBucketPage(tx, blk, layout);

        int oldLocalDepth = page.getLocalDepth();

        // ディレクトリが足りなければ倍増
        if (oldLocalDepth == globalDepth)
            doubleDirectory();

        int newLocalDepth = oldLocalDepth + 1;

        // 新バケットを割り当て
        int newBlockNum = tx.size(filename);
        tx.append(filename);
        BlockId newBlk = new BlockId(filename, newBlockNum);
        HashBucketPage newPage = new HashBucketPage(tx, newBlk, layout);
        newPage.setLocalDepth(newLocalDepth);
        newPage.clearRecords();
        newPage.close();

        // ディレクトリ更新: 旧バケットを指す + 新ビット=1 → 新バケットへ
        for (int i = 0; i < directory.length; i++) {
            if (directory[i] == blockNum && (i & (1 << oldLocalDepth)) != 0)
                directory[i] = newBlockNum;
        }

        // レコード読み出し → 旧バケットをクリア
        List<Constant> keys = new ArrayList<>();
        List<Map<String, Constant>> rows = new ArrayList<>();
        int n = page.getNumRecords();
        for (int s = 0; s < n; s++) {
            keys.add(page.getVal(s, keyField));
            Map<String, Constant> row = new HashMap<>();
            for (String fld : layout.schema().fields())
                row.put(fld, page.getVal(s, fld));
            rows.add(row);
        }
        page.clearRecords();
        page.setLocalDepth(newLocalDepth);
        page.close();

        // 再挿入 (新ディレクトリに従って旧/新バケットに振り分け)
        for (int i = 0; i < keys.size(); i++)
            insert(keys.get(i), rows.get(i));
    }

    // ディレクトリ倍増
    private void doubleDirectory() {
        int n = directory.length;
        int[] newDir = new int[n * 2];
        for (int i = 0; i < n; i++) {
            newDir[i]     = directory[i];
            newDir[i + n] = directory[i];
        }
        directory = newDir;
        globalDepth++;
    }

    // ハッシュ関数  h(x) = x & ((1 << globalDepth) - 1)
    private int hashIndex(Constant key) {
        return key.hashCode() & ((1 << globalDepth) - 1);
    }
}
