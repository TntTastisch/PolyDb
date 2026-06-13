package de.tnttastisch.polydb.query;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultMapper<T> {

    T map(ResultSet rs) throws SQLException;

}
