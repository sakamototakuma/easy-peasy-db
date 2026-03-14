package esypsydb.index.btree;

import static java.sql.Types.INTEGER;

import esypsydb.file.BlockId;
import esypsydb.index.Index;
import esypsydb.query.Constant;
import esypsydb.record.Layout;
import esypsydb.record.RID;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

public class BTreeIndex implements Index {
    private Transaction tx;
    private Layout dirLayout, leafLayout;
    private String leaftbl;
    private BTreeLeaf leaf = null;
    private BlockId rootblk;

    /**
    * 指定された名前の B-tree インデックスを開く
    * 初期化の流れ: 葉テーブルの準備 -> ディレクトリテーブルの準備 -> ルートの初期化
    * 必要なファイルが存在しない場合は新規作成する
    * @param idxname インデックス名
    * @param leafsch 葉レコードのレイアウト
    * @param tx 呼び出し元トランザクション
    */
   public BTreeIndex(Transaction tx, String idxname, Layout leafLayout) {
      this.tx = tx;
      leaftbl = idxname + "leaf";
      this.leafLayout = leafLayout;
      if (tx.size(leaftbl) == 0) {
         BlockId blk = tx.append(leaftbl);
         BTPage node = new BTPage(tx, blk, leafLayout);
         node.format(blk, -1);
      }

      Schema dirsch = new Schema();
      dirsch.add("block",   leafLayout.schema());
      dirsch.add("dataval", leafLayout.schema());
      String dirtbl = idxname + "dir";
      dirLayout = new Layout(dirsch);
      rootblk = new BlockId(dirtbl, 0);
      if (tx.size(dirtbl) == 0) {
         tx.append(dirtbl);
         BTPage node = new BTPage(tx, rootblk, dirLayout);
         node.format(rootblk, 0);
         int fldtype = dirsch.type("dataval");
         Constant minval = (fldtype == INTEGER) ?
               new Constant(Integer.MIN_VALUE) :
               new Constant("");
         node.insertDir(0, minval, 0);
         node.close();
      }
   }

   /**
      * 指定検索キーに対応する葉ブロックの直前へ位置付ける
      * 処理の流れ: ルート探索 -> 葉ブロック特定 -> 葉ページをオープン
      * オープンした葉ページは next と getDataRid で再利用する
      * @see esypsydb.index.Index#beforeFirst(esypsydb.query.Constant)
    */
   public void beforeFirst(Constant searchkey) {
      close();
      BTreeDir root = new BTreeDir(tx, rootblk, dirLayout);
      int blknum = root.search(searchkey);
      root.close();
      BlockId leafblk = new BlockId(leaftbl, blknum);
      leaf = new BTreeLeaf(tx, leafblk, leafLayout, searchkey);
   }

   /**
    * 直前に指定した検索キーに一致する次の葉レコードへ移動する
    * 該当レコードが残っていない場合は false を返す
    * @see esypsydb.index.Index#next()
    */
   public boolean next() {
      return leaf.next();
   }

   public RID getDataRid() {
      return leaf.getDataRid();
   }

   /**
    * 指定レコードをインデックスへ挿入する
    * 処理の流れ: 対象葉を探索 -> 葉へ挿入 -> 必要なら葉分割 -> ルートへ昇格挿入
    * ルート分割が発生した場合は makeNewRoot を呼び出す
    * @see esypsydb.index.Index#insert(esypsydb.query.Constant, esypsydb.record.RID)
    */
   public void insert(Constant dataval, RID datarid) {
      beforeFirst(dataval);
      DirEntry e = leaf.insert(datarid);
      leaf.close();
      if (e == null)
         return;
      BTreeDir root = new BTreeDir(tx, rootblk, dirLayout);
      DirEntry e2 = root.insert(e);
      if (e2 != null)
         root.makeNewRoot(e2);
      root.close();
   }

   /**
    * 指定インデックスレコードを削除する
    * 処理の流れ: 対象葉を探索 -> 葉ページから削除
    * @see esypsydb.index.Index#delete(esypsydb.query.Constant, esypsydb.record.RID)
    */
   public void delete(Constant dataval, RID datarid) {
      beforeFirst(dataval);
      leaf.delete(datarid);
      leaf.close();
   }

   public void close() {
      if (leaf != null)
         leaf.close();
   }

   /**
    * 特定検索キーの探索に必要なブロックアクセス数を見積もる
    * 計算式: 1 + log_{rpb}(numblocks)
    * @param numblocks B-tree ディレクトリのブロック数
    * @param rpb 1ブロック当たりのエントリ数
    * @return 推定探索コスト
    */
   public static int searchCost(int numblocks, int rpb) {
      return 1 + (int)(Math.log(numblocks) / Math.log(rpb));
   }
}
