package esypsydb.log;

import esypsydb.file.FileMgr;
import esypsydb.file.Page;

import java.util.Iterator;

import esypsydb.file.BlockId;

// logpageを1枚もち、ログファイルの最後のブロックをメモリにキャッシュする
public class LogMgr {
    private FileMgr fm;
    private String logfile;
    private Page logpage;
    private BlockId currentblk;
    private int latestLSN = 0;     // appendした最新LSN
    private int lastSavedLSN = 0;  // ディスクに最後に書き込まれたLSN

    // ログファイルを開き、現在書き込み可能な最後の場所を特定する
    public LogMgr(FileMgr fm, String logfile) {
        this.fm = fm;
        this.logfile = logfile;
        byte[] b = new byte[fm.blockSize()];  // メモリ上にブロックサイズの配列生成
        this.logpage = new Page(b);           // 配列bの内容のPageオブジェクトで包む
        int logsize = fm.length(logfile);     // ログファイルに何ブロック保存されえてるか
        //　ログファイルのサイズが空なら、新しい空のページをブロック末尾に追加
        if (logsize == 0)
            currentblk = appendNewBlock();
        else {
            // ログファイルの右端のブロックを作業場所に
            currentblk = new BlockId(logfile, logsize-1);
            // 右端のブロックをディスクからメモリに読み込む
            fm.read(currentblk, logpage);
        }
    }

    /**
    * WALに従ったディスクへの書き込み
    * 指定されたLSNとディス上のLSNを比較
    * LSN 1〜lsn も（連続で）ディスクに載っていることを保証
    * @param lsn ログレコードのLSN
    */
    public void flush(int lsn) {
        if (lsn >= lastSavedLSN) // 等号はなくてもok
            flush();
    }

    // ログの読み出し
    public Iterator<byte[]> iterator() {
        flush();
        return new LogIterator(fm, currentblk);
    }

    // ログレコードの追加
    // 満杯なら新しいブロック
    public synchronized int append(byte[] logrec) {
        int boundary = logpage.getInt(0);
        int recsize = logrec.length;
        int bytesneeded = recsize + Integer.BYTES;     // データ本体 + 長さ情報（4byte）

        if (boundary - bytesneeded < Integer.BYTES) {  // 空き領域がないなら
            flush();
            currentblk = appendNewBlock();
            boundary = logpage.getInt(0);
        }
        int recpos = boundary - bytesneeded;           // 後ろから前に詰めるための開始位置の計算
        logpage.setBytes(recpos, logrec);              // logpageへの書き込み
        logpage.setInt(0, recpos);              // 境界情報を更新
        latestLSN += 1;                                //  LSNをインクリメント
        return latestLSN;
    }

    // 新規ブロック作成。ディスク上のログファルの末尾に
    public BlockId appendNewBlock() {
        BlockId blk = fm.append(logfile);
        logpage.setInt(0, fm.blockSize());
        fm.write(blk, logpage);
        return blk;
    }

    //現在メモリにあるブロックをディスクのブロックに書き出す
    public void flush() {
        fm.write(currentblk, logpage);
        lastSavedLSN = latestLSN;       // LSNの更新
    }
    
}
