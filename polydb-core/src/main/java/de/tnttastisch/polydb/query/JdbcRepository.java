package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcRepository<T> implements Repository<T> {

    private final QueryExecutor executor;
    private final ResultMapper<T> resultMapper;
    private final Dialect dialect;
    private final EntityModel model;
    private final FieldModel idField;

    public JdbcRepository(Class<T> entityClass, DataSource dataSource, Dialect dialect) {
        this.executor = new QueryExecutor(dataSource);
        this.resultMapper = new DefaultResultMapper<>(entityClass);
        this.dialect = dialect;
        this.model = new EntityParser().parseEntity(entityClass);
        this.idField = model.getFields().stream()
                .filter(FieldModel::isId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Entity has no ID field"));
    }

    @Override
    public void save(T entity) {
        Object id = getValue(entity, idField);
        if (id != null && findById(id).isPresent()) {
            update(entity);
            return;
        }
        insert(entity);
    }

    @Override
    public Optional<T> findById(Object id) {
        String sql = String.format("SELECT * FROM %s WHERE %s = ?",
                dialect.quoteIdentifier(model.getTableName()),
                dialect.quoteIdentifier(idField.getColumnName()));

        List<T> results = executor.executeQuery(sql, List.of(id), resultMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<T> findAll() {
        String sql = String.format("SELECT * FROM %s", dialect.quoteIdentifier(model.getTableName()));
        return executor.executeQuery(sql, List.of(), resultMapper);
    }

    @Override
    public void delete(T entity) {
        Object id = getValue(entity, idField);
        if (id == null) return;
        deleteById(id);
    }

    @Override
    public void deleteById(Object id) {
        String sql = String.format("DELETE FROM %s WHERE %s = ?",
                dialect.quoteIdentifier(model.getTableName()),
                dialect.quoteIdentifier(idField.getColumnName()));
        executor.executeUpdate(sql, List.of(id));
    }

    private void insert(T entity) {
        List<FieldModel> fields = model.getFields().stream()
                .filter(f -> !f.isAutoIncrement())
                .collect(Collectors.toList());

        String columns = fields.stream()
                .map(f -> dialect.quoteIdentifier(f.getColumnName()))
                .collect(Collectors.joining(", "));

        String placeholders = fields.stream()
                .map(f -> "?")
                .collect(Collectors.joining(", "));

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                dialect.quoteIdentifier(model.getTableName()),
                columns, placeholders);

        List<Object> values = fields.stream()
                .map(f -> getValue(entity, f))
                .collect(Collectors.toList());

        executor.executeUpdate(sql, values);
    }

    private void update(T entity) {
        List<FieldModel> fields = model.getFields().stream()
                .filter(f -> !f.isId())
                .collect(Collectors.toList());

        String setClause = fields.stream()
                .map(f -> dialect.quoteIdentifier(f.getColumnName()) + " = ?")
                .collect(Collectors.joining(", "));

        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                dialect.quoteIdentifier(model.getTableName()),
                setClause,
                dialect.quoteIdentifier(idField.getColumnName()));

        List<Object> values = fields.stream()
                .map(f -> getValue(entity, f))
                .collect(Collectors.toCollection(ArrayList::new));
        values.add(getValue(entity, idField));

        executor.executeUpdate(sql, values);
    }

    private Object getValue(T entity, FieldModel fieldModel) {
        try {
            fieldModel.getField().setAccessible(true);
            return fieldModel.getField().get(entity);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access field", e);
        }
    }
}
