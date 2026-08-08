package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Condition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Specification} combinators, using a trivial {@link Root} that maps each
 * property straight to a column so the resulting {@link Condition} SQL can be asserted. Focuses on the
 * null-safe AND/OR/NOT/allOf/anyOf behaviour.
 */
class SpecificationTest {

    /** A pass-through root: property name == column name, no value conversion. */
    private static final Root<Object> ROOT = new Root<>() {
        public Condition equal(String p, Object v) { return Condition.eq(p, v); }
        public Condition equalIgnoreCase(String p, Object v) { return Condition.eqIgnoreCase(p, v); }
        public Condition notEqual(String p, Object v) { return Condition.ne(p, v); }
        public Condition lessThan(String p, Object v) { return Condition.lt(p, v); }
        public Condition lessThanOrEqual(String p, Object v) { return Condition.lte(p, v); }
        public Condition greaterThan(String p, Object v) { return Condition.gt(p, v); }
        public Condition greaterThanOrEqual(String p, Object v) { return Condition.gte(p, v); }
        public Condition like(String p, String pattern) { return Condition.like(p, pattern); }
        public Condition contains(String p, String v) { return Condition.like(p, "%" + v + "%"); }
        public Condition startsWith(String p, String v) { return Condition.like(p, v + "%"); }
        public Condition endsWith(String p, String v) { return Condition.like(p, "%" + v); }
        public Condition in(String p, Collection<?> v) { return Condition.in(p, v); }
        public Condition between(String p, Object lo, Object hi) { return Condition.between(p, lo, hi); }
        public Condition isNull(String p) { return Condition.isNull(p); }
        public Condition isNotNull(String p) { return Condition.isNotNull(p); }
        public Condition isTrue(String p) { return Condition.eq(p, Boolean.TRUE); }
        public Condition isFalse(String p) { return Condition.eq(p, Boolean.FALSE); }
        public String column(String p) { return p; }
    };

    private static String sql(Specification<Object> spec) {
        Condition condition = spec.toCondition(ROOT);
        return condition == null ? null : condition.toSql();
    }

    @Test
    void andCombines() {
        Specification<Object> spec = Specification.<Object>where(r -> r.equal("a", 1)).and(r -> r.greaterThan("b", 2));
        assertThat(sql(spec)).isEqualTo("(a = ? AND b > ?)");
    }

    @Test
    void orCombines() {
        Specification<Object> spec = Specification.<Object>where(r -> r.equal("a", 1)).or(r -> r.equal("c", 3));
        assertThat(sql(spec)).isEqualTo("(a = ? OR c = ?)");
    }

    @Test
    void notNegates() {
        assertThat(sql(Specification.not(r -> r.equal("a", 1)))).isEqualTo("NOT (a = ?)");
    }

    @Test
    void andIgnoresNullOperand() {
        Specification<Object> spec = Specification.<Object>where(r -> r.equal("a", 1)).and(null);
        assertThat(sql(spec)).isEqualTo("a = ?");
    }

    @Test
    void unrestrictedProducesNoCondition() {
        assertThat(sql(Specification.unrestricted())).isNull();
        assertThat(sql(Specification.<Object>where(null))).isNull();
    }

    @Test
    void allOfAndsPresentConditionsIgnoringNulls() {
        List<Specification<Object>> specs = Arrays.asList(
                r -> r.equal("a", 1),
                Specification.unrestricted(),
                r -> r.equal("b", 2));
        assertThat(sql(Specification.allOf(specs))).isEqualTo("(a = ? AND b = ?)");
    }

    @Test
    void anyOfOrsPresentConditions() {
        List<Specification<Object>> specs = Arrays.asList(r -> r.equal("a", 1), r -> r.equal("b", 2));
        assertThat(sql(Specification.anyOf(specs))).isEqualTo("(a = ? OR b = ?)");
    }

    @Test
    void allOfWithOnlyNullsProducesNoCondition() {
        assertThat(sql(Specification.allOf(List.of(Specification.unrestricted())))).isNull();
    }
}
