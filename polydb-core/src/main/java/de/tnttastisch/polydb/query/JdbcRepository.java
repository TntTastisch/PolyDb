package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.core.annotations.CascadeType;
import de.tnttastisch.polydb.core.annotations.FetchType;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.query.sql.Condition;
import de.tnttastisch.polydb.query.sql.Order;
import de.tnttastisch.polydb.query.sql.SqlBuilder;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-backed {@link CrudRepository}. In addition to scalar CRUD it resolves entity relations: owning
 * foreign keys are written from the associated entity's id, eager associations are loaded one level
 * deep on read, and {@link CascadeType} operations are propagated to associated entities.
 *
 * <p>Reads, counts and existence checks are assembled through {@link SqlBuilder} from a
 * {@link Condition} tree, which is also the substrate reused by derived query methods and
 * specifications. Lazy relations are not auto-populated (PolyDB does not generate runtime proxies for
 * associations yet) and cross-table cascades are not wrapped in a single transaction.</p>
 *
 * @param <T>  the entity type this repository manages
 * @param <ID> the type of the entity's {@code @Id} field
 */
public final class JdbcRepository<T, ID> implements CrudRepository<T, ID> {

    /** Eager relations are resolved this many levels deep from the root entity. */
    private static final int DEFAULT_DEPTH = 1;

    /** Guards against infinite save cascade recursion on bidirectional associations. */
    private static final ThreadLocal<Set<Object>> IN_FLIGHT =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    /** Guards against infinite delete cascade recursion (keyed by entity class + id). */
    private static final ThreadLocal<Set<String>> DELETING =
            ThreadLocal.withInitial(HashSet::new);

    private final Class<T> entityClass;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final QueryExecutor executor;
    private final DefaultResultMapper<T> mapper;
    private final EntityModel model;
    private final FieldModel idField;
    private final Map<Class<?>, JdbcRepository<?, ?>> registry;

    public JdbcRepository(Class<T> entityClass, DataSource dataSource, Dialect dialect) {
        this(entityClass, dataSource, dialect, new HashMap<>());
    }

    private JdbcRepository(Class<T> entityClass, DataSource dataSource, Dialect dialect, Map<Class<?>, JdbcRepository<?, ?>> registry) {
        this.entityClass = entityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.executor = new QueryExecutor(dataSource);
        this.model = new EntityParser().parseEntity(entityClass);
        this.mapper = new DefaultResultMapper<>(entityClass, model);
        this.idField = model.getFields().stream()
                .filter(FieldModel::isId)
                .findFirst()
                .orElseThrow(() -> new PolyDBException("Entity " + entityClass.getName() + " has no @Id field"));
        this.registry = registry;
        registry.put(entityClass, this);
    }

    // ------------------------------------------------------------------ public API

    @Override
    public <S extends T> S save(S entity) {
        doSave(entity);
        return entity;
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            doSave(entity);
            saved.add(entity);
        }
        return saved;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        List<T> result = findWhere(Condition.eq(idField.getColumnName(), id), null, 1L, null);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public boolean existsById(ID id) {
        if (id == null) {
            return false;
        }
        return existsWhere(Condition.eq(idField.getColumnName(), id));
    }

    @Override
    public List<T> findAll() {
        return findWhere(null, null, null, null);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<Object> idList = new ArrayList<>();
        for (ID id : ids) {
            if (id != null) {
                idList.add(id);
            }
        }
        if (idList.isEmpty()) {
            return new ArrayList<>();
        }
        return findWhere(Condition.in(idField.getColumnName(), idList), null, null, null);
    }

    @Override
    public long count() {
        return countWhere(null);
    }

    @Override
    public void delete(T entity) {
        deleteByIdValue(getValue(entity, idField));
    }

    @Override
    public void deleteById(ID id) {
        deleteByIdValue(id);
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        for (ID id : ids) {
            deleteByIdValue(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        for (T entity : entities) {
            deleteByIdValue(getValue(entity, idField));
        }
    }

    @Override
    public void deleteAll() {
        // Delete row by row so relation cascades fire and foreign-key ordering is respected. Rows are
        // loaded without their relations (depth 0) since only the id is needed to drive the delete.
        for (Object entity : queryObjects("SELECT * FROM " + model.getTableName(), List.of(), 0)) {
            deleteByIdValue(getValue(entity, idField));
        }
    }

    // ------------------------------------------------------------------ query primitives (SPI)

    // These low-level primitives take a Condition tree and are the substrate the higher repository
    // layers build on (derived query methods, specifications). They are public so the query-method
    // infrastructure in the support package can reach them, but they are not part of the
    // CrudRepository interface, so an application obtaining a repository sees only the CRUD surface.

    /**
     * Core read primitive: selects entities matching {@code where} (all rows when {@code null}),
     * ordered and paged as requested, and resolves eager relations one level deep.
     *
     * @param where  the predicate, or {@code null} to match all rows
     * @param orders the ordering terms, or {@code null} for none
     * @param limit  the maximum number of rows, or {@code null} for no cap
     * @param offset the number of leading rows to skip, or {@code null} for none
     * @return the matching entities
     */
    @SuppressWarnings("unchecked")
    public List<T> findWhere(Condition where, List<Order> orders, Long limit, Long offset) {
        SqlBuilder builder = SqlBuilder.from(model.getTableName())
                .where(where)
                .orderBy(orders)
                .limit(limit)
                .offset(offset);
        return (List<T>) (List<?>) queryObjects(builder.toSelectSql(dialect), builder.parameters(), DEFAULT_DEPTH);
    }

    /**
     * Counts rows matching {@code where} (all rows when {@code null}).
     *
     * @param where the predicate, or {@code null} to count all rows
     * @return the number of matching rows
     */
    public long countWhere(Condition where) {
        SqlBuilder builder = SqlBuilder.from(model.getTableName()).where(where);
        List<Long> result = executor.executeQuery(builder.toCountSql(), builder.parameters(), rs -> rs.getLong(1));
        return result.isEmpty() ? 0L : result.get(0);
    }

    /**
     * Whether any row matches {@code where}.
     *
     * @param where the predicate, or {@code null} to test for any row at all
     * @return {@code true} if at least one row matches
     */
    public boolean existsWhere(Condition where) {
        return countWhere(where) > 0;
    }

    /**
     * Deletes every row matching {@code where}, row by row so relation cascades fire (matching the
     * semantics of {@link #deleteById}). Rows are loaded without their relations (only the id drives
     * the delete).
     *
     * @param where the predicate selecting the rows to delete, or {@code null} for all rows
     * @return the number of rows deleted
     */
    public long deleteWhere(Condition where) {
        SqlBuilder builder = SqlBuilder.from(model.getTableName()).where(where);
        List<Object> matches = queryObjects(builder.toSelectSql(dialect), builder.parameters(), 0);
        for (Object entity : matches) {
            deleteByIdValue(getValue(entity, idField));
        }
        return matches.size();
    }

    /** The parsed, dialect-independent model describing this repository's entity. */
    public EntityModel getModel() {
        return model;
    }

    /** The entity type this repository manages. */
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Resolves an entity property name (as used in a derived query method, case-insensitive) to its
     * {@link FieldModel} — a scalar column or an owning foreign-key column.
     *
     * @param propertyName the Java property/field name
     * @return the matching field model
     * @throws PolyDBException if no persistent property with that name exists
     */
    public FieldModel resolveProperty(String propertyName) {
        for (FieldModel field : model.getFields()) {
            if (field.getField().getName().equalsIgnoreCase(propertyName)) {
                return field;
            }
        }
        String known = model.getFields().stream().map(f -> f.getField().getName()).collect(Collectors.joining(", "));
        throw new PolyDBException("No persistent property '" + propertyName + "' on " + entityClass.getName()
                + " (known properties: " + known + ")");
    }

    /**
     * Converts a value bound against {@code field} in a query predicate to the form the column stores:
     * enums bind by name, foreign-key fields bind the referenced id (accepting either the associated
     * entity or the id itself), everything else passes through.
     *
     * @param field the target column
     * @param value the raw argument value
     * @return the value to bind
     */
    public Object toColumnValue(FieldModel field, Object value) {
        if (value == null) {
            return null;
        }
        if (field.isForeignKey()) {
            RelationModel relation = field.getRelation();
            if (relation.getTargetEntity().isInstance(value)) {
                JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
                FieldModel referenced = targetRepo.fieldByColumn(relation.getReferencedColumnName());
                return targetRepo.getValue(value, referenced);
            }
            return value; // assume the caller passed the id directly
        }
        return value instanceof Enum<?> e ? e.name() : value;
    }

    // ------------------------------------------------------------------ write

    private void doSave(Object entity) {
        Set<Object> inFlight = IN_FLIGHT.get();
        if (!inFlight.add(entity)) {
            return; // already being saved in this cascade chain
        }
        try {
            Object id = getValue(entity, idField);
            if (id != null && existsWhere(Condition.eq(idField.getColumnName(), id))) {
                update(entity);
                return;
            }

            insert(entity);
        } finally {
            inFlight.remove(entity);
        }
    }

    private void insert(Object entity) {
        cascadeSaveOwning(entity, false);

        List<FieldModel> columns = model.getFields().stream()
                .filter(field -> !field.isAutoIncrement())
                .collect(Collectors.toList());

        String columnList = columns.stream().map(FieldModel::getColumnName).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(field -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + model.getTableName() + " (" + columnList + ") VALUES (" + placeholders + ")";

        List<Object> values = columns.stream().map(field -> valueForColumn(entity, field)).collect(Collectors.toList());
        executor.executeUpdate(sql, values);

        cascadeSaveInverse(entity, false);
    }

    private void update(Object entity) {
        cascadeSaveOwning(entity, true);

        List<FieldModel> columns = model.getFields().stream()
                .filter(field -> !field.isId())
                .collect(Collectors.toList());

        String setClause = columns.stream()
                .map(field -> field.getColumnName() + " = ?")
                .collect(Collectors.joining(", "));
        String sql = "UPDATE " + model.getTableName() + " SET " + setClause
                + " WHERE " + idField.getColumnName() + " = ?";

        List<Object> values = columns.stream()
                .map(field -> valueForColumn(entity, field))
                .collect(Collectors.toCollection(ArrayList::new));
        values.add(getValue(entity, idField));
        executor.executeUpdate(sql, values);

        cascadeSaveInverse(entity, true);
    }

    /**
     * Resolves the value to bind for a column: a scalar field value, or for a foreign-key column the
     * id of the associated entity.
     */
    private Object valueForColumn(Object entity, FieldModel field) {
        if (!field.isForeignKey()) {
            Object value = getValue(entity, field);
            // Enums map to a string column (see the dialects' getSqlType), so bind their name rather
            // than the enum object, which JDBC drivers cannot handle directly.
            return value instanceof Enum<?> e ? e.name() : value;
        }
        RelationModel relation = field.getRelation();
        Object associated = getValue(entity, relation.getField());
        if (associated == null) {
            return null;
        }
        // Read the column the foreign key actually references (usually the target id, but a custom
        // @JoinColumn(referencedColumnName=...) may point at another column).
        JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
        FieldModel referenced = targetRepo.fieldByColumn(relation.getReferencedColumnName());
        return targetRepo.getValue(associated, referenced);
    }

    /** The scalar column with the given name, or this entity's id column when no match is found. */
    private FieldModel fieldByColumn(String columnName) {
        if (columnName != null) {
            for (FieldModel field : model.getFields()) {
                if (!field.isForeignKey() && field.getColumnName().equalsIgnoreCase(columnName)) {
                    return field;
                }
            }
        }
        return idField;
    }

    /** Saves owning associations (many-to-one / owning one-to-one) so the foreign-key target exists. */
    private void cascadeSaveOwning(Object entity, boolean updating) {
        for (RelationModel relation : model.getRelations()) {
            if (!relation.isOwningSide() || relation.getJoinColumnName() == null) {
                continue;
            }
            if (!shouldCascadeSave(relation, updating)) {
                continue;
            }
            Object associated = getValue(entity, relation.getField());
            if (associated != null) {
                repoFor(relation.getTargetEntity()).doSave(associated);
            }
        }
    }

    /** Saves inverse collections (one-to-many) and many-to-many links after the owner is written. */
    private void cascadeSaveInverse(Object entity, boolean updating) {
        Object ownerId = getValue(entity, idField);
        for (RelationModel relation : model.getRelations()) {
            switch (relation.getType()) {
                case ONE_TO_MANY -> cascadeOneToMany(entity, relation, updating);
                case MANY_TO_MANY -> {
                    if (relation.isOwningSide()) {
                        syncManyToMany(entity, ownerId, relation, updating);
                    }
                }
                case ONE_TO_ONE -> {
                    if (!relation.isOwningSide()) {
                        cascadeInverseOneToOne(entity, relation, updating);
                    }
                }
                default -> { /* owning relations handled in cascadeSaveOwning */ }
            }
        }
    }

    private void cascadeOneToMany(Object entity, RelationModel relation, boolean updating) {
        if (!shouldCascadeSave(relation, updating)) {
            return;
        }
        Collection<?> children = asCollection(getValue(entity, relation.getField()));
        if (children == null) {
            return;
        }
        JdbcRepository<?, ?> childRepo = repoFor(relation.getTargetEntity());
        RelationModel backRef = childRepo.owningRelationByField(relation.getMappedBy());
        for (Object child : children) {
            childRepo.setValue(child, backRef.getField(), entity);
            childRepo.doSave(child);
        }
    }

    private void cascadeInverseOneToOne(Object entity, RelationModel relation, boolean updating) {
        if (!shouldCascadeSave(relation, updating)) {
            return;
        }
        Object child = getValue(entity, relation.getField());
        if (child == null) {
            return;
        }
        JdbcRepository<?, ?> childRepo = repoFor(relation.getTargetEntity());
        RelationModel backRef = childRepo.owningRelationByField(relation.getMappedBy());
        childRepo.setValue(child, backRef.getField(), entity);
        childRepo.doSave(child);
    }

    private void syncManyToMany(Object entity, Object ownerId, RelationModel relation, boolean updating) {
        RelationModel.JoinTableInfo joinTable = relation.getJoinTable();
        Collection<?> targets = asCollection(getValue(entity, relation.getField()));

        if (updating) {
            executor.executeUpdate("DELETE FROM " + joinTable.getTableName()
                    + " WHERE " + joinTable.getJoinColumn() + " = ?", listOf(ownerId));
        }
        if (targets == null) {
            return;
        }
        JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
        boolean cascade = shouldCascadeSave(relation, updating);
        String insert = "INSERT INTO " + joinTable.getTableName()
                + " (" + joinTable.getJoinColumn() + ", " + joinTable.getInverseJoinColumn() + ") VALUES (?, ?)";
        for (Object target : targets) {
            if (cascade) {
                targetRepo.doSave(target);
            }
            Object targetId = targetRepo.getValue(target, targetRepo.idField);
            executor.executeUpdate(insert, List.of(ownerId, targetId));
        }
    }

    private boolean shouldCascadeSave(RelationModel relation, boolean updating) {
        return relation.cascades(CascadeType.PERSIST) || (updating && relation.cascades(CascadeType.MERGE));
    }

    // ------------------------------------------------------------------ delete cascades

    private void deleteByIdValue(Object id) {
        if (id == null) {
            return;
        }
        Set<String> deleting = DELETING.get();
        String key = entityClass.getName() + "#" + id;
        if (!deleting.add(key)) {
            return; // this row is already being deleted in the current cascade chain
        }
        try {
            cascadeRemove(id);
            String sql = "DELETE FROM " + model.getTableName() + " WHERE " + idField.getColumnName() + " = ?";
            executor.executeUpdate(sql, listOf(id));
        } finally {
            deleting.remove(key);
        }
    }

    private void cascadeRemove(Object id) {
        for (RelationModel relation : model.getRelations()) {
            switch (relation.getType()) {
                case ONE_TO_MANY -> {
                    if (relation.cascades(CascadeType.REMOVE)) {
                        deleteChildren(relation, id);
                    }
                }
                case ONE_TO_ONE -> {
                    if (!relation.isOwningSide() && relation.cascades(CascadeType.REMOVE)) {
                        deleteChildren(relation, id);
                    }
                }
                case MANY_TO_MANY -> {
                    if (relation.isOwningSide()) {
                        // Always remove join rows so the owner row can be deleted without violating
                        // the join-table foreign key.
                        executor.executeUpdate("DELETE FROM " + relation.getJoinTable().getTableName()
                                + " WHERE " + relation.getJoinTable().getJoinColumn() + " = ?", listOf(id));
                    }
                }
                default -> { /* owning many-to-one removal is not cascaded automatically */ }
            }
        }
    }

    private void deleteChildren(RelationModel relation, Object parentId) {
        JdbcRepository<?, ?> childRepo = repoFor(relation.getTargetEntity());
        RelationModel backRef = childRepo.owningRelationByField(relation.getMappedBy());
        String sql = "SELECT * FROM " + childRepo.model.getTableName()
                + " WHERE " + backRef.getJoinColumnName() + " = ?";
        for (Object child : childRepo.queryObjects(sql, listOf(parentId), 0)) {
            childRepo.deleteByIdValue(childRepo.getValue(child, childRepo.idField));
        }
    }

    // ------------------------------------------------------------------ read + relation loading

    /** Executes a query with this repository's mapper and resolves eager relations at {@code depth}. */
    private List<Object> queryObjects(String sql, List<Object> params, int depth) {
        List<Object> result = new ArrayList<>(executor.executeQuery(sql, params, mapper));
        if (depth > 0) {
            for (Object entity : result) {
                loadRelations(entity, depth);
            }
        }
        return result;
    }

    private void loadRelations(Object entity, int depth) {
        Object id = getValue(entity, idField);
        if (id == null) {
            return;
        }
        for (RelationModel relation : model.getRelations()) {
            if (relation.getFetch() != FetchType.EAGER) {
                continue; // lazy relations are not auto-populated
            }
            switch (relation.getType()) {
                case MANY_TO_ONE -> loadToOne(entity, relation, id, depth);
                case ONE_TO_ONE -> loadToOne(entity, relation, id, depth);
                case ONE_TO_MANY -> loadOneToMany(entity, relation, id, depth);
                case MANY_TO_MANY -> loadManyToMany(entity, relation, id, depth);
            }
        }
    }

    private void loadToOne(Object entity, RelationModel relation, Object id, int depth) {
        JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
        String sql = toOneSql(relation, targetRepo);
        List<Object> matches = targetRepo.queryObjects(sql, listOf(id), depth - 1);
        setValue(entity, relation.getField(), matches.isEmpty() ? null : matches.get(0));
    }

    /**
     * Builds the to-one load query: the owning side joins through its own foreign-key column, the
     * inverse side filters the target table by the owning relation's join column.
     */
    private String toOneSql(RelationModel relation, JdbcRepository<?, ?> targetRepo) {
        if (relation.isOwningSide()) {
            return "SELECT t.* FROM " + targetRepo.model.getTableName() + " t"
                    + " JOIN " + model.getTableName() + " p ON p." + relation.getJoinColumnName()
                    + " = t." + relation.getReferencedColumnName()
                    + " WHERE p." + idField.getColumnName() + " = ?";
        }

        RelationModel backRef = targetRepo.owningRelationByField(relation.getMappedBy());
        return "SELECT * FROM " + targetRepo.model.getTableName()
                + " WHERE " + backRef.getJoinColumnName() + " = ?";
    }

    private void loadOneToMany(Object entity, RelationModel relation, Object id, int depth) {
        JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
        RelationModel backRef = targetRepo.owningRelationByField(relation.getMappedBy());
        String sql = "SELECT * FROM " + targetRepo.model.getTableName()
                + " WHERE " + backRef.getJoinColumnName() + " = ?";
        List<Object> children = targetRepo.queryObjects(sql, listOf(id), depth - 1);
        setValue(entity, relation.getField(), toFieldCollection(relation.getField(), children));
    }

    private void loadManyToMany(Object entity, RelationModel relation, Object id, int depth) {
        JdbcRepository<?, ?> targetRepo = repoFor(relation.getTargetEntity());
        String targetTable = targetRepo.model.getTableName();
        String targetId = targetRepo.idField.getColumnName();

        String sql = manyToManySql(relation, targetRepo, targetTable, targetId);
        List<Object> targets = targetRepo.queryObjects(sql, listOf(id), depth - 1);
        setValue(entity, relation.getField(), toFieldCollection(relation.getField(), targets));
    }

    /**
     * Builds the many-to-many load query through the join table. The owning side reads the join
     * table directly; the inverse side borrows the owning relation's join table and swaps the join
     * and inverse-join columns.
     */
    private String manyToManySql(RelationModel relation, JdbcRepository<?, ?> targetRepo, String targetTable, String targetId) {
        if (relation.isOwningSide()) {
            RelationModel.JoinTableInfo joinTable = relation.getJoinTable();
            return "SELECT t.* FROM " + targetTable + " t"
                    + " JOIN " + joinTable.getTableName() + " j ON j." + joinTable.getInverseJoinColumn() + " = t." + targetId
                    + " WHERE j." + joinTable.getJoinColumn() + " = ?";
        }

        RelationModel owning = targetRepo.owningRelationByField(relation.getMappedBy());
        RelationModel.JoinTableInfo joinTable = owning.getJoinTable();
        return "SELECT t.* FROM " + targetTable + " t"
                + " JOIN " + joinTable.getTableName() + " j ON j." + joinTable.getJoinColumn() + " = t." + targetId
                + " WHERE j." + joinTable.getInverseJoinColumn() + " = ?";
    }

    // ------------------------------------------------------------------ helpers

    private RelationModel owningRelationByField(String fieldName) {
        for (RelationModel relation : model.getRelations()) {
            if (relation.isOwningSide() && relation.getField() != null
                    && relation.getField().getName().equals(fieldName)) {
                return relation;
            }
        }
        throw new PolyDBException("No owning relation '" + fieldName + "' found on " + entityClass.getName()
                + " (check the mappedBy reference)");
    }

    private JdbcRepository<?, ?> repoFor(Class<?> type) {
        JdbcRepository<?, ?> repo = registry.get(type);
        return repo != null ? repo : newRepository(type, dataSource, dialect, registry);
    }

    private static <X> JdbcRepository<X, Object> newRepository(Class<X> type, DataSource dataSource, Dialect dialect, Map<Class<?>, JdbcRepository<?, ?>> registry) {
        return new JdbcRepository<>(type, dataSource, dialect, registry);
    }

    private Collection<?> asCollection(Object value) {
        return value instanceof Collection<?> collection ? collection : null;
    }

    private Object toFieldCollection(Field field, List<Object> values) {
        if (Set.class.isAssignableFrom(field.getType())) {
            return new java.util.LinkedHashSet<>(values);
        }
        return new ArrayList<>(values);
    }

    private Object getValue(Object entity, FieldModel field) {
        return getValue(entity, field.getField());
    }

    private Object getValue(Object entity, Field field) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new PolyDBException("Could not read field " + field.getName(), e);
        }
    }

    private void setValue(Object entity, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new PolyDBException("Could not set field " + field.getName(), e);
        }
    }

    private static List<Object> listOf(Object value) {
        List<Object> list = new ArrayList<>(1);
        list.add(value);
        return list;
    }
}
