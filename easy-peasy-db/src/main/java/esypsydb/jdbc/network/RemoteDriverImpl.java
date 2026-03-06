package esypsydb.jdbc.network;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import esypsydb.server.EasyPeasyDB;

@SuppressWarnings("serial")
public class RemoteDriverImpl extends UnicastRemoteObject implements RemoteDriver {
    private EasyPeasyDB db;

    public RemoteDriverImpl(EasyPeasyDB db) throws RemoteException {
        this.db = db;
    }

    public RemoteConnection connect() throws RemoteException {
        return new RemoteConnectionImpl(db);
    }

}
