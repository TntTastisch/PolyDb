package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.core.annotations.CreatedBy;
import de.tnttastisch.polydb.core.annotations.CreatedDate;
import de.tnttastisch.polydb.core.annotations.LastModifiedBy;
import de.tnttastisch.polydb.core.annotations.LastModifiedDate;
import de.tnttastisch.polydb.core.annotations.SoftDelete;
import de.tnttastisch.polydb.core.annotations.Version;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects and caches an entity's lifecycle fields — auditing ({@code @CreatedDate}/
 * {@code @LastModifiedDate}/{@code @CreatedBy}/{@code @LastModifiedBy}), the {@code @Version} field and
 * the {@code @SoftDelete} flag — resolving the version and soft-delete columns against the entity
 * model. Built once per entity class; {@link JdbcRepository} consults it on every write and read.
 */
final class LifecycleMetadata {

    private static final Map<Class<?>, LifecycleMetadata> CACHE = new ConcurrentHashMap<>();

    final Field createdDate;
    final Field lastModifiedDate;
    final Field createdBy;
    final Field lastModifiedBy;
    final Field version;
    final Field softDelete;
    final String versionColumn;
    final String softDeleteColumn;

    private LifecycleMetadata(Field createdDate, Field lastModifiedDate, Field createdBy, Field lastModifiedBy,
                             Field version, String versionColumn, Field softDelete, String softDeleteColumn) {
        this.createdDate = createdDate;
        this.lastModifiedDate = lastModifiedDate;
        this.createdBy = createdBy;
        this.lastModifiedBy = lastModifiedBy;
        this.version = version;
        this.versionColumn = versionColumn;
        this.softDelete = softDelete;
        this.softDeleteColumn = softDeleteColumn;
    }

    static LifecycleMetadata of(Class<?> entityClass, EntityModel model) {
        return CACHE.computeIfAbsent(entityClass, key -> build(key, model));
    }

    boolean hasAuditing() {
        return createdDate != null || lastModifiedDate != null || createdBy != null || lastModifiedBy != null;
    }

    boolean hasVersion() {
        return version != null;
    }

    boolean hasSoftDelete() {
        return softDelete != null;
    }

    private static LifecycleMetadata build(Class<?> entityClass, EntityModel model) {
        Field createdDate = null;
        Field lastModifiedDate = null;
        Field createdBy = null;
        Field lastModifiedBy = null;
        Field version = null;
        Field softDelete = null;

        for (Class<?> type = entityClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (createdDate == null && field.isAnnotationPresent(CreatedDate.class)) createdDate = field;
                if (lastModifiedDate == null && field.isAnnotationPresent(LastModifiedDate.class)) lastModifiedDate = field;
                if (createdBy == null && field.isAnnotationPresent(CreatedBy.class)) createdBy = field;
                if (lastModifiedBy == null && field.isAnnotationPresent(LastModifiedBy.class)) lastModifiedBy = field;
                if (version == null && field.isAnnotationPresent(Version.class)) version = field;
                if (softDelete == null && field.isAnnotationPresent(SoftDelete.class)) softDelete = field;
            }
        }

        return new LifecycleMetadata(createdDate, lastModifiedDate, createdBy, lastModifiedBy,
                version, columnFor(model, version), softDelete, columnFor(model, softDelete));
    }

    private static String columnFor(EntityModel model, Field field) {
        if (field == null) {
            return null;
        }
        for (FieldModel fieldModel : model.getFields()) {
            if (fieldModel.getField().equals(field)) {
                return fieldModel.getColumnName();
            }
        }
        throw new PolyDBException("Lifecycle field '" + field.getName() + "' is not mapped to a column");
    }
}
