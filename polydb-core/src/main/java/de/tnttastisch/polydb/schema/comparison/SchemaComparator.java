package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.dialect.AbstractSqlDialect;
import de.tnttastisch.polydb.schema.db.ColumnSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.TableSchema;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;
import de.tnttastisch.polydb.schema.model.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchemaComparator {

    private static final Logger log = LoggerFactory.getLogger(SchemaComparator.class);

    public List<SchemaChange> compare(List<EntityModel> entities, DatabaseSchema dbSchema) {
        Map<String, EntityModel> entityByClass = new HashMap<>();
        for (EntityModel entity : entities) {
            entityByClass.put(entity.getClassName(), entity);
        }

        // Newly required tables (entities + synthesised many-to-many join tables).
        List<EntityModel> toCreate = new ArrayList<>();
        List<SchemaChange> columnChanges = new ArrayList<>();
        List<SchemaChange> existingTableForeignKeys = new ArrayList<>();

        for (EntityModel entity : entities) {
            TableSchema dbTable = dbSchema.getTable(entity.getTableName());
            if (dbTable == null) {
                toCreate.add(entity);
            } else {
                compareColumns(entity, dbTable, columnChanges);
                compareForeignKeys(entity, dbTable, existingTableForeignKeys);
            }
        }

        for (EntityModel joinTable : buildJoinTables(entities, entityByClass)) {
            if (dbSchema.getTable(joinTable.getTableName()) == null) {
                toCreate.add(joinTable);
            }
        }

        return assemble(toCreate, columnChanges, existingTableForeignKeys, dbSchema);
    }

    private List<SchemaChange> assemble(List<EntityModel> toCreate,
                                        List<SchemaChange> columnChanges,
                                        List<SchemaChange> existingTableForeignKeys,
                                        DatabaseSchema dbSchema) {
        List<EntityModel> ordered = topologicalOrder(toCreate);

        // Tables that exist by the time a given CREATE runs: pre-existing DB tables plus those
        // created earlier in this ordering.
        Set<String> available = new HashSet<>(dbSchema.getTables().keySet());

        List<SchemaChange> creates = new ArrayList<>();
        List<SchemaChange> deferredForeignKeys = new ArrayList<>();

        for (EntityModel entity : ordered) {
            String table = entity.getTableName().toLowerCase();
            List<RelationModel> inline = new ArrayList<>();

            for (RelationModel relation : owningColumnRelations(entity)) {
                String refTable = relation.getReferencedTable() == null ? null : relation.getReferencedTable().toLowerCase();
                boolean referenceReady = refTable != null && (available.contains(refTable) || refTable.equals(table));
                if (referenceReady) {
                    inline.add(relation);
                } else {
                    // Cyclic / forward reference: emit the foreign key after all tables exist.
                    deferredForeignKeys.add(toAddForeignKey(entity.getTableName(), relation));
                    log.debug("Deferring foreign key on {}.{} -> {} to ALTER (dependency not yet created)",
                            entity.getTableName(), relation.getJoinColumnName(), relation.getReferencedTable());
                }
            }

            creates.add(new SchemaChange.CreateTable(entity, inline));
            available.add(table);
        }

        List<SchemaChange> result = new ArrayList<>();
        result.addAll(creates);
        result.addAll(columnChanges);
        result.addAll(deferredForeignKeys);
        result.addAll(existingTableForeignKeys);
        return result;
    }

    private void compareColumns(EntityModel entity, TableSchema dbTable, List<SchemaChange> changes) {
        for (FieldModel field : entity.getFields()) {
            ColumnSchema dbColumn = dbTable.getColumns().get(field.getColumnName().toLowerCase());
            if (dbColumn == null) {
                changes.add(new SchemaChange.AddColumn(entity.getTableName(), field));
            }
        }
    }

    private void compareForeignKeys(EntityModel entity, TableSchema dbTable, List<SchemaChange> changes) {
        for (RelationModel relation : owningColumnRelations(entity)) {
            if (!dbTable.hasForeignKeyOn(relation.getJoinColumnName())) {
                changes.add(toAddForeignKey(entity.getTableName(), relation));
            }
        }
    }

    private SchemaChange.AddForeignKey toAddForeignKey(String tableName, RelationModel relation) {
        String constraintName = AbstractSqlDialect.foreignKeyConstraintName(tableName, relation.getJoinColumnName());
        return new SchemaChange.AddForeignKey(tableName, constraintName, relation.getJoinColumnName(),
                relation.getReferencedTable(), relation.getReferencedColumnName());
    }

    /**
     * Owning relations backed by a single foreign-key column on this table (many-to-one and owning
     * one-to-one, plus the synthesised join-table columns).
     */
    private List<RelationModel> owningColumnRelations(EntityModel entity) {
        List<RelationModel> result = new ArrayList<>();
        for (RelationModel relation : entity.getRelations()) {
            if (relation.isOwningSide() && relation.getJoinColumnName() != null) {
                result.add(relation);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ many-to-many join tables

    private List<EntityModel> buildJoinTables(List<EntityModel> entities, Map<String, EntityModel> entityByClass) {
        List<EntityModel> joinTables = new ArrayList<>();
        for (EntityModel entity : entities) {
            FieldModel ownerId = idFieldOf(entity);
            if (ownerId == null) {
                continue;
            }
            for (RelationModel relation : entity.getRelations()) {
                if (relation.getType() != RelationType.MANY_TO_MANY || !relation.isOwningSide()) {
                    continue;
                }
                RelationModel.JoinTableInfo info = relation.getJoinTable();
                EntityModel targetEntity = entityByClass.get(relation.getTargetEntity().getName());
                FieldModel targetId = targetEntity == null ? null : idFieldOf(targetEntity);
                if (info == null || targetEntity == null || targetId == null) {
                    log.warn("Cannot build join table for {}.{}: target entity metadata unavailable",
                            entity.getTableName(), relation.getJoinColumnName());
                    continue;
                }
                joinTables.add(buildJoinTable(entity, ownerId, targetEntity, targetId, info));
            }
        }
        return joinTables;
    }

    private EntityModel buildJoinTable(EntityModel owner, FieldModel ownerId,
                                       EntityModel target, FieldModel targetId,
                                       RelationModel.JoinTableInfo info) {
        EntityModel joinTable = new EntityModel(info.getTableName(), info.getTableName());

        FieldModel ownerColumn = new FieldModel(null, info.getJoinColumn(), ownerId.getType(),
                true, false, false, false, 255, 0, 0);
        FieldModel targetColumn = new FieldModel(null, info.getInverseJoinColumn(), targetId.getType(),
                true, false, false, false, 255, 0, 0);
        joinTable.addField(ownerColumn);
        joinTable.addField(targetColumn);

        RelationModel ownerFk = RelationModel.builder(RelationType.MANY_TO_ONE, null, Object.class)
                .owningSide(true)
                .joinColumnName(info.getJoinColumn())
                .referencedColumnName(ownerId.getColumnName())
                .referencedTable(owner.getTableName())
                .build();
        RelationModel targetFk = RelationModel.builder(RelationType.MANY_TO_ONE, null, Object.class)
                .owningSide(true)
                .joinColumnName(info.getInverseJoinColumn())
                .referencedColumnName(targetId.getColumnName())
                .referencedTable(target.getTableName())
                .build();
        joinTable.addRelation(ownerFk);
        joinTable.addRelation(targetFk);
        return joinTable;
    }

    private FieldModel idFieldOf(EntityModel entity) {
        for (FieldModel field : entity.getFields()) {
            if (field.isId()) {
                return field;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ topological ordering

    /**
     * Orders new tables so that a referenced table is created before the table referencing it.
     * Tables involved in a dependency cycle keep their original relative order; the foreign keys
     * that would close the cycle are detected in {@link #assemble} and emitted via {@code ALTER}.
     */
    private List<EntityModel> topologicalOrder(List<EntityModel> toCreate) {
        Map<String, EntityModel> byTable = new LinkedHashMap<>();
        for (EntityModel entity : toCreate) {
            byTable.put(entity.getTableName().toLowerCase(), entity);
        }

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String table : byTable.keySet()) {
            inDegree.put(table, 0);
        }

        for (EntityModel entity : toCreate) {
            String table = entity.getTableName().toLowerCase();
            Set<String> deps = new HashSet<>();
            for (RelationModel relation : owningColumnRelations(entity)) {
                String refTable = relation.getReferencedTable() == null ? null : relation.getReferencedTable().toLowerCase();
                if (refTable != null && !refTable.equals(table) && byTable.containsKey(refTable)) {
                    deps.add(refTable);
                }
            }
            for (String dep : deps) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(table);
                inDegree.merge(table, 1, Integer::sum);
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<EntityModel> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        drain(ready, ordered, placed, dependents, inDegree, byTable);

        // Break cycles deterministically: force-place a table that is genuinely part of a cycle
        // (not merely a dependent stuck behind one), then resume draining. This keeps acyclic tables
        // ordered after their dependencies so only the true cycle-closing foreign key is deferred.
        while (placed.size() < byTable.size()) {
            String forced = pickCycleMember(toCreate, dependents, placed);
            ready.add(forced);
            drain(ready, ordered, placed, dependents, inDegree, byTable);
        }

        return ordered;
    }

    private void drain(Deque<String> ready, List<EntityModel> ordered, Set<String> placed,
                       Map<String, List<String>> dependents, Map<String, Integer> inDegree,
                       Map<String, EntityModel> byTable) {
        while (!ready.isEmpty()) {
            String table = ready.poll();
            if (!placed.add(table)) {
                continue;
            }
            ordered.add(byTable.get(table));
            for (String dependent : dependents.getOrDefault(table, List.of())) {
                if (inDegree.merge(dependent, -1, Integer::sum) <= 0 && !placed.contains(dependent)) {
                    ready.add(dependent);
                }
            }
        }
    }

    /**
     * Returns the first unplaced table (in declared order) that lies on a dependency cycle; falls
     * back to the first unplaced table if none is detected (defensive — should not happen when stuck).
     */
    private String pickCycleMember(List<EntityModel> toCreate, Map<String, List<String>> dependents, Set<String> placed) {
        String firstUnplaced = null;
        for (EntityModel entity : toCreate) {
            String table = entity.getTableName().toLowerCase();
            if (placed.contains(table)) {
                continue;
            }
            if (firstUnplaced == null) {
                firstUnplaced = table;
            }
            if (onCycle(table, dependents, placed)) {
                return table;
            }
        }
        return firstUnplaced;
    }

    private boolean onCycle(String start, Map<String, List<String>> dependents, Set<String> placed) {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (String next : dependents.getOrDefault(start, List.of())) {
            if (!placed.contains(next)) {
                stack.push(next);
            }
        }
        while (!stack.isEmpty()) {
            String node = stack.pop();
            if (node.equals(start)) {
                return true;
            }
            if (!visited.add(node)) {
                continue;
            }
            for (String next : dependents.getOrDefault(node, List.of())) {
                if (!placed.contains(next)) {
                    stack.push(next);
                }
            }
        }
        return false;
    }
}
