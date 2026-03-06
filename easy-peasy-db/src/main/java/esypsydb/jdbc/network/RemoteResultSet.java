package esypsydb.jdbc.network;

import java.rmi.RemoteException;

public interface RemoteResultSet {
    public boolean next()                   throws RemoteException;
    public int getInt(String fldname)       throws RemoteException;
    public String getString(String fldname) throws RemoteException;
    public RemoteMetaData getMetaData()     throws RemoteException;
    public void close()                     throws RemoteException;
}
