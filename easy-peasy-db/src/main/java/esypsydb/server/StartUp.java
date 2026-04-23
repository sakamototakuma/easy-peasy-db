package esypsydb.server;

public class StartUp {
    private static final int DEFAULT_PORT = 1099;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: StartUp <db-directory> [port]");
            System.exit(1);
        }
        String dirname = args[0];
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        new EasyPeasyDB(dirname);

        // RMIレジストリのbindとRemoteDriverの登録は、JDBCリモート層実装後に追加予定

        System.out.println("database server ready (db=" + dirname + ", port=" + port + ")");

        Thread.currentThread().join();
    }
}