package esypsydb.metadata;

public class StatInfo {
    private int numBlocks;
    private int numRecs;
   
   /**
    * StatInfo オブジェクトを生成する
    * - distinct 値数はコンストラクタで受け取らない
    * - distinct 値はこのクラスが適当に偽装する
    * @param numblocks テーブルのブロック数
    * @param numrecs テーブルのレコード数
    */
   public StatInfo(int numblocks, int numrecs) {
      this.numBlocks = numblocks;
      this.numRecs   = numrecs;
   }
   
   /**
    * テーブルのブロック数の推定値を返す
    * @return テーブルのブロック数の推定値
    */
   public int blocksAccessed() {
      return numBlocks;
   }
   
   /**
    * テーブルのレコード数の推定値を返す
    * @return テーブルのレコード数の推定値
    */
   public int recordsOutput() {
      return numRecs;
   }
   
   /**
    * 指定フィールドの distinct 値数の推定値を返す
    * - 完全な当て推量である
    * - まともに算出する処理はこのシステムの対象外
    * @param fldname
    * @return distinct 値数の当て推量
    */
   public int distinctValues(String fldname) {
      return 1 + (numRecs / 3);
   }
}
