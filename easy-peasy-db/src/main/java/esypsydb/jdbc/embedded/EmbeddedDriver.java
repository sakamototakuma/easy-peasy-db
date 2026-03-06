package esypsydb.jdbc.embedded;

import java.util.Properties;
import java.sql.SQLException;
import esypsydb.server.EasyPeasyDB;
import esypsydb.jdbc.DriverAdapter;

/**
 * RemoteDriver の組み込み実装
 */

public class EmbeddedDriver extends DriverAdapter {   
   /**
    * 新しい EmbeddedConnection を作成して返す
    */
   public EmbeddedConnection connect(String url, Properties p) throws SQLException {
      String dbname = url.replace("jdbc:esypsydb:", "");
      EasyPeasyDB db = new EasyPeasyDB(dbname);
      return new EmbeddedConnection(db);
   }
}

