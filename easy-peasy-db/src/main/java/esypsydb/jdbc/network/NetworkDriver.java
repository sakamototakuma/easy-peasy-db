package esypsydb.jdbc.network;

import java.rmi.registry.*;
import java.sql.*;
import java.util.Properties;
import esypsydb.jdbc.DriverAdapter;

public class NetworkDriver extends DriverAdapter {
    
    public Connection connect(String url, Properties prop) throws SQLException {
        try {
            String host = url.replace("jdbc:easypeasydb://", "");
            Registry reg = LocateRegistry.getRegistry(host, 1099);
            RemoteDriver rdvr = (RemoteDriver) reg.lookup("easypeasydb");
            RemoteConnection rconn = rdvr.connect();
            return new NetworkConnection(rconn);
        }
        catch (Exception e) {
            throw new SQLException();
        }
    }
}
