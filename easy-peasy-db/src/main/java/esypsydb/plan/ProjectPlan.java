package esypsydb.plan;

import java.util.List;

import esypsydb.query.ProjectScan;
import esypsydb.query.Scan;
import esypsydb.record.Schema;

public class ProjectPlan implements Plan {
    private Plan p;
    private Schema schema = new Schema();
    
    public ProjectPlan(Plan p, List<String> fieldlist) {
        this.p = p;
        for (String fldname : fieldlist)
        schema.add(fldname, p.schema());
    }

    @Override
    public Scan open() {
        Scan s = p.open();
        return 
        new ProjectScan(s, schema.fields());
    }

    @Override
    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    @Override
    public int recordsOutput() {
        return p.recordsOutput();
    }

    @Override
    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    @Override
    public Schema schema() {
        return schema;
    }
}
