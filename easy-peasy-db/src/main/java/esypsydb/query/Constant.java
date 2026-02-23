package esypsydb.query;

// IntegerかStringを隠蔽し、DBの値とするAPI
public class Constant implements Comparable<Constant> {
    private Integer ival = null;
    private String sval = null;

    public Constant(Integer ival) {
        this.ival = ival;
    }

    public Constant(String sval) {
        this.sval = sval;
    }

    // 値を取り出す
    public int asInt() {
        return ival;
    }

    public String asString() {
        return sval;
    }

    // ival != null: 整数が入ってる / ival == null: 文字列が入ってる
    //　値の比較
    @Override
    public boolean equals(Object obj) {
        Constant c = (Constant) obj;
        return (ival != null) ? ival.equals(c.ival)
                              : sval.equals(c.sval);
    }

    public int compareTo(Constant c) {
        return (ival != null) ? ival.compareTo(c.ival)
                              : sval.compareTo(c.sval);
    }

    public int hashCode() {
        return (ival != null) ? ival.hashCode() : sval.hashCode();
    }

    public String toString() {
        return (ival != null) ? ival.toString() : sval.toString();
    }
}
