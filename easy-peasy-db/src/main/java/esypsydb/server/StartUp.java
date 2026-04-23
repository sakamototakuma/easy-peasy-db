package esypsydb.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import esypsydb.jdbc.network.RemoteDriver;
import esypsydb.jdbc.network.RemoteDriverImpl;

public class StartUp {
    private static final int DEFAULT_PORT = 1099;
    private static final String BINDING_NAME = "easypeasydb";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: StartUp <db-directory> [port]");
            System.exit(1);
        }
        String dirname = args[0];
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        EasyPeasyDB db = new EasyPeasyDB(dirname);

        Registry reg = LocateRegistry.createRegistry(port);
        RemoteDriver rd = new RemoteDriverImpl(db);
        reg.rebind(BINDING_NAME, rd);

        System.out.println("database server ready (db=" + dirname + ", port=" + port + ")");

        Thread.currentThread().join();
    }
}