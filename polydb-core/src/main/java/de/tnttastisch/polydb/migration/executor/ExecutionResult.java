package de.tnttastisch.polydb.migration.executor;

import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.plan.ExecutionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of running a {@link de.tnttastisch.polydb.migration.plan.MigrationPlan} through the
 * {@link MigrationExecutor}: whether it was applied, skipped by a precondition, or merely rendered
 * (dry-run / preview), together with the operations involved and the generated {@link SqlScript}.
 */
public final class ExecutionResult {

    private final ExecutionMode mode;
    private final boolean applied;
    private final boolean skipped;
    private final String skipReason;
    private final List<MigrationOperation> operations;
    private final SqlScript script;

    private ExecutionResult(ExecutionMode mode, boolean applied, boolean skipped, String skipReason,
                            List<MigrationOperation> operations, SqlScript script) {
        this.mode = mode;
        this.applied = applied;
        this.skipped = skipped;
        this.skipReason = skipReason;
        this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
        this.script = script;
    }

    /** The plan was skipped because a precondition was not met. */
    public static ExecutionResult skipped(String reason) {
        return new ExecutionResult(null, false, true, reason, List.of(), new SqlScript(List.of()));
    }

    /** The plan's SQL was rendered without executing (dry-run / preview). */
    public static ExecutionResult rendered(ExecutionMode mode, List<MigrationOperation> operations, SqlScript script) {
        return new ExecutionResult(mode, false, false, null, operations, script);
    }

    /** The plan was executed against the database. */
    public static ExecutionResult executed(List<MigrationOperation> operations, SqlScript script) {
        return new ExecutionResult(ExecutionMode.EXECUTE, true, false, null, operations, script);
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public boolean isApplied() {
        return applied;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public List<MigrationOperation> getOperations() {
        return operations;
    }

    public SqlScript getScript() {
        return script;
    }

    /** Convenience: the rendered SQL text of this result. */
    public String getSql() {
        return script.toSql();
    }
}
