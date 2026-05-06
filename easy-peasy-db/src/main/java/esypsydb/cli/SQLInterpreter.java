package esypsydb.cli;

import java.sql.Types;
import java.util.Scanner;

import esypsydb.plan.Plan;
import esypsydb.plan.Planner;
import esypsydb.query.Scan;
import esypsydb.record.Schema;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class SQLInterpreter {
    private static final int COL_WIDTH = 15;

    public static void main(String[] args) {
        String dbname = args.length > 0 ? args[0] : "studentdb";
        EasyPeasyDB db = new EasyPeasyDB(dbname);
        Planner planner = db.planner();

        printBanner(dbname);

        StringBuilder buf = new StringBuilder();
        try (Scanner sc = new Scanner(System.in)) {
            System.out.print("SQL> ");
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String trimmed = line.trim();

                // バッファ未使用時のメタコマンド
                if (buf.length() == 0) {
                    if (trimmed.isEmpty()) {
                        System.out.print("SQL> ");
                        continue;
                    }
                    if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit"))
                        break;
                    if (trimmed.equalsIgnoreCase("help") || trimmed.equals("?")) {
                        printHelp();
                        System.out.print("SQL> ");
                        continue;
                    }
                }

                if (buf.length() > 0)
                    buf.append('\n');
                buf.append(line);

                // 文の終端は ;  なければ継続
                if (!buf.toString().trim().endsWith(";")) {
                    System.out.print(" ... ");
                    continue;
                }

                String stmt = buf.toString().trim();
                stmt = stmt.substring(0, stmt.length() - 1).trim();
                buf.setLength(0);
                if (stmt.isEmpty()) {
                    System.out.print("SQL> ");
                    continue;
                }

                execute(planner, db, stmt);
                System.out.print("SQL> ");
            }
        }
        System.out.println("bye.");
    }

    private static void execute(Planner planner, EasyPeasyDB db, String stmt) {
        String lower = stmt.toLowerCase();
        Transaction tx = db.newTx();
        try {
            if (lower.startsWith("explain")) {
                System.out.print(planner.explainQuery(stmt, tx));
                tx.commit();
            } else if (lower.startsWith("select")) {
                Plan plan = planner.createQueryPlan(stmt, tx);
                printResult(plan);
                tx.commit();
            } else {
                int n = planner.executeUpdate(stmt, tx);
                System.out.println(n + " rows affected.");
                tx.commit();
            }
        } catch (RuntimeException e) {
            tx.rollback();
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void printBanner(String dbname) {
        System.out.println("EasyPeasyDB SQL Interpreter (db=" + dbname + ")");
        System.out.println("Statements end with ';'. Type 'help' for usage, 'exit' to quit.");
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  ; ... 文の終端 (複数行入力可)");
        System.out.println("  select / insert / update / delete / create / explain ...");
        System.out.println("  help, ? ... このメッセージ");
        System.out.println("  exit, quit ... 終了");
    }

    private static void printResult(Plan plan) {
        Scan s = plan.open();
        Schema sch = plan.schema();
        try {
            StringBuilder header = new StringBuilder();
            for (String fld : sch.fields())
                header.append(pad(fld));
            System.out.println(header);
            System.out.println(repeat('-', header.length()));

            int count = 0;
            while (s.next()) {
                StringBuilder row = new StringBuilder();
                for (String fld : sch.fields()) {
                    if (sch.type(fld) == Types.INTEGER)
                        row.append(pad(Integer.toString(s.getInt(fld))));
                    else
                        row.append(pad(s.getString(fld)));
                }
                System.out.println(row);
                count++;
            }
            System.out.println("(" + count + " rows)");
        } finally {
            s.close();
        }
    }

    private static String pad(String v) {
        if (v == null) v = "";
        if (v.length() >= COL_WIDTH) return v + " ";
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < COL_WIDTH) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        for (int i = 0; i < n; i++) buf[i] = c;
        return new String(buf);
    }
}
