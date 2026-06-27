package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.testentities.LegacyEntity;
import de.tnttastisch.polydb.testentities.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@link EntityParser} resolves a column's {@code DEFAULT} value: derived from the field's
 * initialised value for the common scalar types, overridden by an explicit
 * {@link de.tnttastisch.polydb.core.annotations.Column#defaultValue()}, and skipped for database-assigned
 * ({@code @Id}) or unreadable (no no-arg constructor) fields. These assertions are dialect-independent;
 * DDL rendering is covered by {@code DialectDefaultValueTest}.
 */
class EntityParserDefaultValueTest {

    private final EntityParser parser = new EntityParser();

    private Map<String, FieldModel> columns(Class<?> entity) {
        return parser.parseEntity(entity).getFields().stream()
                .collect(Collectors.toMap(FieldModel::getColumnName, Function.identity()));
    }

    @Test
    void derivesBooleanFalseFromInitialiser() {
        assertThat(columns(Player.class).get("notify").getDefaultValue()).isEqualTo("false");
    }

    @Test
    void derivesBooleanTrueFromInitialiser() {
        assertThat(columns(Player.class).get("premium").getDefaultValue()).isEqualTo("true");
    }

    @Test
    void derivesIntegerLiteralFromInitialiser() {
        assertThat(columns(Player.class).get("coins").getDefaultValue()).isEqualTo("100");
    }

    @Test
    void derivesDecimalLiteralFromInitialiser() {
        assertThat(columns(Player.class).get("balance").getDefaultValue()).isEqualTo("4.5");
    }

    @Test
    void quotesStringInitialiser() {
        assertThat(columns(Player.class).get("nickname").getDefaultValue()).isEqualTo("'unknown'");
    }

    @Test
    void escapesSingleQuotesInStringInitialiser() {
        assertThat(columns(Player.class).get("motto").getDefaultValue()).isEqualTo("'it''s me'");
    }

    @Test
    void derivesEnumByName() {
        assertThat(columns(Player.class).get("rank").getDefaultValue()).isEqualTo("'BRONZE'");
    }

    @Test
    void explicitDefaultOverridesInitialiser() {
        assertThat(columns(Player.class).get("title").getDefaultValue()).isEqualTo("'rookie'");
    }

    @Test
    void uninitialisedFieldHasNoDefault() {
        FieldModel bio = columns(Player.class).get("bio");
        assertThat(bio.hasDefault()).isFalse();
        assertThat(bio.getDefaultValue()).isEmpty();
    }

    @Test
    void idColumnHasNoDefault() {
        assertThat(columns(Player.class).get("id").hasDefault()).isFalse();
    }

    @Test
    void explicitDefaultStillAppliesWithoutNoArgConstructor() {
        assertThat(columns(LegacyEntity.class).get("status").getDefaultValue()).isEqualTo("'NEW'");
    }

    @Test
    void derivedDefaultSkippedWithoutNoArgConstructor() {
        assertThat(columns(LegacyEntity.class).get("active").hasDefault()).isFalse();
    }
}
