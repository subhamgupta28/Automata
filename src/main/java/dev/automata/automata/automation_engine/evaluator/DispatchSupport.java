package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Moved out of AutomationOrchestrator — verbatim logic, now shared by OutcomeHandlers.
 */
@Slf4j
public final class DispatchSupport {
    private DispatchSupport() {
    }

    public static void armDurationWindows(EvalResult result, DispatchContext ctx) {
        Set<String> toArm = result.getIntervalNodesToArm();
        if (toArm == null || toArm.isEmpty()) return;

        ExecutionPlan plan = ctx.planCache().get(ctx.automationId());
        if (plan == null || plan.getConditionTree() == null) return;

        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        for (String nodeId : toArm) {
            ExecutionPlan.CompiledConditionNode node = nodeMap.get(nodeId);
            if (node == null || node.getCondition() == null) continue;
            long durationTtl = node.getCondition().getDurationMinutes() * 60L;
            if (durationTtl <= 0) continue;
            ctx.stateStore().setRunningKey(ctx.automationId(), nodeId, durationTtl);
            log.info("⏱️ [{}] Duration window armed for '{}': {}min",
                    ctx.automationId(), nodeId, node.getCondition().getDurationMinutes());
        }
    }

    public static void armResendThrottles(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        if (plan.getConditionTree() == null || result.getConditionResults() == null) return;
        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!Boolean.TRUE.equals(result.getConditionResults().get(node.getNodeId()))) continue;
            if (node.hasMemoryPolicy()) continue;
            if (node.getMinResendIntervalSeconds() <= 0) continue;
            if (node.getPositiveActions() == null || node.getPositiveActions().isEmpty()) continue;
            ctx.stateStore().setThrottleKey(ctx.automationId(), node.getNodeId(), node.getMinResendIntervalSeconds());
        }
    }

    /**
     * RULE B, verbatim: first writer per device+key wins, conflicts logged.
     */
    public static List<ExecutionPlan.CompiledAction> resolveFinalActions(List<ExecutionPlan.CompiledAction> actions) {
        if (actions == null || actions.isEmpty()) return actions == null ? List.of() : actions;
        if (actions.size() == 1) return actions;

        Map<String, ExecutionPlan.CompiledAction> byDeviceKey = new LinkedHashMap<>();
        for (ExecutionPlan.CompiledAction a : actions) {
            String dedupeKey = a.getDeviceId() + "|" + a.getKey();
            ExecutionPlan.CompiledAction prior = byDeviceKey.putIfAbsent(dedupeKey, a);
            if (prior != null && !Objects.equals(prior.getData(), a.getData())) {
                log.warn("⚠️ Global action resolve: conflicting values for device '{}' key '{}' — "
                                + "keeping {}='{}' (node '{}'), dropping {}='{}' (node '{}')",
                        a.getDeviceId(), a.getKey(),
                        a.getKey(), prior.getData(), prior.getNodeId(),
                        a.getKey(), a.getData(), a.getNodeId());
            }
        }
        return new ArrayList<>(byDeviceKey.values());
    }
}
