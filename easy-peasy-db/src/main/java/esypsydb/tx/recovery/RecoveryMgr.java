package esypsydb.tx.recovery;

// リカバリマネージャ. 各TXが専用に1つ持つ
public class RecoveryMgr {
    private int txnum;

    public RecoveryMgr(int txnum) {
        this.txnum = txnum;
        new StartRecord(txnum).writeLog();
    }
}
