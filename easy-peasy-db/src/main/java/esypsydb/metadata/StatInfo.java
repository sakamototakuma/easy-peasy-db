package esypsydb.metadata;

import java.util.Collections;
import java.util.Map;

public class StatInfo {
    private int numBlocks;
    private int numRecs;
    private final Map<String, Integer> distinctMap;

   /**
    * StatInfo オブジェクトを生成する
    * @param numblocks テーブルのブロック数
    * @param numrecs テーブルのレコード数
    */
   public StatInfo(int numblocks, int numrecs) {
      this(numblocks, numrecs, Collections.emptyMap());
   }

   /**
    * StatInfo オブジェクトを生成
    * @param numblocks テーブルのブロック数
    * @param numrecs テーブルのレコード数
    * @param distinctMap フィールドごとの distinct値数の実測値
    */
   public StatInfo(int numblocks, int numrecs, Map<String, Integer> distinctMap) {
      this.numBlocks = numblocks;
      this.numRecs   = numrecs;
      this.distinctMap = distinctMap;
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
    * @param fldname
    * @return distinct 値数
    */
   public int distinctValues(String fldname) {
      Integer v = distinctMap.get(fldname);
      if (v != null)
         return Math.max(1, v);
      return 1 + (numRecs / 3);
   }
}
