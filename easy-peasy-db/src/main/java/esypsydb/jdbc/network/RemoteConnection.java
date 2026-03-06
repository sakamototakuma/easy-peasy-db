package esypsydb.jdbc.network;

import java.rmi.RemoteException;

public interface RemoteConnection {
    public RemoteStatement createStatement() throws RemoteException;
    public void close() throws RemoteException;
}
