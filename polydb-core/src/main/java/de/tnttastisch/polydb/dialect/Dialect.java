package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.util.List;

public interface Dialect {

    String getName();

    String getSqlType(FieldModel field);

    String getCreateTableSql(String tableName, List<FieldModel> fields);

    String getAddColumnSql(String tableName, FieldModel field);

    String getModifyColumnSql(String tableName, FieldModel field);

    String getDropColumnSql(String tableName, String columnName);

    String getCreateIndexSql(String tableName, IndexModel index);

    String getDropIndexSql(String tableName, String indexName);

    String getDropTableSql(String tableName);

    String quoteIdentifier(String identifier);
}
