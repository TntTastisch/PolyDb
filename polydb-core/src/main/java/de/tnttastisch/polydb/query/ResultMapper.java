package de.tnttastisch.polydb.query;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Strategy for turning the <em>current</em> row of a {@link ResultSet} into a value of type
 * {@code T}. Implementations must read from the row the cursor is already positioned on and must
 * not call {@link ResultSet#next()} themselves; advancing the cursor is the caller's responsibility
 * (see {@link QueryExecutor#executeQuery}).
 */
@FunctionalInterface
public interface ResultMapper<T> {

    /**
     * Maps the row the result set is currently positioned on to a {@code T}.
     *
     * @param rs the result set, positioned on a valid row
     * @return the mapped value
     * @throws SQLException if a column cannot be read or mapping fails; propagated so the caller can
     *                      wrap it with query context
     */
    T map(ResultSet rs) throws SQLException;

}
