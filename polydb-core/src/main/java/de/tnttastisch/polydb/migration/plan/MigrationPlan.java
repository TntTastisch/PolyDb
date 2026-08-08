package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.precondition.Precondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of {@link MigrationOperation}s plus the {@link Precondition}s that gate them — the
 * single unit the migration executor consumes, whether it came from the automatic schema comparator or
 * a manual {@code MigrationBuilder}. Immutable once built.
 */
public final class MigrationPlan {

    private final List<MigrationOperation> operations;
    private final List<Precondition> preconditions;

    public MigrationPlan(List<MigrationOperation> operations, List<Precondition> preconditions) {
        this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
        this.preconditions = Collections.unmodifiableList(new ArrayList<>(preconditions));
    }

    public MigrationPlan(List<MigrationOperation> operations) {
        this(operations, Collections.emptyList());
    }

    public List<MigrationOperation> getOperations() {
        return operations;
    }

    public List<Precondition> getPreconditions() {
        return preconditions;
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public int size() {
        return operations.size();
    }

    /** A numbered, human-readable listing of the operations, e.g. for logging what a migration will do. */
    public String describe() {
        if (operations.isEmpty()) {
            return "(no operations)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operations.size(); i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(i + 1).append(". ").append(operations.get(i).describe());
        }
        return sb.toString();
    }
}
