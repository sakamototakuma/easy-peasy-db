package esypsydb.samples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import esypsydb.plan.Planner;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class SqlScriptRunner {

    public static int runFiles(EasyPeasyDB db, Path... scripts) throws Exception {
        Transaction tx = db.newTx();
        Planner planner = db.planner();
        int count = 0;
        for (Path script : scripts) {
            for (String stmt : parseStatements(Files.readString(script))) {
                execute(planner, tx, stmt);
                count++;
            }
        }
        tx.commit();
        return count;
    }

    static List<String> parseStatements(String text) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\\R")) {
            int idx = line.indexOf("--");
            if (idx >= 0) line = line.substring(0, idx);
            cleaned.append(line).append('\n');
        }
        List<String> stmts = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String s = part.trim();
            if (!s.isEmpty()) stmts.add(s);
        }
        return stmts;
    }

    private static void execute(Planner planner, Transaction tx, String stmt) {
        String first = stmt.split("\\s+", 2)[0].toLowerCase();
        if (first.equals("select")) {
            planner.createQueryPlan(stmt, tx);
        } else {
            planner.executeUpdate(stmt, tx);
        }
    }
}