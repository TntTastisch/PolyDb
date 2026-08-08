package de.tnttastisch.polydb.query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Thread-bound registry of the connection that an active {@link TransactionTemplate} has opened, keyed
 * by {@link DataSource} so several PolyDB instances on one thread stay independent. {@link QueryExecutor}
 * consults it: when a connection is bound for its data source, every statement runs on that connection
 * (and is <em>not</em> closed per call), which is what makes a sequence of writes — including cascades —
 * atomic.
 */
final class TransactionResources {

    private static final ThreadLocal<Map<DataSource, Connection>> BOUND = ThreadLocal.withInitial(HashMap::new);

    private TransactionResources() {
    }

    /** The transactional connection bound for {@code dataSource} on this thread, or {@code null}. */
    static Connection boundConnection(DataSource dataSource) {
        return BOUND.get().get(dataSource);
    }

    /** Whether a transaction is active for {@code dataSource} on this thread. */
    static boolean isActive(DataSource dataSource) {
        return BOUND.get().containsKey(dataSource);
    }

    static void bind(DataSource dataSource, Connection connection) {
        BOUND.get().put(dataSource, connection);
    }

    static void unbind(DataSource dataSource) {
        Map<DataSource, Connection> bound = BOUND.get();
        bound.remove(dataSource);
        if (bound.isEmpty()) {
            BOUND.remove();
        }
    }
}
