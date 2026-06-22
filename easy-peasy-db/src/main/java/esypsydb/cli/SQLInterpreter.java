package esypsydb.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.*;
import java.util.regex.Pattern;

import org.jline.reader.EndOfFileException;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import esypsydb.opt.HeuristicQueryPlanner;
import esypsydb.opt.SelingerQueryPlanner;
import esypsydb.parse.Parser;
import esypsydb.parse.QueryData;
import esypsydb.plan.*;
import esypsydb.query.Scan;
import esypsydb.record.Schema;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class SQLInterpreter {

    // ── session settings ───────────────────────────────────────
    private enum PlannerType { HEURISTIC, SELINGER, BASIC }
    private static PlannerType activePlanner = PlannerType.HEURISTIC;
    private static boolean activeIndexEnabled = true;

    private static QueryPlanner buildQueryPlanner(EasyPeasyDB db) {
        return switch (activePlanner) {
            case HEURISTIC -> new HeuristicQueryPlanner(db.mdMgr(), activeIndexEnabled);
            case SELINGER  -> new SelingerQueryPlanner(db.mdMgr(), activeIndexEnabled);
            case BASIC     -> new BasicQueryPlanner(db.mdMgr());
        };
    }

    private static Terminal terminal;
    private static PrintWriter out;
    private static boolean interactive;

    public static void main(String[] args) throws IOException {
        String dbname = args.length > 0 ? args[0] : "studentdb";
        EasyPeasyDB db = new EasyPeasyDB(dbname);
        Planner planner = db.planner();

        interactive = System.console() != null;
        terminal = TerminalBuilder.builder().system(true).build();
        out = interactive ? terminal.writer() : new PrintWriter(System.out, true);

        final int BATCH_SIZE = 2000;
        Transaction batchTx = null;
        int batchCount = 0;
        int totalInserted = 0;
        int spinIdx = 0;
        final char[] SPINNER = {'|', '/', '-', '\\'};

        if (interactive) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .highlighter(new SqlHighlighter())
                    .history(new DefaultHistory())
                    .variable(LineReader.HISTORY_FILE,
                            Paths.get(System.getProperty("user.home"), ".easypeasydb_history"))
                    .variable(LineReader.HISTORY_SIZE, 500)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

            printBanner(dbname);

            StringBuilder buf = new StringBuilder();
            while (true) {
                String promptStr = buf.length() == 0
                        ? ansi("SQL> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                        : ansi(" ... ", AttributedStyle.DEFAULT.faint());
                String line;
                try {
                    line = reader.readLine(promptStr);
                } catch (UserInterruptException e) {
                    buf.setLength(0);
                    out.println(dim("(cancelled)"));
                    out.flush();
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                String trimmed = line.trim();
                if (buf.length() == 0) {
                    if (trimmed.isEmpty()) continue;
                    if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) break;
                    if (trimmed.equalsIgnoreCase("help") || trimmed.equals("?")) {
                        printHelp();
                        continue;
                    }
                }

                if (buf.length() > 0) buf.append('\n');
                buf.append(line);

                if (!buf.toString().trim().endsWith(";")) continue;

                String stmt = buf.toString().trim();
                stmt = stmt.substring(0, stmt.length() - 1).trim();
                buf.setLength(0);
                if (stmt.isEmpty()) continue;

                reader.getHistory().add(stmt + ";");
                execute(planner, db, stmt);
                out.flush();
            }

        } else {
            // batch (piped) mode — plain BufferedReader, no prompts
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            StringBuilder buf = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (buf.length() > 0) buf.append('\n');
                buf.append(line);

                if (!buf.toString().trim().endsWith(";")) continue;

                String stmt = buf.toString().trim();
                stmt = stmt.substring(0, stmt.length() - 1).trim();
                buf.setLength(0);
                if (stmt.isEmpty()) continue;

                if (isUpdateStmt(stmt)) {
                    try {
                        if (batchTx == null) batchTx = db.newTx();
                        planner.executeUpdate(stmt, batchTx);
                        totalInserted++;
                        if (++batchCount >= BATCH_SIZE) {
                            batchTx.commit();
                            batchTx = null;
                            batchCount = 0;
                            System.err.printf("\rDB作成中... %c  %,d 件", SPINNER[spinIdx++ % 4], totalInserted);
                            System.err.flush();
                        }
                    } catch (RuntimeException e) {
                        if (batchTx != null) { batchTx.rollback(); batchTx = null; batchCount = 0; }
                        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                        System.err.println("\nError: " + msg);
                        e.printStackTrace(System.err);
                    }
                } else {
                    if (batchTx != null) {
                        try { batchTx.commit(); } catch (RuntimeException e) { batchTx.rollback(); }
                        batchTx = null;
                        batchCount = 0;
                    }
                    execute(planner, db, stmt);
                    out.flush(); // batch では明示 flush（explain 等は print のみで autoflush されないため）
                }
            }
        }

        if (batchTx != null) {
            try { batchTx.commit(); } catch (RuntimeException e) { batchTx.rollback(); }
        }
        if (!interactive && totalInserted > 0) {
            System.err.printf("\rDB作成中... 完了  %,d 件%n", totalInserted);
            System.err.flush();
        }

        db.checkpoint();

        if (interactive) {
            out.println(dim("bye."));
            out.flush();
        }
        terminal.close();
    }

    // ── execution ──────────────────────────────────────────────

    private static void execute(Planner planner, EasyPeasyDB db, String stmt) {
        String lower = stmt.toLowerCase();
        if (lower.startsWith("compare")) {
            executeCompare(db, stmt);
            return;
        }
        if (lower.startsWith("indexcmp")) {
            executeIndexCmp(db, stmt);
            return;
        }
        if (lower.startsWith("set")) {
            executeSet(planner, db, stmt);
            return;
        }
        Transaction tx = db.newTx();
        try {
            if (lower.startsWith("explain")) {
                out.print(planner.explainQuery(stmt, tx));
                tx.commit();
            } else if (lower.startsWith("select")) {
                Plan plan = planner.createQueryPlan(stmt, tx);
                printResult(plan);
                tx.commit();
            } else {
                int n = planner.executeUpdate(stmt, tx);
                if (interactive) {
                    if (lower.startsWith("create") || lower.startsWith("drop"))
                        out.println(green("OK."));
                    else
                        out.println(green(n + " rows affected."));
                }
                tx.commit();
            }
        } catch (Throwable e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            out.println(red("Error: " + msg));
            e.printStackTrace(out);
            out.flush();
            try { tx.rollback(); } catch (Throwable re) {
                out.println(red("Rollback failed: " + re.getClass().getName()));
                re.printStackTrace(out);
                out.flush();
            }
        }
    }

    private static void executeCompare(EasyPeasyDB db, String stmt) {
        String sql = stmt.replaceFirst("(?i)^compare\\s+", "");
        int w = Math.max(60, Math.min(terminal.getWidth() - 2, 100));
        out.println();
        out.println(bold("SQL: ") + sql);
        out.println(dim(repeat('═', w)));
        out.println();

        // active planner (respects set planner / set index)
        String activeLabel = String.format("★ Active  [%s, index=%s]",
                activePlanner.name().toLowerCase(), activeIndexEnabled ? "on" : "off");
        runPlannerComparison(db, sql, buildQueryPlanner(db), activeLabel);

        // reference: the other 2 planners (index=on)
        if (activePlanner != PlannerType.SELINGER) {
            out.println();
            runPlannerComparison(db, sql, new SelingerQueryPlanner(db.mdMgr()),  "  Selinger [DP,     index=on]");
        }
        if (activePlanner != PlannerType.HEURISTIC || !activeIndexEnabled) {
            out.println();
            runPlannerComparison(db, sql, new HeuristicQueryPlanner(db.mdMgr()), "  Heuristic[greedy, index=on]");
        }
        if (activePlanner != PlannerType.BASIC) {
            out.println();
            runPlannerComparison(db, sql, new BasicQueryPlanner(db.mdMgr()),     "  Basic    [no optimizer]   ");
        }
        out.println(dim(repeat('═', w)));
    }

    private static void executeIndexCmp(EasyPeasyDB db, String stmt) {
        String sql = stmt.replaceFirst("(?i)^indexcmp\\s+", "");
        int w = Math.max(60, Math.min(terminal.getWidth() - 2, 100));
        out.println();
        out.println(bold("SQL: ") + sql);
        out.println(dim(repeat('═', w)));
        out.println();
        runPlannerComparison(db, sql, new HeuristicQueryPlanner(db.mdMgr(), true),  "Heuristic Planner  [index ON ]");
        out.println();
        runPlannerComparison(db, sql, new HeuristicQueryPlanner(db.mdMgr(), false), "Heuristic Planner  [index OFF]");
        out.println(dim(repeat('═', w)));
    }

    private static void executeSet(Planner planner, EasyPeasyDB db, String stmt) {
        String[] parts = stmt.trim().toLowerCase().split("\\s+");
        // "set" alone → show current settings
        if (parts.length == 1) {
            out.println(bold("Current settings:"));
            out.println("  planner = " + cyan(activePlanner.name().toLowerCase()));
            out.println("  index   = " + cyan(activeIndexEnabled ? "on" : "off"));
            out.flush();
            return;
        }
        if (parts.length < 3) {
            out.println(red("Usage: set planner heuristic|selinger|basic  |  set index on|off"));
            out.flush();
            return;
        }
        String key = parts[1];
        String val = parts[2];
        switch (key) {
            case "planner" -> {
                PlannerType prev = activePlanner;
                activePlanner = switch (val) {
                    case "heuristic" -> PlannerType.HEURISTIC;
                    case "selinger"  -> PlannerType.SELINGER;
                    case "basic"     -> PlannerType.BASIC;
                    default -> { out.println(red("Unknown planner: " + val + "  (heuristic|selinger|basic)")); yield prev; }
                };
                if (activePlanner != prev) {
                    planner.setQueryPlanner(buildQueryPlanner(db));
                    out.println(green("planner → " + activePlanner.name().toLowerCase()));
                }
            }
            case "index" -> {
                boolean prev = activeIndexEnabled;
                activeIndexEnabled = switch (val) {
                    case "on"  -> true;
                    case "off" -> false;
                    default -> { out.println(red("Unknown value: " + val + "  (on|off)")); yield prev; }
                };
                if (activeIndexEnabled != prev) {
                    planner.setQueryPlanner(buildQueryPlanner(db));
                    out.println(green("index → " + (activeIndexEnabled ? "on" : "off")));
                }
            }
            default -> out.println(red("Unknown setting: " + key + "  (planner|index)"));
        }
        out.flush();
    }

    private static void runPlannerComparison(EasyPeasyDB db, String sql, QueryPlanner qp, String label) {
        out.println(yellow(">>> " + label));
        Transaction tx = db.newTx();
        try {
            Parser parser = new Parser(sql);
            QueryData data = parser.query();
            Plan plan = qp.createPlan(data, tx);
            out.print(PlanFormatter.format(plan));

            long start = System.nanoTime();
            int rows = 0;
            Scan s = plan.open();
            try {
                while (s.next())
                    rows++;
            } finally {
                s.close();
            }
            long ms = (System.nanoTime() - start) / 1_000_000;

            out.println(green(String.format(
                    "Execution time: %d ms  |  actual rows=%-6d  |  est. blocks=%d",
                    ms, rows, plan.blocksAccessed())));
            tx.commit();
        } catch (Throwable e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            out.println(red("Error: " + msg));
            e.printStackTrace(out);
            try { tx.rollback(); } catch (Throwable re) { /* suppress */ }
            out.flush();
        }
    }

    private static boolean isUpdateStmt(String stmt) {
        String lower = stmt.toLowerCase();
        return lower.startsWith("insert") || lower.startsWith("update")
                || lower.startsWith("delete") || lower.startsWith("create");
    }

    // ── result display ─────────────────────────────────────────

    private static void printResult(Plan plan) {
        final int COL_WIDTH = 15;
        Scan s = plan.open();
        Schema sch = plan.schema();
        try {
            StringBuilder header = new StringBuilder();
            for (String fld : sch.fields())
                header.append(pad(fld, COL_WIDTH));
            out.println(bold(header.toString()));
            out.println(dim(repeat('─', header.length())));

            int count = 0;
            while (s.next()) {
                StringBuilder row = new StringBuilder();
                for (String fld : sch.fields()) {
                    String val = sch.type(fld) == Types.INTEGER
                            ? Integer.toString(s.getInt(fld))
                            : s.getString(fld);
                    row.append(pad(val, COL_WIDTH));
                }
                out.println(row);
                count++;
            }
            out.println(dim("(" + count + " rows)"));
        } finally {
            s.close();
        }
    }

    // ── UI chrome ──────────────────────────────────────────────

    private static void printBanner(String dbname) {
        out.println(bold("EasyPeasyDB") + dim(" │ db=") + cyan(dbname));
        out.println(dim("SQL statements end with ';'  │  'help' for usage  │  Ctrl+D to exit"));
        out.println(dim(repeat('─', 60)));
        out.flush();
    }

    private static void printHelp() {
        out.println(bold("Commands:"));
        out.println("  " + cyan("select / insert / update / delete / create") + "  — standard SQL");
        out.println("  " + cyan("explain <select ...>") + "  — show query plan + timing");
        out.println("  " + cyan("compare <select ...>") + "  — compare selinger / heuristic / basic planner");
        out.println("  " + cyan("indexcmp <select ...>") + "  — compare index ON vs index OFF");
        out.println("  " + cyan("set planner heuristic|selinger|basic") + "  — switch active query planner");
        out.println("  " + cyan("set index on|off") + "  — enable/disable index access");
        out.println("  " + cyan("set") + "  — show current settings");
        out.println(dim("Keys: ↑↓ history  ←→ move cursor  Home/End  Ctrl+A/E  Ctrl+C cancel  Ctrl+D exit"));
        out.println(dim("Multi-line: press Enter to continue, end statement with ';' to execute."));
        out.flush();
    }

    // ── ANSI helpers ───────────────────────────────────────────

    private static String ansi(String s, AttributedStyle style) {
        return new AttributedStringBuilder()
                .style(style).append(s).style(AttributedStyle.DEFAULT).toAnsi();
    }

    private static String bold(String s) {
        return ansi(s, AttributedStyle.DEFAULT.bold());
    }

    private static String dim(String s) {
        return ansi(s, AttributedStyle.DEFAULT.faint());
    }

    private static String red(String s) {
        return ansi(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold());
    }

    private static String green(String s) {
        return ansi(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }

    private static String yellow(String s) {
        return ansi(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold());
    }

    private static String cyan(String s) {
        return ansi(s, AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }

    // ── string utilities ───────────────────────────────────────

    private static String pad(String v, int width) {
        if (v == null)
            v = "";
        if (v.length() >= width)
            return v.substring(0, width - 1) + " ";
        StringBuilder sb = new StringBuilder(v);
        while (sb.length() < width)
            sb.append(' ');
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        Arrays.fill(buf, c);
        return new String(buf);
    }

    // ── SQL syntax highlighter ─────────────────────────────────

    private static class SqlHighlighter implements Highlighter {
        private static final Set<String> KW = new HashSet<>(Arrays.asList(
                "select", "from", "where", "and", "or", "not", "join", "on", "as",
                "insert", "into", "values", "update", "set", "delete",
                "create", "table", "index", "view", "explain", "compare", "indexcmp", "set",
                "order", "by", "group", "having", "distinct", "int", "varchar"));

        @Override
        public AttributedString highlight(LineReader reader, String buffer) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            int i = 0;
            while (i < buffer.length()) {
                char c = buffer.charAt(i);
                if (c == '\'') {
                    // string literal → green
                    sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                    sb.append(c);
                    i++;
                    while (i < buffer.length() && buffer.charAt(i) != '\'')
                        sb.append(buffer.charAt(i++));
                    if (i < buffer.length()) {
                        sb.append('\'');
                        i++;
                    }
                    sb.style(AttributedStyle.DEFAULT);
                } else if (Character.isDigit(c)) {
                    // number literal → magenta
                    sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));
                    while (i < buffer.length() && Character.isDigit(buffer.charAt(i)))
                        sb.append(buffer.charAt(i++));
                    sb.style(AttributedStyle.DEFAULT);
                } else if (Character.isLetter(c) || c == '_') {
                    int j = i;
                    while (j < buffer.length()
                            && (Character.isLetterOrDigit(buffer.charAt(j)) || buffer.charAt(j) == '_'))
                        j++;
                    String word = buffer.substring(i, j);
                    if (KW.contains(word.toLowerCase())) {
                        // keyword → cyan bold
                        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold());
                        sb.append(word);
                        sb.style(AttributedStyle.DEFAULT);
                    } else {
                        sb.append(word);
                    }
                    i = j;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toAttributedString();
        }

        @Override
        public void setErrorPattern(Pattern p) {
        }

        @Override
        public void setErrorIndex(int i) {
        }
    }
}
