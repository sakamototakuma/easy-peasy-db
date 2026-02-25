package esypsydb.parse;

public class CreateViewData {
    private String viewname;
    private QueryData qrydata;

    public CreateViewData(String viewname, QueryData qrydata) {
        this.viewname = viewname;
        this.qrydata = qrydata;
    }

    public String viewName() {
        return viewname;
    }

    public String viewDef() {
        return qrydata.toString();
    }
}
