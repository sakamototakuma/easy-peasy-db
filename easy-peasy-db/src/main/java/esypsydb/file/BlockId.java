package esypsydb.file;

// エンジンで扱うOSファイルと論理ブロックを扱う
public class BlockId {
    private String filename;
    private int blknum;

    public BlockId(String filename, int blknum) {
        this.filename = filename;
        this.blknum = blknum;
    }

    //getter
    // read/writeの対象ファイル名
    public String fileName() {
        return filename;
    }

    // 論理ブロック番号
    public int number() {
        return blknum;
    }

    // ファイル名が等しいかつブロック番号が等しいを判定
    public boolean equals(Object obj) {
        if (obj == null) return false;
        BlockId blk = (BlockId) obj;    // blkオブジェクトをBlockId型にキャスト
        return filename.equals(blk.filename) && blknum == blk.blknum;
    }

    // デバッグ用の文字列を返す
    public String toString() {
        return "[file" + filename + ", block" + blknum + "]";
    }

    // ハッシュコード化のメソッド
    // バケットはPageオブジェクトを管理するためのbuggerManagerのスロット
    // (ハッシュ値を実際に管理してる配列：内部配列)
    public int hashCode() {
        return 31 * filename.hashCode() + blknum;
    }


}
