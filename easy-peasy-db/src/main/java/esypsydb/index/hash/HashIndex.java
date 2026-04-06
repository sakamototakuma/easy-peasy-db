package esypsydb.index.hash;

import esypsydb.query.*;
import esypsydb.record.*;
import esypsydb.tx.Transaction;
import esypsydb.index.Index;

public class HashIndex implements Index {
    public static int NUM_BUCKETS = 100;
    private Transaction tx;
    private String idxname;
    private Layout layout;
    private Constant searchKey = null;
    private TableScan ts = null;

    public HashIndex(Transaction tx, String idxname, Layout layout) {
        this.tx = tx;
        this.idxname = idxname;
        this.layout = layout;
    }

    public void beforeFirst(Constant searchkey) {
        close();
        this.searchKey = searchkey;
        int bucket = searchkey.hashCode() % NUM_BUCKETS;
        String tblname = idxname + bucket;
        ts = new TableScan(tx, tblname, layout);
    }

    public boolean next() {
        while (ts.next()) {
            if (ts.getVal("dataval").equals(searchKey))
                return true;
        }
        return false;
    }

    public RID getDataRid() {
        int blknum = ts.getInt("block");
        int id = ts.getInt("id");
        return new RID(blknum, id);
    }

    public void insert(Constant val, RID rid) {
        beforeFirst(val);
        ts.insert();
        ts.setInt("block", rid.blockNumber());
        ts.setInt("id", rid.slot());
        ts.setVal("dataval", val);
    }

    public void delete(Constant val, RID rid) {
		beforeFirst(val);
		while(next())
			if (getDataRid().equals(rid)) {
				ts.delete();
				return;
			}
	}

    public void close() {
		if (ts != null)
			ts.close();
	}

    public static int searchCost(int numblocks, int rpb) {
        return numblocks / HashIndex.NUM_BUCKETS;
    }

}
