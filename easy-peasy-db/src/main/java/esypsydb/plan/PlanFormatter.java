package esypsydb.plan;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class PlanFormatter {

    public static String format(Plan p) {
        StringBuilder sb = new StringBuilder();
        format(p, "", sb);
        return sb.toString();
    }

    private static void format(Plan p, String indent, StringBuilder sb) {
        sb.append(indent)
          .append("- ").append(p.getClass().getSimpleName())
          .append(" [blocks=").append(p.blocksAccessed())
          .append(", rows=").append(p.recordsOutput());
        //   for (String fld : p.schema().fields())
        //     sb.append(", distinct(").append(fld).append(")=").append(p.distinctValues(fld));
            sb.append("]\n");
        for (Plan child : children(p))
            format(child, indent + "  ", sb);
    }

    private static List<Plan> children(Plan p) {
        List<Plan> result = new ArrayList<>();
        Class<?> c = p.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Plan.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(p);
                        if (v != null)
                            result.add((Plan) v);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return result;
    }
}
