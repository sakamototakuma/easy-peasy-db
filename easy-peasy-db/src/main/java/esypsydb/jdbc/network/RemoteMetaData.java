package esypsydb.jdbc.network;

import java.rmi.RemoteException;

public interface RemoteMetaData {
    public int    getColumnCount()              throws RemoteException;
    public String getColumnName(int column)     throws RemoteException;
    public int    getColumnType(int column)     throws RemoteException;
    public int getColumnDisplaySize(int column) throws RemoteException;
}
