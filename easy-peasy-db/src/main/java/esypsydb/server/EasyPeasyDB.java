package esypsydb.server;

import java.io.File;
import esypsydb.file.FileMgr;
import esypsydb.log.LogMgr;
import esypsydb.buffer.BufferMgr;
import esypsydb.tx.Transaction;
import esypsydb.metadata.MetadataMgr;
import esypsydb.plan.*;

public class EasyPeasyDB {
    public static int BLOCK_SIZE = 400;
    public static int BUFFER_SIZE = 8;
    public static String LOG_FILE = "easypeasydb.log";

    private FileMgr     fm;
    private BufferMgr   bm;
    private LogMgr      lm;
    private MetadataMgr mdm;
    private Planner planner;

    public EasyPeasyDB(String dirname) {
        this(dirname, BLOCK_SIZE, BUFFER_SIZE);
    }

    public EasyPeasyDB(String dirname, int blocksize, int buffsize) {
        File dbDirectory = new File(dirname);
        fm = new FileMgr(dbDirectory, blocksize);
        lm = new LogMgr(fm, LOG_FILE);
        bm = new BufferMgr(fm, lm, buffsize);
        Transaction tx = new Transaction(fm, lm, bm);
        boolean isNew = fm.isNew();
        if (!isNew)
            tx.recover();
        mdm = new MetadataMgr(isNew, tx);
        QueryPlanner qp = new BasicQueryPlanner(mdm);
        UpdatePlanner up = new BasicUpdatePlanner(mdm);
        planner = new Planner(qp, up);
        tx.commit();
    }

    public Transaction newTx() {
        return new Transaction(fm, lm, bm);
    }

    public Planner planner() {
        return planner;
    }

    public MetadataMgr mdMgr() {
        return mdm;
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
