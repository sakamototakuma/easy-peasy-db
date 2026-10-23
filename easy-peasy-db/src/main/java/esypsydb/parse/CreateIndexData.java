package esypsydb.parse;

public class CreateIndexData {
    private String idxname, tblname, fldname, idxtype;

    public CreateIndexData(String idxname, String tblname, String fldname, String idxtype) {
    this.idxname = idxname;
    this.tblname = tblname;
    this.fldname = fldname;
    this.idxtype = idxtype;
    }

    public String indexName() {
        return idxname;
    }

    public String tableName() {
        return tblname;
    }
    
    public String fieldName() {
        return fldname;
    }

    public String indexType() {
        return idxtype;
    }
}
