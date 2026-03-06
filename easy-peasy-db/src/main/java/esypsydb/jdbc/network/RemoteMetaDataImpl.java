package esypsydb.jdbc.network;

import java.rmi.RemoteException;
import static java.sql.Types.INTEGER;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import esypsydb.record.Schema; 

@SuppressWarnings("serial")
public class RemoteMetaDataImpl extends UnicastRemoteObject implements RemoteMetaData {
    private Schema sch;
    private List<String> fields = new ArrayList<String>();
   
   /**
    * 指定されたスキーマをラップするメタデータオブジェクトを作成s
    * @param sch スキーマ
    * @throws RemoteException
    */
    public RemoteMetaDataImpl(Schema sch) throws RemoteException {
      this.sch = sch;
      for (String fld : sch.fields())
            fields.add(fld);
   }
   
   public int getColumnCount() throws RemoteException {
      return fields.size();
   }
   
   public String getColumnName(int column) throws RemoteException {
      return fields.get(column-1);
   }
   
   public int getColumnType(int column) throws RemoteException {
      String fldname = getColumnName(column);
      return sch.type(fldname);
   }
   
   public int getColumnDisplaySize(int column) throws RemoteException {
      String fldname = getColumnName(column);
      int fldtype = sch.type(fldname);
      int fldlength = (fldtype == INTEGER) ? 6 : sch.length(fldname);
      return Math.max(fldname.length(), fldlength) + 1;
   }
}
