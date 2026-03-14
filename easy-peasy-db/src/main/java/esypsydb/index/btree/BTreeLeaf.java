package esypsydb.index.btree;

import esypsydb.file.BlockId;
import esypsydb.query.Constant;
import esypsydb.record.Layout;
import esypsydb.record.RID;
import esypsydb.tx.Transaction;

public class BTreeLeaf {
    private Transaction tx;
   private Layout layout;
   private Constant searchkey;
   private BTPage contents;
   private int currentslot;
   private String filename;

   /**
    * 指定された葉ブロックを開き、
    * 指定された検索キーを持つ最初のレコードの直前に位置付ける
    * @param blk ブロックへの参照
    * @param layout B木葉ファイルのメタデータ
    * @param searchkey 検索キー
    * @param tx トランザクション
    */
   public BTreeLeaf(Transaction tx, BlockId blk, Layout layout, Constant searchkey) {
      this.tx = tx;
      this.layout = layout;
      this.searchkey = searchkey;
      contents = new BTPage(tx, blk, layout);
      currentslot = contents.findSlotBefore(searchkey);
      filename = blk.fileName();            
   }

   public void close() {
      contents.close();
   }

   /**
    * 検索キーを持つ次のレコードへ移動
    * 条件を満たすレコードがなくなればfalseを返す
    * @return 次のレコードがない場合はfalse
    */
   public boolean next() {
      currentslot++;
      if (currentslot >= contents.getNumRecs()) 
         return tryOverflow();
      else if (contents.getDataVal(currentslot).equals(searchkey))
         return true;
      else 
         return tryOverflow();
   }

   public RID getDataRid() {
      return contents.getDataRid(currentslot);
   }

   public void delete(RID datarid) {
      while(next())
         if(getDataRid().equals(datarid)) {
            contents.delete(currentslot);
            return;
         }
   }

   /**
    * 指定されたデータRIDと検索キーを持つ新しいレコードを挿入する。
    * ページが満杯の場合は分割され、新しいページのディレクトリエントリを返す。
    * それ以外の場合はnullを返す。
    * ページ内の全レコードが同じキーを持つ場合、分割はせずオーバーフローブロックを作成する。
    * @param datarid 新しいレコードのデータRID
    * @return 分割された場合はディレクトリエントリ、そうでない場合はnull
    */
   public DirEntry insert(RID datarid) {
      if (contents.getFlag() >= 0 && contents.getDataVal(0).compareTo(searchkey) > 0) {
         Constant firstval = contents.getDataVal(0);
         BlockId newblk = contents.split(0, contents.getFlag());
         currentslot = 0;
         contents.setFlag(-1);
         contents.insertLeaf(currentslot, searchkey, datarid); 
         return new DirEntry(firstval, newblk.number());  
      }

      currentslot++;
      contents.insertLeaf(currentslot, searchkey, datarid);
      if (!contents.isFull())
         return null;
      // ページが満杯のため分割する
      Constant firstkey = contents.getDataVal(0);
      Constant lastkey  = contents.getDataVal(contents.getNumRecs()-1);
      if (lastkey.equals(firstkey)) {
         // 最初のレコード以外を保持するオーバーフローブロックを作成する
         BlockId newblk = contents.split(1, contents.getFlag());
         contents.setFlag(newblk.number());
         return null;
      }
      else {
         int splitpos = contents.getNumRecs() / 2;
         Constant splitkey = contents.getDataVal(splitpos);
         if (splitkey.equals(firstkey)) {
            // 右へ移動し、次のキーを探す
            while (contents.getDataVal(splitpos).equals(splitkey))
               splitpos++;
            splitkey = contents.getDataVal(splitpos);
         }
         else {
            // 左へ移動し、そのキーを持つ最初のエントリを探す
            while (contents.getDataVal(splitpos-1).equals(splitkey))
               splitpos--;
         }
         BlockId newblk = contents.split(splitpos, -1);
         return new DirEntry(splitkey, newblk.number());
      }
   }

   private boolean tryOverflow() {
      Constant firstkey = contents.getDataVal(0);
      int flag = contents.getFlag();
      if (!searchkey.equals(firstkey) || flag < 0)
         return false;
      contents.close();
      BlockId nextblk = new BlockId(filename, flag);
      contents = new BTPage(tx, nextblk, layout);
      currentslot = 0;
      return true;
   }
}
