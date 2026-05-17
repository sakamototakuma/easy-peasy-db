package esypsydb.metadata;
import esypsydb.tx.Transaction;
import esypsydb.record.*;
import java.util.*;

/**
 * テーブルマネージャ
 * データベースのテーブル定義（スキーマ）とフィールド定義を管理するクラスです。
 * "tblcat"（テーブルカタログ）と "fldcat"（フィールドカタログ）という2つの
 * システムテーブルを使用してメタデータを保持
 */
public class TableMgr {
    public static final int MAX_NAME = 32; 
    private Layout tcatLayout, fcatLayout;

    /**
     * システムテーブル（tblcat, fldcat）のスキーマを定義し、レイアウトを作成します。
     * データベースが新規作成される場合（isNew = true）、これらのシステムテーブル自体を作成
     */
    public TableMgr(boolean isNew, Transaction tx) {
        // --- tblcat（テーブルカタログ）のスキーマ定義 ---
        // 各レコードは「テーブル名」と「スロットサイズ（1レコードの長さ）」を持ちます
        Schema tcatSchema = new Schema();
        tcatSchema.addStringField("tblname", MAX_NAME);
        tcatSchema.addIntField("slotsize");
        tcatLayout = new Layout(tcatSchema);

        // --- fldcat（フィールドカタログ）のスキーマ定義 ---
        // 各レコードは特定のテーブルの特定のフィールドに関する情報を持ちます
        // 「テーブル名」「フィールド名」「型」「長さ」「レコード内のオフセット」
        Schema fcatSchema = new Schema();
        fcatSchema.addStringField("tblname", MAX_NAME);
        fcatSchema.addStringField("fldname", MAX_NAME);
        fcatSchema.addIntField("type");
        fcatSchema.addIntField("length");
        fcatSchema.addIntField("offset");
        fcatLayout = new Layout(fcatSchema);

        // データベースの初回起動時は、メタデータを格納するためのシステムテーブル自体を作成する
        if (isNew) {
            createTable("tblcat", tcatSchema, tx);
            createTable("fldcat", fcatSchema, tx);
        }
    }

    /**
     * 新しいテーブルを作成するメソッド
     * 指定されたテーブル名とスキーマ情報を、システムカタログ（tblcat, fldcat）に書き込みます。
     * 
     * @param tblname 作成するテーブルの名前
     * @param sch テーブルのスキーマ（フィールド定義）
     * @param tx 現在のトランザクション
     */
    public void createTable(String tblname, Schema sch, Transaction tx) {
        Layout layout = new Layout(sch);
        
        // 1. tblcat（テーブルカタログ）にテーブル情報を登録
        TableScan tcat = new TableScan(tx, "tblcat", tcatLayout);
        tcat.insert();
        tcat.setString("tblname", tblname);
        tcat.setInt("slotsize", layout.slotSize());
        tcat.close();

        // 2. fldcat（フィールドカタログ）に各フィールドの情報を登録
        TableScan fcat = new TableScan(tx, "fldcat", fcatLayout);
        for (String fldname : sch.fields()) {
            fcat.insert();
            fcat.setString("tblname", tblname);
            fcat.setString("fldname", fldname);
            fcat.setInt("type", sch.type(fldname));
            fcat.setInt("length", sch.length(fldname));
            fcat.setInt("offset", layout.offset(fldname));
        }
        fcat.close();
    }

    /**
     * 指定されたテーブルのレイアウト情報を取得するメソッド
     * システムカタログ（tblcat, fldcat）を検索し、テーブル構造（Schema, Layout）を復元
     * 
     * @param tblname 取得したいテーブルの名前
     * @param tx 現在のトランザクション
     * @return 復元されたLayoutオブジェクト
     */
    public Layout getLayout(String tblname, Transaction tx) {
        int size = -1;
        
        // 1. tblcatからテーブルのスロットサイズを取得
        TableScan tcat = new TableScan(tx, "tblcat", tcatLayout);
        while (tcat.next()) {
            if (tcat.getString("tblname").equals(tblname)) {
                size = tcat.getInt("slotsize");
                break;
            }
        }
        tcat.close();

        // 2. fldcatからフィールド情報を全て取得してスキーマを再構築
        Schema sch = new Schema();
        Map<String, Integer> offsets = new HashMap<String, Integer>();
        TableScan fcat = new TableScan(tx, "fldcat", fcatLayout);
        while (fcat.next()) {
            if(fcat.getString("tblname").equals(tblname)) {
                String fldname = fcat.getString("fldname");
                int fldtype = fcat.getInt("type");
                int fldlen = fcat.getInt("length");
                int offset = fcat.getInt("offset");
                
                // オフセット情報マップに追加
                offsets.put(fldname, offset);
                // スキーマにフィールド定義を追加
                sch.addField(fldname, fldtype, fldlen);
            }
        }
        fcat.close();
        
        // 復元した情報で新しいLayoutオブジェクトを作成して返す
        return new Layout(sch, offsets, size);
    }
}
