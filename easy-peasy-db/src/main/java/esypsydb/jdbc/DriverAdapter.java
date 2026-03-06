package esypsydb.jdbc;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public abstract class DriverAdapter implements Driver {
    public boolean acceptsURL(String url) throws SQLException {
        throw new SQLException("operation not imolemented");
    }

    public Connection connect(String url, Properties info) throws SQLException {
        throw new SQLException("operation not implemented");
    }

    public int getMajorVersion() {
        return 0;
    }

    public int getMinorVersion() {
        return 0;
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return null;
    }

    public boolean jdbcCompliant() {
        return false;
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("operation not implemented");
    }
}
