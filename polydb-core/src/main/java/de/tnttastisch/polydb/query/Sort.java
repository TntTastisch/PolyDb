package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable, ordered set of sort {@link Order}s over entity <em>properties</em> (not columns —
 * properties are resolved to columns by the repository). Modelled on Spring Data's {@code Sort}: build
 * one with {@link #by(String...)} / {@link #by(Direction, String...)} and combine or flip direction
 * fluently. {@link #unsorted()} represents "no ordering".
 */
public final class Sort {

    private static final Sort UNSORTED = new Sort(Collections.emptyList());

    private final List<Order> orders;

    private Sort(List<Order> orders) {
        this.orders = Collections.unmodifiableList(orders);
    }

    /** The empty sort — no ordering is applied. */
    public static Sort unsorted() {
        return UNSORTED;
    }

    /** Ascending sort over the given properties, in order. */
    public static Sort by(String... properties) {
        return by(Direction.ASC, properties);
    }

    /** Sort over the given properties, all in {@code direction}. */
    public static Sort by(Direction direction, String... properties) {
        List<Order> orders = Arrays.stream(properties)
                .map(property -> new Order(property, direction))
                .collect(Collectors.toList());
        return new Sort(orders);
    }

    /** Sort from an explicit list of orders. */
    public static Sort by(List<Order> orders) {
        return orders.isEmpty() ? UNSORTED : new Sort(new ArrayList<>(orders));
    }

    /** Whether any ordering is present. */
    public boolean isSorted() {
        return !orders.isEmpty();
    }

    /** Whether this sort is empty. */
    public boolean isUnsorted() {
        return orders.isEmpty();
    }

    /** The ordering terms, in priority order. */
    public List<Order> getOrders() {
        return orders;
    }

    /** A new sort with all of this sort's properties flipped to ascending. */
    public Sort ascending() {
        return withDirection(Direction.ASC);
    }

    /** A new sort with all of this sort's properties flipped to descending. */
    public Sort descending() {
        return withDirection(Direction.DESC);
    }

    /** A new sort that applies this sort's orders first, then {@code other}'s. */
    public Sort and(Sort other) {
        List<Order> combined = new ArrayList<>(this.orders);
        combined.addAll(other.orders);
        return new Sort(combined);
    }

    private Sort withDirection(Direction direction) {
        return new Sort(orders.stream()
                .map(order -> new Order(order.getProperty(), direction, order.isIgnoreCase()))
                .collect(Collectors.toList()));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Sort other && orders.equals(other.orders);
    }

    @Override
    public int hashCode() {
        return orders.hashCode();
    }

    @Override
    public String toString() {
        return isUnsorted() ? "UNSORTED" : orders.stream().map(Order::toString).collect(Collectors.joining(", "));
    }

    /** A single sort term: an entity property, a {@link Direction}, and optional case-insensitivity. */
    public static final class Order {

        private final String property;
        private final Direction direction;
        private final boolean ignoreCase;

        public Order(String property, Direction direction) {
            this(property, direction, false);
        }

        public Order(String property, Direction direction, boolean ignoreCase) {
            this.property = Objects.requireNonNull(property, "property must not be null");
            this.direction = Objects.requireNonNull(direction, "direction must not be null");
            this.ignoreCase = ignoreCase;
        }

        /** Ascending order on {@code property}. */
        public static Order asc(String property) {
            return new Order(property, Direction.ASC);
        }

        /** Descending order on {@code property}. */
        public static Order desc(String property) {
            return new Order(property, Direction.DESC);
        }

        public String getProperty() {
            return property;
        }

        public Direction getDirection() {
            return direction;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        /** A copy of this order made case-insensitive. */
        public Order ignoreCase() {
            return new Order(property, direction, true);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Order other
                    && property.equals(other.property)
                    && direction == other.direction
                    && ignoreCase == other.ignoreCase;
        }

        @Override
        public int hashCode() {
            return Objects.hash(property, direction, ignoreCase);
        }

        @Override
        public String toString() {
            return property + ": " + direction + (ignoreCase ? " (ignore case)" : "");
        }
    }
}
