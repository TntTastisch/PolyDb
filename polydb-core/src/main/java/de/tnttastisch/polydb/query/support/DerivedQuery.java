package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.sql.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parsed form of a derived query method name (e.g. {@code findByNameAndQuantityGreaterThan}),
 * independent of any entity metadata: the {@link Action} (find/count/exists/delete), an optional row
 * limit ({@code Top}/{@code First}), the predicate tree as {@code OR} groups of {@code AND}-joined
 * {@link Predicate}s, and the {@code OrderBy} terms. Column resolution and argument binding happen
 * later, in {@link DerivedQueryExecutor}.
 *
 * <p>Like Spring Data's parser, {@code And}/{@code Or}/direction keywords are recognised at CamelCase
 * boundaries (a keyword is a boundary only when preceded by a lower-case letter/digit and followed by
 * an upper-case letter), which keeps property names such as {@code order} or {@code description} from
 * being mis-split. A property whose name genuinely embeds an operator keyword (e.g. a field literally
 * called {@code checkIn}) is the known ambiguous case; use {@code @Query} for those.</p>
 */
public final class DerivedQuery {

    /** What the method does with the rows it selects. */
    public enum Action { FIND, COUNT, EXISTS, DELETE }

    /** A comparison keyword and how many method arguments it consumes. */
    public enum Keyword {
        SIMPLE(1), NOT(1),
        LESS_THAN(1), LESS_THAN_EQUAL(1), GREATER_THAN(1), GREATER_THAN_EQUAL(1),
        BEFORE(1), AFTER(1), BETWEEN(2), IN(1), NOT_IN(1),
        LIKE(1), NOT_LIKE(1), STARTING_WITH(1), ENDING_WITH(1), CONTAINING(1),
        IS_NULL(0), IS_NOT_NULL(0), TRUE(0), FALSE(0);

        private final int argCount;

        Keyword(int argCount) {
            this.argCount = argCount;
        }

        /** Number of method arguments this keyword binds. */
        public int argCount() {
            return argCount;
        }
    }

    /** One comparison: a property, its keyword and whether it is case-insensitive. */
    public record Predicate(String property, Keyword keyword, boolean ignoreCase) {
    }

    /** One {@code ORDER BY} term. */
    public record OrderItem(String property, Direction direction) {
    }

    /** Keyword suffixes, ordered most-specific first so longer forms win over their prefixes. */
    private static final List<Map.Entry<String, Keyword>> KEYWORDS = List.of(
            Map.entry("IsNotNull", Keyword.IS_NOT_NULL),
            Map.entry("IsNull", Keyword.IS_NULL),
            Map.entry("NotNull", Keyword.IS_NOT_NULL),
            Map.entry("Null", Keyword.IS_NULL),
            Map.entry("GreaterThanEqual", Keyword.GREATER_THAN_EQUAL),
            Map.entry("GreaterThan", Keyword.GREATER_THAN),
            Map.entry("LessThanEqual", Keyword.LESS_THAN_EQUAL),
            Map.entry("LessThan", Keyword.LESS_THAN),
            Map.entry("Between", Keyword.BETWEEN),
            Map.entry("NotIn", Keyword.NOT_IN),
            Map.entry("In", Keyword.IN),
            Map.entry("NotLike", Keyword.NOT_LIKE),
            Map.entry("Like", Keyword.LIKE),
            Map.entry("StartingWith", Keyword.STARTING_WITH),
            Map.entry("EndingWith", Keyword.ENDING_WITH),
            Map.entry("Containing", Keyword.CONTAINING),
            Map.entry("Before", Keyword.BEFORE),
            Map.entry("After", Keyword.AFTER),
            Map.entry("IsNot", Keyword.NOT),
            Map.entry("Not", Keyword.NOT),
            Map.entry("True", Keyword.TRUE),
            Map.entry("False", Keyword.FALSE),
            Map.entry("Equals", Keyword.SIMPLE),
            Map.entry("Is", Keyword.SIMPLE));

    /** Verb prefixes mapped to their action, checked in declaration order. */
    private static final List<Map.Entry<String, Action>> VERBS = List.of(
            Map.entry("find", Action.FIND),
            Map.entry("read", Action.FIND),
            Map.entry("get", Action.FIND),
            Map.entry("query", Action.FIND),
            Map.entry("search", Action.FIND),
            Map.entry("stream", Action.FIND),
            Map.entry("count", Action.COUNT),
            Map.entry("exists", Action.EXISTS),
            Map.entry("delete", Action.DELETE),
            Map.entry("remove", Action.DELETE));

    private static final Pattern AND = Pattern.compile("(?<=[a-z0-9])And(?=[A-Z])");
    private static final Pattern OR = Pattern.compile("(?<=[a-z0-9])Or(?=[A-Z])");
    private static final Pattern DIRECTION = Pattern.compile("(?<=[a-z0-9])(Asc|Desc)");
    private static final Pattern LIMIT = Pattern.compile("(Top|First)(\\d*)");

    private final Action action;
    private final Integer limit;
    private final List<List<Predicate>> orGroups;
    private final List<OrderItem> orderItems;

    private DerivedQuery(Action action, Integer limit, List<List<Predicate>> orGroups, List<OrderItem> orderItems) {
        this.action = action;
        this.limit = limit;
        this.orGroups = orGroups;
        this.orderItems = orderItems;
    }

    public Action getAction() {
        return action;
    }

    /** The {@code Top}/{@code First} row cap, or {@code null} when unbounded. */
    public Integer getLimit() {
        return limit;
    }

    /** {@code OR} groups, each an {@code AND}-joined list of predicates; empty means "match all". */
    public List<List<Predicate>> getOrGroups() {
        return orGroups;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    /**
     * Parses a repository method name into its query structure.
     *
     * @param methodName the method name, e.g. {@code findByNameOrderByQuantityDesc}
     * @return the parsed query
     * @throws PolyDBException if the name does not start with a recognised query verb
     */
    public static DerivedQuery parse(String methodName) {
        Map.Entry<String, Action> verb = matchVerb(methodName);
        String remainder = methodName.substring(verb.getKey().length());

        int byIndex = remainder.indexOf("By");
        String subject = byIndex < 0 ? remainder : remainder.substring(0, byIndex);
        String predicatePart = byIndex < 0 ? "" : remainder.substring(byIndex + 2);

        Integer limit = parseLimit(subject);

        String criteriaPart = predicatePart;
        List<OrderItem> orderItems = new ArrayList<>();
        int orderIndex = predicatePart.indexOf("OrderBy");
        if (orderIndex >= 0) {
            criteriaPart = predicatePart.substring(0, orderIndex);
            orderItems = parseOrder(predicatePart.substring(orderIndex + "OrderBy".length()));
        }

        return new DerivedQuery(verb.getValue(), limit, parseCriteria(criteriaPart), orderItems);
    }

    private static Map.Entry<String, Action> matchVerb(String methodName) {
        for (Map.Entry<String, Action> verb : VERBS) {
            if (methodName.startsWith(verb.getKey())) {
                return verb;
            }
        }
        throw new PolyDBException("Not a derived query method (unknown verb): " + methodName
                + " — expected a name starting with find/read/get/query/count/exists/delete/remove");
    }

    private static Integer parseLimit(String subject) {
        Matcher matcher = LIMIT.matcher(subject);
        if (matcher.find()) {
            String digits = matcher.group(2);
            return digits.isEmpty() ? 1 : Integer.parseInt(digits);
        }
        return null;
    }

    private static List<List<Predicate>> parseCriteria(String criteria) {
        List<List<Predicate>> orGroups = new ArrayList<>();
        if (criteria.isEmpty()) {
            return orGroups;
        }
        for (String orPart : OR.split(criteria)) {
            List<Predicate> andGroup = new ArrayList<>();
            for (String token : AND.split(orPart)) {
                if (!token.isEmpty()) {
                    andGroup.add(parsePredicate(token));
                }
            }
            if (!andGroup.isEmpty()) {
                orGroups.add(andGroup);
            }
        }
        return orGroups;
    }

    private static Predicate parsePredicate(String rawToken) {
        String token = rawToken;
        boolean ignoreCase = false;
        if (token.endsWith("IgnoreCase") && token.length() > "IgnoreCase".length()) {
            ignoreCase = true;
            token = token.substring(0, token.length() - "IgnoreCase".length());
        }
        for (Map.Entry<String, Keyword> keyword : KEYWORDS) {
            String suffix = keyword.getKey();
            if (token.length() > suffix.length() && token.endsWith(suffix)) {
                return new Predicate(token.substring(0, token.length() - suffix.length()), keyword.getValue(), ignoreCase);
            }
        }
        return new Predicate(token, Keyword.SIMPLE, ignoreCase);
    }

    private static List<OrderItem> parseOrder(String spec) {
        List<OrderItem> items = new ArrayList<>();
        Matcher matcher = DIRECTION.matcher(spec);
        int last = 0;
        while (matcher.find()) {
            String property = spec.substring(last, matcher.start());
            if (!property.isEmpty()) {
                Direction direction = matcher.group().equals("Desc") ? Direction.DESC : Direction.ASC;
                items.add(new OrderItem(property, direction));
            }
            last = matcher.end();
        }
        if (last < spec.length()) {
            items.add(new OrderItem(spec.substring(last), Direction.ASC));
        }
        return items;
    }
}
