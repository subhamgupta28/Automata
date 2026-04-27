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
import java.util.Map;

/**
 * One log entry per A/B evaluation tick.
 * Stored separately from AutomationLog to avoid polluting the main log.
 * Retained for the lifetime of the test plus 7 days after ending.
 */
@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_ab_test_logs")
@CompoundIndex(name = "ab_test_tick_idx", def = "{ 'testId': 1, 'timestamp': -1 }")
public class AutomationAbTestLog {

    @Id
    private String id;

    @Indexed
    private String testId;

    private Date timestamp;
    private Map<String, Object> payload;

    /**
     * Did the two variants agree on the root result?
     */
    private boolean agreed;

    private VariantResult variantA;
    private VariantResult variantB;

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantResult {
        private String variantId;
        private boolean rootTrue;
        private String rootNodeId;
        private List<AutomationLog.ConditionResult> conditionResults;

        // For branch automations: which branch would have fired
        private String winningBranchDescription;
        private boolean actionsExecuted;   // always true for A, always false for B
    }
}