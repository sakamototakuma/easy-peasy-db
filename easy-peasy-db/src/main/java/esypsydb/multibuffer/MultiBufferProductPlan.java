package esypsydb.multibuffer;

import java.util.List;

import esypsydb.plan.*;
import esypsydb.query.Scan;
import esypsydb.query.UpdateScan;
import esypsydb.record.*;
import esypsydb.tx.Transaction;
import esypsydb.materialize.*;

public class MultiBufferProductPlan implements Plan {
    private Plan physicalOuter, physicalInner;
    private Transaction tx;
    private Schema schema = new Schema();
    private boolean innerIsLogicalLhs;
    private int estimatedBlocks;

    public MultiBufferProductPlan(Plan lhs, Plan rhs, Transaction tx) {
        this.tx = tx;
        choosePhysicalOrder(lhs, rhs);
        schema.addAll(lhs.schema());
        schema.addAll(rhs.schema());
    }

    public Scan open() {
        TempTable tt = copyRecordFrom(physicalInner);
        String filename = tt.tablename() + ".tbl";
        Layout layout = tt.getLayout();
        Scan outerscan = physicalOuter.open();
        Schema logicalLhs = innerIsLogicalLhs ? physicalInner.schema() : physicalOuter.schema();
        Schema logicalRhs = innerIsLogicalLhs ? physicalOuter.schema() : physicalInner.schema();
        return new MultiBufferProductScan(outerscan, filename, layout, tx,
                                          logicalLhs, logicalRhs, innerIsLogicalLhs);
    }

    @Override
    public String nodeTypeName() {
        return "Nested Loop (chunked)";
    }

    public String accessMethod() {
        return "multi-buffer-product";
    }

    @Override
    public List<String> extraInfoLines() {
        if (!innerIsLogicalLhs)
            return List.of();
        return List.of("Physical: outer=right, inner=left");
    }

    /**
     * cost = B2 + (B1×B2/k)
     */
    public int blocksAccessed() {
        return estimatedBlocks;
    }

    public int recordsOutput() {
        return physicalOuter.recordsOutput() * physicalInner.recordsOutput();
    }

    public int distinctValues(String fldname) {
        Plan logicalLhs = innerIsLogicalLhs ? physicalInner : physicalOuter;
        Plan logicalRhs = innerIsLogicalLhs ? physicalOuter : physicalInner;
        if (logicalLhs.schema().hasField(fldname))
            return logicalLhs.distinctValues(fldname);
        else
            return logicalRhs.distinctValues(fldname);
    }

    public Schema schema() {
        return schema;
    }

    private TempTable copyRecordFrom(Plan p) {
        Scan src = p.open();
        Schema sch = p.schema();
        TempTable tt = new TempTable(tx, sch);
        UpdateScan dest = (UpdateScan) tt.open();
        while (src.next()) {
            dest.insert();
            for (String fldname : sch.fields())
                dest.setVal(fldname, src.getVal(fldname));
        }
        src.close();
        dest.close();
        return tt;
    }

    private void choosePhysicalOrder(Plan lhs, Plan rhs) {
        int forwardCost = productCost(lhs, rhs);
        int swappedCost = productCost(rhs, lhs);
        if (swappedCost < forwardCost) {
            physicalOuter = new MaterializePlan(tx, rhs);
            physicalInner = lhs;
            innerIsLogicalLhs = true;
            estimatedBlocks = swappedCost;
        } else {
            physicalOuter = new MaterializePlan(tx, lhs);
            physicalInner = rhs;
            innerIsLogicalLhs = false;
            estimatedBlocks = forwardCost;
        }
    }

    private int productCost(Plan outer, Plan inner) {
        int innerSize = materializedBlocks(inner);
        int numchunks = numChunks(innerSize);
        return inner.blocksAccessed() + (materializedBlocks(outer) * numchunks);
    }

    private int materializedBlocks(Plan p) {
        return new MaterializePlan(tx, p).blocksAccessed();
    }

    private int numChunks(int blocks) {
        int available = tx.availableBuffs();
        if (available <= 0)
            return blocks;
        return (int) Math.ceil((double) blocks / available);
    }
}
