package esypsydb.server;

import java.io.File;
import esypsydb.file.FileMgr;
import esypsydb.log.LogMgr;
import esypsydb.buffer.BufferMgr;
import esypsydb.tx.Transaction;

public class EasyPeasyDB {
    public static int BLOCK_SIZE = 400;
    public static int BUFFER_SIZE = 8;
    public static String LOG_FILE = "easypeasydb.log";

    private FileMgr fm;
    private LogMgr lm;
    private BufferMgr bm;

    public EasyPeasyDB(String dirname, int blocksize, int buffsize) {
        File dbDirectory = new File(dirname);
        fm = new FileMgr(dbDirectory, blocksize);
        lm = new LogMgr(fm, LOG_FILE);
        bm = new BufferMgr(fm, lm, buffsize);
    }

    public Transaction newTx() {
        return new Transaction(fm, lm, bm);
    }

    public FileMgr fileMgr() {
        return fm;
    }

    public LogMgr logMgr() {
        return lm;
    }

    public BufferMgr bufferMgr() {
        return bm;
    }

    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
