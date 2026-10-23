package esypsydb.parse;

import junit.framework.TestCase;

// CREATE INDEX name ON tbl(fld) [USING hash|btree] の構文。省略時は従来どおり Hash
public class ParserCreateIndexTest extends TestCase {

    private CreateIndexData parse(String sql) {
        return (CreateIndexData) new Parser(sql).updateCmd();
    }

    public void testWithoutUsingDefaultsToHash() {
        assertEquals("hash", parse("create index i1 on t(f)").indexType());
    }

    public void testUsingBtree() {
        assertEquals("btree", parse("create index i1 on t(f) using btree").indexType());
    }

    public void testUsingHash() {
        assertEquals("hash", parse("create index i1 on t(f) using hash").indexType());
    }

    public void testUsingKeepsIndexTableAndFieldNames() {
        CreateIndexData d = parse("create index i1 on t(f) using btree");
        assertEquals("i1", d.indexName());
        assertEquals("t", d.tableName());
        assertEquals("f", d.fieldName());
    }

    public void testUnknownTypeIsRejected() {
        try {
            parse("create index i1 on t(f) using foo");
            fail("unknown index type must be rejected");
        } catch (BadSyntaxException expected) {
        }
    }

    public void testUsingWithoutTypeIsRejected() {
        try {
            parse("create index i1 on t(f) using");
            fail("USING without a type must be rejected");
        } catch (BadSyntaxException expected) {
        }
    }
}
