package dev.automata.automata.automation_engine;

import dev.automata.automata.automation_engine.grace.GraceWindowEvaluator;
import dev.automata.automata.automation_engine.helpers.ActionResolver;
import dev.automata.automata.dto.AutomationRuntimeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BUG 1 FIX — STRANDED DESCENDANT NEGATIVE ACTIONS.
 * Extracted from AutomationOrchestrator: given a C1_NEGATIVE eval result,
 * computes which additional negative actions must fire for nodes that were
 * active but not walked this tick (stranded by a parent failing higher up
 * the tree). Pure business logic — the only I/O is grace-window bookkeeping,
 * delegated to GraceWindowEvaluator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrandedActionResolver {

    private final GraceWindowEvaluator graceWindowEvaluator;
    private final ActionResolver actionResolver;

    public EvalResult resolve(EvalResult result, ExecutionPlan plan,
                              AutomationRuntimeState prevState, String automationId) {

        if (plan.getConditionTree() == null || plan.getConditionTree().isEmpty()) return result;

        Map<String, Boolean> walkedThisTick =
                result.getConditionResults() != null ? result.getConditionResults() : Map.of();

        List<ExecutionPlan.CompiledAction> existing =
                result.getActionsToFire() != null ? result.getActionsToFire() : List.of();

        List<ExecutionPlan.CompiledAction> candidates = new ArrayList<>(existing);

        Map<String, ExecutionPlan.CompiledConditionNode> nodeById = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        List<ExecutionPlan.CompiledConditionNode> strandedCandidates = new ArrayList<>();
        Map<String, Boolean> extendedResults = new LinkedHashMap<>(walkedThisTick);
        long nowMs = System.currentTimeMillis();

        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!node.isStateful()) continue;
            boolean wasActive = prevState.isNodeActive(node.getNodeId());
            boolean walkedThisNode = walkedThisTick.containsKey(node.getNodeId());
            if (wasActive && !walkedThisNode) strandedCandidates.add(node);
        }
        Set<String> strandedIds = strandedCandidates.stream()
                .map(ExecutionPlan.CompiledConditionNode::getNodeId)
                .collect(Collectors.toSet());

        Set<String> superseded = new HashSet<>();
        for (ExecutionPlan.CompiledConditionNode node : strandedCandidates) {
            if (hasStrandedDescendant(node, nodeById, strandedIds, new HashSet<>())) {
                superseded.add(node.getNodeId());
            }
        }

        for (ExecutionPlan.CompiledConditionNode node : strandedCandidates) {
            boolean walkedThisNode = walkedThisTick.containsKey(node.getNodeId());
            if (walkedThisNode) continue;

            ExecutionPlan.CompiledCondition c = node.getCondition();
            boolean hasGrace = c != null
                    && c.getDurationMinutes() > 0
                    && !"scheduled".equals(c.getConditionType());

            if (hasGrace) {
                long durationMs = c.getDurationMinutes() * 1000L;
                GraceWindowEvaluator.GraceDecision decision =
                        graceWindowEvaluator.evaluate(automationId, node.getNodeId(), durationMs, true, nowMs);
                if (decision.hold()) {
                    if (decision.justArmed()) {
                        log.info("⏳ Stranded node '{}' — parent false, honoring {}min child grace before negative actions",
                                node.getNodeId(), c.getDurationMinutes());
                    }
                    continue;
                }
            }

            extendedResults.put(node.getNodeId(), false);

            if (superseded.contains(node.getNodeId())) {
                log.debug("🧩 Stranded node '{}' superseded by a deeper stranded descendant — "
                        + "its own negative actions are skipped", node.getNodeId());
                continue;
            }

            log.debug("🧩 Stranded descendant '{}' was active but not walked this tick — "
                            + "firing its {} negative action(s)",
                    node.getNodeId(), node.getNegativeActions() != null
                            ? node.getNegativeActions().size() : 0);

            if (node.getNegativeActions() != null) {
                candidates.addAll(node.getNegativeActions());
            }
        }

        if (candidates.size() == existing.size() && extendedResults.size() == walkedThisTick.size()) return result;

        List<ExecutionPlan.CompiledAction> combined = new ArrayList<>(actionResolver.resolve(candidates));

        return result.toBuilder()
                .actionsToFire(combined)
                .conditionResults(extendedResults)
                .build();
    }

    private boolean hasStrandedDescendant(ExecutionPlan.CompiledConditionNode node,
                                          Map<String, ExecutionPlan.CompiledConditionNode> nodeById,
                                          Set<String> strandedIds,
                                          Set<String> visited) {
        if (node.getPositiveChildNodeIds() == null || !visited.add(node.getNodeId())) return false;
        for (String childId : node.getPositiveChildNodeIds()) {
            if (strandedIds.contains(childId)) return true;
            ExecutionPlan.CompiledConditionNode child = nodeById.get(childId);
            if (child != null && hasStrandedDescendant(child, nodeById, strandedIds, visited)) return true;
        }
        return false;
    }
}