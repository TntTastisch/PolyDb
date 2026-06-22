package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link DefaultResultMapper} coerces JDBC column values into the declared entity field
 * types, in particular without losing precision. Runs against a real in-memory H2 table populated with
 * a row whose values stress the type conversions, then maps the {@link ResultSet} onto a local entity.
 */
class DefaultResultMapperTest {

    /**
     * A row of {@code VARCHAR}/{@code BIGINT}/{@code TIMESTAMP} values maps onto the entity's
     * {@code UUID}/{@code BigDecimal}/{@code LocalDateTime} fields. The {@code BIGINT} is deliberately
     * larger than {@code 2^53} to prove the mapper does not route it through {@code double} (which would
     * corrupt the value) but preserves it exactly as a {@link BigDecimal}.
     */
    @Test
    void coercesJdbcTypesToEntityFieldTypesWithoutPrecisionLoss() throws Exception {
        UUID id = UUID.randomUUID();
        long beyondDoubleMantissa = 9007199254740993L; // 2^53 + 1, not exactly representable as double

        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:maptest_" + UUID.randomUUID().toString().replace("-", ""))) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE rec (id VARCHAR(64), amount BIGINT, created_at TIMESTAMP)");
                stmt.execute("INSERT INTO rec (id, amount, created_at) VALUES ('" + id + "', "
                        + beyondDoubleMantissa + ", TIMESTAMP '2026-06-22 10:15:30')");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, amount, created_at FROM rec")) {
                assertThat(rs.next()).isTrue();

                Record mapped = new DefaultResultMapper<>(Record.class).map(rs);

                assertThat(mapped.id).isEqualTo(id);                                 // String -> UUID
                assertThat(mapped.amount).isEqualTo(new BigDecimal("9007199254740993")); // BIGINT -> BigDecimal, exact
                assertThat(mapped.createdAt).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30)); // Timestamp -> LocalDateTime
            }
        }
    }

    /** Minimal entity exercising the {@code String->UUID}, {@code BIGINT->BigDecimal} and {@code Timestamp->LocalDateTime} mappings. */
    @Entity
    static class Record {
        @Id
        @Column(name = "id")
        private UUID id;

        @Column(name = "amount")
        private BigDecimal amount;

        @Column(name = "created_at")
        private LocalDateTime createdAt;
    }
}
