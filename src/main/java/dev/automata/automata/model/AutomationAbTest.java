package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * An A/B test pairs a currently-live automation (variant A) with a
 * candidate automation (variant B). On every evaluation tick, BOTH
 * variants are evaluated against the same incoming payload. Variant A
 * executes actions normally. Variant B is evaluated but its actions
 * are NEVER executed — only the result is logged for comparison.
 * <p>
 * This lets you tune thresholds, reorder branches, or restructure
 * conditions in production without affecting real devices until you
 * are confident the new logic behaves correctly.
 */
@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_ab_tests")
public class AutomationAbTest {

    @Id
    private String id;

    private String name;
    private String description;

    /**
     * The live automation — executes actions normally.
     */
    @Indexed
    private String variantAId;

    /**
     * The candidate automation — evaluated but actions suppressed.
     * This is a full Automation stored in the automations collection
     * with isEnabled=false so it never fires independently.
     */
    @Indexed
    private String variantBId;

    @Builder.Default
    private AbTestStatus status = AbTestStatus.RUNNING;

    private Date startedAt;
    private Date endedAt;
    private String startedBy;

    /**
     * Conclusion written by the user when they end the test.
     */
    private String conclusion;

    /**
     * Which variant won — set when test is ended.
     */
    private String winnerVariant;  // "A" | "B" | null

    // ── Status ────────────────────────────────────────────────────────────────

    public enum AbTestStatus {
        RUNNING,    // both variants evaluated on every tick
        PAUSED,     // evaluation suspended, no new log entries
        ENDED       // test concluded, results preserved
    }

    // ── Summary statistics (updated periodically, not per-tick) ──────────────

    private AbTestStats stats;

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AbTestStats {
        private long totalEvaluations;

        // How many times each variant's root resolved TRUE
        private long variantATriggerCount;
        private long variantBTriggerCount;

        // Agreement rate — both variants gave the same root result
        private double agreementRate;       // 0.0 – 1.0

        // Divergence examples — up to 10 recent ticks where A ≠ B
        private List<DivergenceExample> recentDivergences;

        private Date lastComputedAt;
    }

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DivergenceExample {
        private Date timestamp;
        private boolean variantAResult;
        private boolean variantBResult;
        private Map<String, Object> payload;    // the sensor data that caused divergence
        private String variantARoot;            // winning node ID in A
        private String variantBRoot;            // winning node ID in B
    }
}