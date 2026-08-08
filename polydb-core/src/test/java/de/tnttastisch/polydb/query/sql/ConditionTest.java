package de.tnttastisch.polydb.query.sql;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Condition} rendering and parameter collection: each leaf operator produces the
 * expected SQL fragment with {@code ?} placeholders and contributes its bind values in order, and the
 * composite/negation combinators nest and parenthesise correctly.
 */
class ConditionTest {

    private static List<Object> paramsOf(Condition condition) {
        List<Object> params = new ArrayList<>();
        condition.collectParameters(params);
        return params;
    }

    @Test
    void rendersScalarComparisons() {
        assertThat(Condition.eq("name", "a").toSql()).isEqualTo("name = ?");
        assertThat(Condition.ne("name", "a").toSql()).isEqualTo("name <> ?");
        assertThat(Condition.lt("qty", 5).toSql()).isEqualTo("qty < ?");
        assertThat(Condition.lte("qty", 5).toSql()).isEqualTo("qty <= ?");
        assertThat(Condition.gt("qty", 5).toSql()).isEqualTo("qty > ?");
        assertThat(Condition.gte("qty", 5).toSql()).isEqualTo("qty >= ?");
        assertThat(Condition.like("name", "a%").toSql()).isEqualTo("name LIKE ?");
        assertThat(Condition.notLike("name", "a%").toSql()).isEqualTo("name NOT LIKE ?");
    }

    @Test
    void collectsScalarParameterInPlaceholderOrder() {
        assertThat(paramsOf(Condition.eq("name", "widget"))).containsExactly("widget");
        assertThat(paramsOf(Condition.between("qty", 1, 9))).containsExactly(1, 9);
    }

    @Test
    void wrapsIgnoreCaseInLower() {
        Condition condition = Condition.eqIgnoreCase("name", "Widget");
        assertThat(condition.toSql()).isEqualTo("LOWER(name) = LOWER(?)");
        assertThat(paramsOf(condition)).containsExactly("Widget");
    }

    @Test
    void rendersNullChecksWithoutParameters() {
        assertThat(Condition.isNull("name").toSql()).isEqualTo("name IS NULL");
        assertThat(Condition.isNotNull("name").toSql()).isEqualTo("name IS NOT NULL");
        assertThat(paramsOf(Condition.isNull("name"))).isEmpty();
    }

    @Test
    void rendersInWithOnePlaceholderPerValue() {
        Condition condition = Condition.in("id", List.of(1, 2, 3));
        assertThat(condition.toSql()).isEqualTo("id IN (?, ?, ?)");
        assertThat(paramsOf(condition)).containsExactly(1, 2, 3);
    }

    @Test
    void emptyInMatchesNothingAndEmptyNotInMatchesEverything() {
        assertThat(Condition.in("id", List.of()).toSql()).isEqualTo("1 = 0");
        assertThat(Condition.notIn("id", List.of()).toSql()).isEqualTo("1 = 1");
        assertThat(paramsOf(Condition.in("id", List.of()))).isEmpty();
    }

    @Test
    void rendersBetween() {
        assertThat(Condition.between("qty", 1, 9).toSql()).isEqualTo("qty BETWEEN ? AND ?");
    }

    @Test
    void combinesWithAndOrParenthesised() {
        Condition and = Condition.and(List.of(Condition.eq("a", 1), Condition.gt("b", 2)));
        assertThat(and.toSql()).isEqualTo("(a = ? AND b > ?)");

        Condition or = Condition.or(List.of(Condition.eq("a", 1), Condition.eq("c", 3)));
        assertThat(or.toSql()).isEqualTo("(a = ? OR c = ?)");

        assertThat(paramsOf(and)).containsExactly(1, 2);
    }

    @Test
    void singleElementCombineIsUnwrapped() {
        Condition single = Condition.and(List.of(Condition.eq("a", 1)));
        assertThat(single.toSql()).isEqualTo("a = ?");
    }

    @Test
    void negationWrapsInNot() {
        Condition not = Condition.not(Condition.eq("a", 1));
        assertThat(not.toSql()).isEqualTo("NOT (a = ?)");
        assertThat(paramsOf(not)).containsExactly(1);
    }

    @Test
    void nestedCompositeKeepsParameterOrder() {
        Condition nested = Condition.and(List.of(
                Condition.eq("a", 1),
                Condition.or(List.of(Condition.eq("b", 2), Condition.eq("c", 3)))));
        assertThat(nested.toSql()).isEqualTo("(a = ? AND (b = ? OR c = ?))");
        assertThat(paramsOf(nested)).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsEmptyCombine() {
        assertThatThrownBy(() -> Condition.and(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
