package esypsydb.index;

import esypsydb.query.Constant;
import esypsydb.record.RID;


public interface Index {
    /**
    * 指定された検索キーを持つ最初のレコードの前にインデックスを配置します。
    * @param searchkey 検索キーの値。
    */
    public void beforeFirst(Constant searchkey);
    
    /**
    * beforeFirstメソッドで指定された検索キーを持つ次のレコードへインデックスを移動させます。
    * これ以上そのようなインデックスレコードが存在しない場合はfalseを返します。
    * @return 他に同じ検索キーを持つインデックスレコードがない場合はfalse。
    */
    public boolean next();
    
    public RID getDataRid();
    
    /**
    * 指定されたdatavalとdataRIDの値を持つインデックスレコードを挿入します。
    * @param dataval 新しいインデックスレコードのdataval。
    * @param datarid 新しいインデックスレコードのdataRID。
    */
    public void insert(Constant dataval, RID datarid);
    
    /**
    * 指定されたdatavalとdataRIDの値を持つインデックスレコードを削除します。
    * @param dataval 削除されるインデックスレコードのdataval。
    * @param datarid 削除されるインデックスレコードのdataRID。
    */
    public void delete(Constant dataval, RID datarid);
    
    public void close();
}
