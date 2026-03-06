package esypsydb.jdbc.network;

import java.rmi.*;

public interface RemoteDriver extends Remote {
    public RemoteConnection connect() throws RemoteException;
}
