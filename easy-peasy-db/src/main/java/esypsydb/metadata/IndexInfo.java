package esypsydb.metadata;

import static java.sql.Types.INTEGER;

import java.util.*;
import esypsydb.index.Index;
import esypsydb.index.hash.HashIndex;
import esypsydb.record.*;
import esypsydb.tx.Transaction;

/**
 * インデックスに関する情報を保持するクラス。
 * クエリプランナーがインデックス使用コストを見積もり、
 * インデックスレコードのレイアウトを取得するために使用される。
 * メソッドは基本的に Plan のメソッドと同様である。
 */
public class IndexInfo {
    private String idxname, fldname;
    private Transaction tx;
    private Schema tblSchema;
    private Layout idxLayout;
    private StatInfo si;

    /**
     * 指定されたインデックスの IndexInfo オブジェクトを生成する。
     * @param idxname インデックスの名前
     * @param fldname インデックス対象フィールドの名前
     * @param tblSchema テーブルのスキーマ
     * @param tx 呼び出し元のトランザクション
     * @param si テーブルの統計情報
     */
    public IndexInfo(String idxname, String fldname, Schema tblSchema,
                    Transaction tx,  StatInfo si) {
      this.idxname = idxname;
      this.fldname = fldname;
      this.tx = tx;
      this.tblSchema = tblSchema;
      this.idxLayout = createIdxLayout();
      this.si = si;
    }

   /**
    * このオブジェクトが表すインデックスを開く。
    * @return このインデックス情報に対応する Index オブジェクト
    */
    public Index open() {
      return new HashIndex(tx, idxname, idxLayout);
    // return new BTreeIndex(tx, idxname, idxLayout);
   }

   /**
    * 特定の検索キーを持つすべてのインデックスレコードを見つけるために
    * 必要なブロックアクセス数を見積もる。
    * テーブルのメタデータを使用してインデックスファイルのサイズと
    * ブロックあたりのインデックスレコード数を推定し、
    * 対応するインデックス種別の traversalCost メソッドに渡して見積もりを得る。
    * @return インデックスをトラバースするために必要なブロックアクセス数
    */
   public int blocksAccessed() {
      int rpb = tx.blockSize() / idxLayout.slotSize();
      int numblocks = si.recordsOutput() / rpb;
      return HashIndex.searchCost(numblocks, rpb);
    // return BTreeIndex.searchCost(numblocks, rpb);
   }

   /**
    * 検索キーを持つレコードの推定件数を返す。
    * この値は SELECT クエリの結果と同様で、
    * テーブルの総レコード数をインデックス対象フィールドの
    * 異なる値の数で割った値
    * @return 検索キーを持つレコードの推定件数
    */
   public int recordsOutput() {
      return si.recordsOutput() / si.distinctValues(fldname);
   }

   /**
    * 基になるテーブルの指定フィールドにおける異なる値の数を返す。
    * ただしインデックス対象フィールドの場合は 1 を返す。
    * @param fname 対象フィールド名
    */
   public int distinctValues(String fname) {
      return fldname.equals(fname) ? 1 : si.distinctValues(fldname);
   }

   /**
    * インデックスレコードのレイアウトを返す。
    * スキーマは dataRID（ブロック番号とレコードIDの2つの整数）と
    * dataval（インデックス対象フィールドの値）から構成される。
    * インデックス対象フィールドのスキーマ情報はテーブルのスキーマから取得する。
    * @return インデックスレコードのレイアウト
    */
   private Layout createIdxLayout() {
      Schema sch = new Schema();
      sch.addIntField("block");
      sch.addIntField("id");
      if (tblSchema.type(fldname) == INTEGER)
         sch.addIntField("dataval");
      else {
         int fldlen = tblSchema.length(fldname);
         sch.addStringField("dataval", fldlen);
      }
      return new Layout(sch);
   }
}
