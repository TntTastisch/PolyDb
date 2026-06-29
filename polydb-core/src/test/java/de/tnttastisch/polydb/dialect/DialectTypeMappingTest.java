package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SQL types each dialect emits for the scalar Java types added beyond the original
 * string/int/long/boolean/floating-point/temporal set: {@code BigDecimal}/{@code BigInteger},
 * {@code short}/{@code byte}, {@code char}, {@code byte[]}, {@code LocalTime}, {@code Instant},
 * {@code ZonedDateTime} and enums. The {@link FieldModel} is built directly (no reflected field is
 * needed, since {@link Dialect#getSqlType(FieldModel)} only reads its type and precision/scale).
 */
class DialectTypeMappingTest {

    private enum Colour { RED }

    /** A plain scalar column of {@code type} with the given precision/scale and a 255 default length. */
    private FieldModel field(Class<?> type, int precision, int scale) {
        return new FieldModel(null, "col", type, false, false, true, false, 255, precision, scale);
    }

    private FieldModel field(Class<?> type) {
        return field(type, 0, 0);
    }

    @Test
    void bigDecimalUsesDeclaredPrecisionAndScale() {
        assertThat(new MySqlDialect().getSqlType(field(BigDecimal.class, 18, 4))).isEqualTo("DECIMAL(18, 4)");
        assertThat(new PostgreSqlDialect().getSqlType(field(BigDecimal.class, 18, 4))).isEqualTo("NUMERIC(18, 4)");
        assertThat(new OracleDialect().getSqlType(field(BigDecimal.class, 10, 2))).isEqualTo("NUMBER(10, 2)");
        assertThat(new SqlServerDialect().getSqlType(field(BigDecimal.class, 12, 0))).isEqualTo("DECIMAL(12, 0)");
    }

    @Test
    void bigDecimalWithoutPrecisionFallsBackToBareKeyword() {
        assertThat(new MySqlDialect().getSqlType(field(BigDecimal.class))).isEqualTo("DECIMAL");
        assertThat(new H2Dialect().getSqlType(field(BigInteger.class))).isEqualTo("DECIMAL");
        assertThat(new SqliteDialect().getSqlType(field(BigDecimal.class))).isEqualTo("NUMERIC");
    }

    @Test
    void shortAndByteMapToSmallIntegerTypes() {
        assertThat(new MySqlDialect().getSqlType(field(short.class))).isEqualTo("SMALLINT");
        assertThat(new MySqlDialect().getSqlType(field(byte.class))).isEqualTo("TINYINT");
        assertThat(new PostgreSqlDialect().getSqlType(field(byte.class))).isEqualTo("SMALLINT");
        assertThat(new H2Dialect().getSqlType(field(Short.class))).isEqualTo("SMALLINT");
    }

    @Test
    void charMapsToSingleCharacterColumn() {
        assertThat(new MySqlDialect().getSqlType(field(char.class))).isEqualTo("CHAR(1)");
        assertThat(new SqlServerDialect().getSqlType(field(Character.class))).isEqualTo("NCHAR(1)");
    }

    @Test
    void binaryAndTimeTypesAreMapped() {
        assertThat(new PostgreSqlDialect().getSqlType(field(byte[].class))).isEqualTo("BYTEA");
        assertThat(new MySqlDialect().getSqlType(field(byte[].class))).isEqualTo("BLOB");
        assertThat(new PostgreSqlDialect().getSqlType(field(LocalTime.class))).isEqualTo("TIME");
        assertThat(new MySqlDialect().getSqlType(field(Instant.class))).isEqualTo("DATETIME");
        assertThat(new PostgreSqlDialect().getSqlType(field(ZonedDateTime.class))).isEqualTo("TIMESTAMPTZ");
    }

    @Test
    void enumsMapToTheStringColumnTypeOfEachDialect() {
        assertThat(new MySqlDialect().getSqlType(field(Colour.class))).isEqualTo("VARCHAR(255)");
        assertThat(new PostgreSqlDialect().getSqlType(field(Colour.class))).isEqualTo("VARCHAR(255)");
        assertThat(new OracleDialect().getSqlType(field(Colour.class))).isEqualTo("VARCHAR2(255)");
        assertThat(new SqlServerDialect().getSqlType(field(Colour.class))).isEqualTo("NVARCHAR(255)");
        assertThat(new SqliteDialect().getSqlType(field(Colour.class))).isEqualTo("TEXT");
        // Without this mapping an enum would have fallen through to a binary default (e.g. BLOB).
        assertThat(new MariaDbDialect().getSqlType(field(Colour.class))).isEqualTo("VARCHAR(255)");
    }
}
