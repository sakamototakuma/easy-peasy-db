package esypsydb.jdbc.embedded;

import java.sql.SQLException;
import static java.sql.Types.INTEGER;
import esypsydb.record.Schema;
import esypsydb.jdbc.ResultSetMetaDataAdapter;

/**
 * ResultSetMetaData の組み込み実装
 */
public class EmbeddedMetaData extends ResultSetMetaDataAdapter {
   private Schema sch;
   
   /**
    * 指定されたスキーマをラップするメタデータオブジェクトを作成する
    * フィールドに位置インデックスでアクセスできるようスキーマのフィールド一覧を保持する
    * @param sch スキーマ
    */
   public EmbeddedMetaData(Schema sch) {
      this.sch = sch;
   }
   
   /**
    * フィールドリストのサイズを返す
    */
   public int getColumnCount() throws SQLException {
      return sch.fields().size();
   }
   
   /**
    * 指定された列番号のフィールド名を返す
    * JDBC では列番号は 1 始まりのため、リストの (column-1) 番目を取得する
    */
   public String getColumnName(int column) throws SQLException {
      return sch.fields().get(column-1);
   }
   
   /**
    * 指定された列の型を返す
    * 列のフィールド名を取得し、スキーマで型を調べる
    */
   public int getColumnType(int column) throws SQLException {
      String fldname = getColumnName(column);
      return sch.type(fldname);
   }
   
   /**
    * 指定された列の表示に必要な文字数を返す
    * 文字列型はスキーマの長さをそのまま使用する
    * 整数型は最大 6 文字と仮定しており、999,999 を超える数値は正しく表示されないことがある
    */
   public int getColumnDisplaySize(int column) throws SQLException {
      String fldname = getColumnName(column);
      int fldtype = sch.type(fldname);
      int fldlength = (fldtype == INTEGER) ? 6 : sch.length(fldname);
      return Math.max(fldname.length(), fldlength) + 1;
   }
}
