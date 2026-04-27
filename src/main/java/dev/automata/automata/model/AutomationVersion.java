package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

/**
 * Stores a full snapshot of an Automation every time it is saved.
 * <p>
 * Design decisions:
 * - We store the full Automation object (not a diff) so rollback is a
 * simple replace — no patching logic required.
 * - Version numbers are per-automation, not global.
 * - The AutomationDetail (node positions) is also snapshotted so the
 * editor can restore the exact visual layout on rollback.
 * - We keep the last 20 versions per automation (enforced on save).
 */
@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_versions")
@CompoundIndex(name = "automation_version_idx",
        def = "{ 'automationId': 1, 'version': -1 }")
public class AutomationVersion {

    @Id
    private String id;

    @Indexed
    private String automationId;

    /**
     * Monotonically increasing per-automation version number.
     */
    private int version;

    /**
     * Who saved this version (username or "system").
     */
    private String savedBy;

    /**
     * Optional human-readable note — user can annotate what changed.
     */
    private String changeNote;

    private Date savedAt;

    /**
     * Full snapshot of the Automation document at this version.
     */
    private Automation automationSnapshot;

    /**
     * Full snapshot of the AutomationDetail (node graph + positions).
     */
    private AutomationDetail detailSnapshot;

    /**
     * Summary of what changed compared to the previous version.
     * Computed at save time — never re-computed on read.
     */
    private VersionDiff diff;

    // ── Diff model ────────────────────────────────────────────────────────────

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionDiff {
        private List<String> addedNodeIds;
        private List<String> removedNodeIds;
        private List<ChangedNode> changedNodes;
        private List<String> changedFields;    // top-level fields: name, isEnabled, etc.
    }

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangedNode {
        private String nodeId;
        private String nodeType;               // "condition" | "action" | "operator" | "trigger"
        private List<String> changedFields;    // e.g. ["value", "triggerKey"]
        private String before;                 // JSON of old value (abbreviated)
        private String after;                  // JSON of new value (abbreviated)
    }
}