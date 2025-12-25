package esypsydb.log;

import java.util.Iterator;

import esypsydb.file.BlockId;
import esypsydb.file.FileMgr;
import esypsydb.file.Page;

public class LogIterator implements Iterator<byte[]> {
    private FileMgr fm;
    private BlockId blk;
    private Page p;          // メモリ上の作業場
    private int currentpos;  // ブロック内の現在のバイト位置
    private int boundary;
    public LogIterator(FileMgr fm, BlockId blk) {
        this.fm = fm;
        this.blk = blk;
        // メモリ空間の確保
        byte[] b = new byte[fm.blockSize()];
        p = new Page(b);
        moveToBlock(blk);
    }


    public boolean hasNext() {
        // 「現在ブロックに続きがある」or「古いブロッック後ろにある」
        return currentpos < fm.blockSize() || blk.number() > 0;
    }

    // 次のログのブロックに移動
    public byte[] next() {
        // currentblkを末尾(blocksizeバイト)まで読んだら、1つ前のブロック番号にする
        if (currentpos == fm.blockSize()) {
            blk = new BlockId(blk.fileName(), blk.number()-1);
            moveToBlock(blk);
        }
        byte[] rec = p.getBytes(currentpos);        // 現在のposからログレコードを取り出す
        currentpos += rec.length + Integer.BYTES;   // 読み取った分だけ進む
        return rec;
    }

    // 指定されたブロックをディスクからメモリpに読み込み、ポインタをセットする
    public void moveToBlock(BlockId blk) {
        fm.read(blk, p);
        boundary = p.getInt(0);
        currentpos = boundary;
    }
}
