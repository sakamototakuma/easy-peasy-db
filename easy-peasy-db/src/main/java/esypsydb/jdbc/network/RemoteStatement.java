package esypsydb.jdbc.network;

import java.rmi.RemoteException;

public interface RemoteStatement {
    public RemoteResultSet executeQuery(String qry) throws RemoteException;
    public int            executeUpdate(String cmd) throws RemoteException;
    public void           close() throws RemoteException;
}
