package esypsydb.samples;

import java.nio.file.Path;

import esypsydb.server.EasyPeasyDB;

public class CreateStudentDB {
    private static final String DEFAULT_SAMPLES_DIR = "samples/student";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: CreateStudentDB <db-directory> [<scripts-dir>]");
            System.exit(1);
        }
        String dirname = args[0];
        Path base = Path.of(args.length >= 2 ? args[1] : DEFAULT_SAMPLES_DIR);

        EasyPeasyDB db = new EasyPeasyDB(dirname);
        int n = SqlScriptRunner.runFiles(db,
                base.resolve("schema.sql"),
                base.resolve("data.sql"));

        System.out.println("student database created at: " + dirname
                + " (" + n + " statements from " + base + ")");
    }
}