package esypsydb.parse;

import java.util.*;

import esypsydb.query.Predicate;
import esypsydb.record.*;

public class QueryData {
    private List<String> fields;
    private Collection<String> tables;
    private Predicate pred;

    public QueryData(List<String> fields, Collection<String> tables, Predicate pred) {
        this.fields = fields;
        this.tables = tables;
        this.pred = pred;
    }

    public List<String> fields() {
        return fields;
    }

    public Collection<String> tables() {
        return tables;
    }

    public Predicate pred() {
        return pred;
    }

    /**
     * オブジェクトの状態をSQL文の形式で返す
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // SELECT句
        sb.append("select ");
        sb.append(String.join(",", fields)); // , で接続

        // FROM句
        sb.append(" from ");
        sb.append(String.join(",", tables));

        // WHERE句
        String predString = pred.toString();
        if (!predString.isEmpty())
           sb.append(" where ").append(predString);

        return sb.toString();
    }
}
