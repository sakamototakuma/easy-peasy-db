package esypsydb.metadata;

import java.util.*;
import esypsydb.tx.Transaction;
import esypsydb.record.*;
import esypsydb.query.Constant;

public class StatMgr {
       private TableMgr tblMgr;
   private Map<String,StatInfo> tablestats;
   private int numcalls;
   
   /**
    * Create the statistics manager.
    * The initial statistics are calculated by
    * traversing the entire database.
    * @param tx the startup transaction
    */
   public StatMgr(TableMgr tblMgr, Transaction tx) {
      this.tblMgr = tblMgr;
      refreshStatistics(tx);
   }
   
   /**
    * Return the statistical information about the specified table.
    * @param tblname the name of the table
    * @param layout the table's layout
    * @param tx the calling transaction
    * @return the statistical information about the table
    */
   public synchronized StatInfo getStatInfo(String tblname, 
                              Layout layout, Transaction tx) {
      numcalls++;
      if (numcalls > 100_000)
         refreshStatistics(tx);
      StatInfo si = tablestats.get(tblname);
      if (si == null) {
         si = calcTableStats(tblname, layout, tx);
         tablestats.put(tblname, si);
      }
      return si;
   }
   
   private synchronized void refreshStatistics(Transaction tx) {
      tablestats = new HashMap<String,StatInfo>();
      numcalls = 0;
      Layout tcatlayout = tblMgr.getLayout("tblcat", tx);
      TableScan tcat = new TableScan(tx, "tblcat", tcatlayout);
      while(tcat.next()) {
         String tblname = tcat.getString("tblname");
         Layout layout = tblMgr.getLayout(tblname, tx);
         StatInfo si = calcTableStats(tblname, layout, tx);
         tablestats.put(tblname, si);
      }
      tcat.close();
   }
   
   private synchronized StatInfo calcTableStats(String tblname,
                              Layout layout, Transaction tx) {
      int numRecs = 0;
      int numblocks = 0;
      Schema sch = layout.schema();
      // フィールドごとの出現値のHashSetを持ち、走査後distinct = HashSet.size()
      Map<String, Set<Constant>> seen = new HashMap<>();
      for (String fld : sch.fields())
         seen.put(fld, new HashSet<>());
      TableScan ts = new TableScan(tx, tblname, layout);
      while (ts.next()) {
         numRecs++;
         numblocks = ts.getRid().blockNumber() + 1;
         for (String fld : sch.fields()) {
            Constant val = ts.getVal(fld);
            seen.get(fld).add(val);
         }
      }
      ts.close();
      Map<String, Integer> distinctMap = new HashMap<>();
      for (Map.Entry<String, Set<Constant>> e : seen.entrySet())
         distinctMap.put(e.getKey(), e.getValue().size());
      return new StatInfo(numblocks, numRecs, distinctMap);
   }
}
