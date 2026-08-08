package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.sql.Direction;
import de.tnttastisch.polydb.query.support.DerivedQuery.Keyword;
import de.tnttastisch.polydb.query.support.DerivedQuery.OrderItem;
import de.tnttastisch.polydb.query.support.DerivedQuery.Predicate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DerivedQuery#parse(String)}: the method-name grammar (verb/action, {@code Top}
 * limiting, operator keywords, {@code And}/{@code Or} composition, {@code IgnoreCase}, and
 * {@code OrderBy}) is decoded into the expected structure, and unknown verbs are rejected.
 */
class DerivedQueryTest {

    private static Predicate onlyPredicate(DerivedQuery query) {
        assertThat(query.getOrGroups()).hasSize(1);
        assertThat(query.getOrGroups().get(0)).hasSize(1);
        return query.getOrGroups().get(0).get(0);
    }

    @Test
    void parsesSimpleEquality() {
        DerivedQuery query = DerivedQuery.parse("findByName");
        assertThat(query.getAction()).isEqualTo(DerivedQuery.Action.FIND);
        Predicate predicate = onlyPredicate(query);
        assertThat(predicate.property()).isEqualTo("Name");
        assertThat(predicate.keyword()).isEqualTo(Keyword.SIMPLE);
        assertThat(predicate.ignoreCase()).isFalse();
    }

    @Test
    void parsesIgnoreCase() {
        Predicate predicate = onlyPredicate(DerivedQuery.parse("findByNameIgnoreCase"));
        assertThat(predicate.property()).isEqualTo("Name");
        assertThat(predicate.ignoreCase()).isTrue();
    }

    @Test
    void parsesComparisonKeywords() {
        assertThat(onlyPredicate(DerivedQuery.parse("findByQuantityGreaterThan")).keyword()).isEqualTo(Keyword.GREATER_THAN);
        assertThat(onlyPredicate(DerivedQuery.parse("findByQuantityGreaterThanEqual")).keyword()).isEqualTo(Keyword.GREATER_THAN_EQUAL);
        assertThat(onlyPredicate(DerivedQuery.parse("findByQuantityLessThan")).keyword()).isEqualTo(Keyword.LESS_THAN);
        assertThat(onlyPredicate(DerivedQuery.parse("findByQuantityBetween")).keyword()).isEqualTo(Keyword.BETWEEN);
        assertThat(onlyPredicate(DerivedQuery.parse("findByCreatedAtBefore")).keyword()).isEqualTo(Keyword.BEFORE);
        assertThat(onlyPredicate(DerivedQuery.parse("findByStatusIn")).keyword()).isEqualTo(Keyword.IN);
        assertThat(onlyPredicate(DerivedQuery.parse("findByStatusNotIn")).keyword()).isEqualTo(Keyword.NOT_IN);
        assertThat(onlyPredicate(DerivedQuery.parse("findByNameContaining")).keyword()).isEqualTo(Keyword.CONTAINING);
        assertThat(onlyPredicate(DerivedQuery.parse("findByNameStartingWith")).keyword()).isEqualTo(Keyword.STARTING_WITH);
        assertThat(onlyPredicate(DerivedQuery.parse("findByPriceIsNull")).keyword()).isEqualTo(Keyword.IS_NULL);
        assertThat(onlyPredicate(DerivedQuery.parse("findByPriceIsNotNull")).keyword()).isEqualTo(Keyword.IS_NOT_NULL);
        assertThat(onlyPredicate(DerivedQuery.parse("findByActiveTrue")).keyword()).isEqualTo(Keyword.TRUE);
        assertThat(onlyPredicate(DerivedQuery.parse("findByActiveFalse")).keyword()).isEqualTo(Keyword.FALSE);
        assertThat(onlyPredicate(DerivedQuery.parse("findByNameNot")).keyword()).isEqualTo(Keyword.NOT);
    }

    @Test
    void parsesAndComposition() {
        DerivedQuery query = DerivedQuery.parse("findByNameAndQuantityGreaterThan");
        assertThat(query.getOrGroups()).hasSize(1);
        List<Predicate> group = query.getOrGroups().get(0);
        assertThat(group).extracting(Predicate::property).containsExactly("Name", "Quantity");
        assertThat(group).extracting(Predicate::keyword).containsExactly(Keyword.SIMPLE, Keyword.GREATER_THAN);
    }

    @Test
    void parsesOrComposition() {
        DerivedQuery query = DerivedQuery.parse("findByStatusOrQuantityGreaterThan");
        assertThat(query.getOrGroups()).hasSize(2);
        assertThat(query.getOrGroups().get(0)).extracting(Predicate::property).containsExactly("Status");
        assertThat(query.getOrGroups().get(1)).extracting(Predicate::property).containsExactly("Quantity");
    }

    @Test
    void doesNotSplitPropertyNamesOnEmbeddedKeywords() {
        // "OrderId" starts with "Or" but is not a boundary (no lower-case letter precedes it).
        Predicate predicate = onlyPredicate(DerivedQuery.parse("findByOrderId"));
        assertThat(predicate.property()).isEqualTo("OrderId");
        assertThat(predicate.keyword()).isEqualTo(Keyword.SIMPLE);
    }

    @Test
    void parsesOrderBy() {
        DerivedQuery query = DerivedQuery.parse("findByActiveTrueOrderByQuantityDesc");
        assertThat(onlyPredicate(query).keyword()).isEqualTo(Keyword.TRUE);
        assertThat(query.getOrderItems()).extracting(OrderItem::property).containsExactly("Quantity");
        assertThat(query.getOrderItems()).extracting(OrderItem::direction).containsExactly(Direction.DESC);
    }

    @Test
    void parsesMultipleOrderTerms() {
        DerivedQuery query = DerivedQuery.parse("findByActiveTrueOrderByQuantityAscNameDesc");
        assertThat(query.getOrderItems()).extracting(OrderItem::property).containsExactly("Quantity", "Name");
        assertThat(query.getOrderItems()).extracting(OrderItem::direction).containsExactly(Direction.ASC, Direction.DESC);
    }

    @Test
    void parsesTopLimit() {
        assertThat(DerivedQuery.parse("findTop2ByActiveTrueOrderByQuantityDesc").getLimit()).isEqualTo(2);
        assertThat(DerivedQuery.parse("findFirstByOrderByQuantityDesc").getLimit()).isEqualTo(1);
        assertThat(DerivedQuery.parse("findByName").getLimit()).isNull();
    }

    @Test
    void parsesActions() {
        assertThat(DerivedQuery.parse("countByActiveTrue").getAction()).isEqualTo(DerivedQuery.Action.COUNT);
        assertThat(DerivedQuery.parse("existsByName").getAction()).isEqualTo(DerivedQuery.Action.EXISTS);
        assertThat(DerivedQuery.parse("deleteByStatus").getAction()).isEqualTo(DerivedQuery.Action.DELETE);
        assertThat(DerivedQuery.parse("removeByStatus").getAction()).isEqualTo(DerivedQuery.Action.DELETE);
        assertThat(DerivedQuery.parse("getByName").getAction()).isEqualTo(DerivedQuery.Action.FIND);
    }

    @Test
    void emptyCriteriaMatchesAll() {
        DerivedQuery query = DerivedQuery.parse("findFirstByOrderByQuantityDesc");
        assertThat(query.getOrGroups()).isEmpty();
        assertThat(query.getOrderItems()).hasSize(1);
    }

    @Test
    void rejectsUnknownVerb() {
        assertThatThrownBy(() -> DerivedQuery.parse("wibbleByName"))
                .isInstanceOf(PolyDBException.class)
                .hasMessageContaining("unknown verb");
    }
}
