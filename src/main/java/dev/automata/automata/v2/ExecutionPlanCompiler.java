package dev.automata.automata.v2;

import dev.automata.automata.dto.NodeRef;
import dev.automata.automata.model.Automation;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compiles a raw Automation (node graph with handle strings) into an
 * ExecutionPlan (flat, typed, pre-sorted).
 * <p>
 * Called ONCE at save time from AutomationService.saveAutomationDetailInternal().
 * Never called at evaluation time.
 * <p>
 * All handle-string parsing, previousNodeRef traversal, and gate/chained
 * condition classification happens here — not in the evaluator hot path.
 * <p>
 * Key fixes vs previous version:
 * - Compiles topLevelPositiveActions and topLevelNegativeActions for
 * no-branch automations (simple trigger→action without operators).
 * Previously these were silently dropped.
 * - Separates c1NegativeActions (trigger-node refs) from informationalActions
 * and fallbackActions cleanly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionPlanCompiler {

    private final MainService mainService;

    public ExecutionPlan compile(Automation automation) {
        log.info("🔧 Compiling execution plan for '{}'", automation.getName());

        List<Automation.Condition> conditions =
                automation.getConditions() == null ? List.of() : automation.getConditions();
        List<Automation.Operator> operators =
                automation.getOperators() == null ? List.of() : automation.getOperators();
        List<Automation.Action> actions =
                automation.getActions() == null ? List.of() : automation.getActions();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId).collect(Collectors.toSet());
        Set<String> conditionNodeIds = conditions.stream()
                .map(Automation.Condition::getNodeId).collect(Collectors.toSet());

        // ── Classify conditions ───────────────────────────────────────────
        List<Automation.Condition> rootTriggers = new ArrayList<>();
        List<Automation.Condition> chainedTriggers = new ArrayList<>();
        List<Automation.Condition> gateConditions = new ArrayList<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            if (isGate(c, operatorIds)) gateConditions.add(c);
            else if (isChained(c, conditionNodeIds, operatorIds)) chainedTriggers.add(c);
            else rootTriggers.add(c);
        }

        // ── Trigger conditions (topologically sorted) ─────────────────────
        List<ExecutionPlan.CompiledCondition> triggerConditions = new ArrayList<>();
        for (Automation.Condition c : rootTriggers)
            triggerConditions.add(compileCondition(c, null, false));
        for (Automation.Condition c : topoSortChained(chainedTriggers, conditionNodeIds)) {
            String parentId = c.getPreviousNodeRef().stream()
                    .map(r -> r.getNodeId())
                    .filter(id -> conditionNodeIds.contains(id) && !operatorIds.contains(id))
                    .findFirst().orElse(null);
            triggerConditions.add(compileCondition(c, parentId, true));
        }

        // ── Gate branches ─────────────────────────────────────────────────
        List<ExecutionPlan.CompiledBranch> branches =
                buildBranches(gateConditions, operators, operatorIds, actions);

        // ── Collect node ID sets for action routing ───────────────────────
        Set<String> gateNodeIds = gateConditions.stream()
                .map(Automation.Condition::getNodeId).collect(Collectors.toSet());
        Set<String> triggerNodeIds = new HashSet<>();
        rootTriggers.forEach(c -> triggerNodeIds.add(c.getNodeId()));
        chainedTriggers.forEach(c -> triggerNodeIds.add(c.getNodeId()));

        // ── No-branch top-level actions ───────────────────────────────────
        // These are conditionGroup="positive"/"negative" actions whose
        // previousNodeRef points to a trigger condition node (not a gate node).
        // They are used by the no-branch path in AutomationEvaluator.
        List<ExecutionPlan.CompiledAction> topLevelPositive =
                compileTopLevelActions(actions, triggerNodeIds, "positive");
        List<ExecutionPlan.CompiledAction> topLevelNegative =
                compileTopLevelActions(actions, triggerNodeIds, "negative");

        // ── c1-negative: negative actions referencing trigger nodes ────────
        // Fires when c1 turns false and a branch was active (branch automations).
        // Same set as topLevelNegative for no-branch automations, but kept
        // separate to avoid confusion: c1NegativeActions are for branch reverting,
        // topLevelNegativeActions are for the ACTIVE→false state machine.
        Set<String> rootTriggerNodeIds = rootTriggers.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());
        List<ExecutionPlan.CompiledAction> c1Negative =
                deduplicateActions(compileC1NegativeActions(actions, rootTriggerNodeIds));

        // ── Other global groups ───────────────────────────────────────────
        List<ExecutionPlan.CompiledAction> informational = compileByGroup(actions, "informational");
        List<ExecutionPlan.CompiledAction> fallback = compileByGroup(actions, "fallback");
        List<ExecutionPlan.CompiledAction> stateless = compileStatelessActions(actions);

        ExecutionPlan plan = ExecutionPlan.builder()
                .automationId(automation.getId())
                .automationName(automation.getName())
                .triggerDeviceId(automation.getTrigger().getDeviceId())
                .schemaVersion(ExecutionPlan.CURRENT_SCHEMA_VERSION)
                .compiledAt(new Date())
                .triggerConditions(triggerConditions)
                .branches(branches)
                .topLevelPositiveActions(topLevelPositive)
                .topLevelNegativeActions(topLevelNegative)
                .c1NegativeActions(c1Negative)
                .informationalActions(informational)
                .fallbackActions(fallback)
                .statelessActions(stateless)
                .build();

        log.info("✅ Plan compiled for '{}': {} trigger conds, {} branches, {} top-positive, {} top-negative, {} c1-neg, {} fallback",
                automation.getName(), triggerConditions.size(), branches.size(),
                topLevelPositive.size(), topLevelNegative.size(),
                c1Negative.size(), fallback.size());

        return plan;
    }


    // ─────────────────────────────────────────────────────────────────────
    // CLASSIFICATION
    // ─────────────────────────────────────────────────────────────────────

    public boolean isGate(Automation.Condition c, Set<String> operatorIds) {
        return c.getPreviousNodeRef() != null &&
                c.getPreviousNodeRef().stream()
                        .anyMatch(r -> operatorIds.contains(r.getNodeId()));
    }

    public boolean isChained(Automation.Condition c,
                             Set<String> conditionNodeIds,
                             Set<String> operatorIds) {
        if (c.getPreviousNodeRef() == null || c.getPreviousNodeRef().isEmpty()) return false;
        return c.getPreviousNodeRef().stream()
                .anyMatch(r -> conditionNodeIds.contains(r.getNodeId())
                        && !operatorIds.contains(r.getNodeId()));
    }

    /**
     * DFS topological sort — validates that sort is correct.
     */
    private List<Automation.Condition> topoSortChained(
            List<Automation.Condition> chained, Set<String> conditionNodeIds) {
        Map<String, Automation.Condition> byId = chained.stream()
                .collect(Collectors.toMap(Automation.Condition::getNodeId, c -> c));
        List<Automation.Condition> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();  // ← Add to detect cycles

        for (Automation.Condition c : chained)
            if (!visited.contains(c.getNodeId()))
                dfsVisit(c, byId, conditionNodeIds, visited, visiting, sorted);

        // FIX: Validate that all parents appear before children in sorted list
        Map<String, Integer> sortIndex = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            sortIndex.put(sorted.get(i).getNodeId(), i);
        }

        for (Automation.Condition c : sorted) {
            if (c.getPreviousNodeRef() != null) {
                c.getPreviousNodeRef().stream()
                        .map(r -> r.getNodeId())
                        .filter(id -> byId.containsKey(id))
                        .forEach(parentId -> {
                            Integer parentIdx = sortIndex.get(parentId);
                            Integer childIdx = sortIndex.get(c.getNodeId());
                            if (parentIdx == null || childIdx == null || parentIdx > childIdx) {
                                throw new IllegalStateException(
                                        "Topological sort failed: parent '" + parentId +
                                                "' (index " + parentIdx + ") must come before child '" +
                                                c.getNodeId() + "' (index " + childIdx + ")");
                            }
                        });
            }
        }

        return sorted;
    }

    private void dfsVisit(Automation.Condition c,
                          Map<String, Automation.Condition> byId,
                          Set<String> conditionNodeIds,
                          Set<String> visited,
                          Set<String> visiting,  // ← Add
                          List<Automation.Condition> sorted) {
        if (visiting.contains(c.getNodeId())) {
            throw new IllegalStateException(
                    "Circular dependency detected in chained conditions: " + c.getNodeId());
        }
        if (visited.contains(c.getNodeId())) {
            return;
        }

        visiting.add(c.getNodeId());

        if (c.getPreviousNodeRef() != null)
            c.getPreviousNodeRef().stream()
                    .map(NodeRef::getNodeId)
                    .filter(id -> conditionNodeIds.contains(id) && byId.containsKey(id))
                    .forEach(id -> dfsVisit(byId.get(id), byId, conditionNodeIds, visited, visiting, sorted));

        visiting.remove(c.getNodeId());
        visited.add(c.getNodeId());
        sorted.add(c);
    }

    private void dfsVisit(Automation.Condition c,
                          Map<String, Automation.Condition> byId,
                          Set<String> conditionNodeIds,
                          Set<String> visited,
                          List<Automation.Condition> sorted) {
        visited.add(c.getNodeId());
        if (c.getPreviousNodeRef() != null)
            c.getPreviousNodeRef().stream()
                    .map(NodeRef::getNodeId)
                    .filter(id -> conditionNodeIds.contains(id) && byId.containsKey(id)
                            && !visited.contains(id))
                    .forEach(id -> dfsVisit(byId.get(id), byId, conditionNodeIds, visited, sorted));
        sorted.add(c);
    }


    // ─────────────────────────────────────────────────────────────────────
    // BRANCH COMPILATION
    // ─────────────────────────────────────────────────────────────────────

    private List<ExecutionPlan.CompiledBranch> buildBranches(
            List<Automation.Condition> gateConditions,
            List<Automation.Operator> operators,
            Set<String> operatorIds,
            List<Automation.Action> actions) {

        Map<String, Automation.Operator> opById = operators.stream()
                .collect(Collectors.toMap(Automation.Operator::getNodeId, o -> o));
        List<ExecutionPlan.CompiledBranch> branches = new ArrayList<>();

        for (Automation.Condition gc : gateConditions) {
            String opNodeId = gc.getPreviousNodeRef().stream()
                    .filter(r -> operatorIds.contains(r.getNodeId()))
                    .map(r -> r.getNodeId()).findFirst().orElse(null);
            if (opNodeId == null) {
                log.warn("Gate '{}' has no parent operator", gc.getNodeId());
                continue;
            }
            Automation.Operator op = opById.get(opNodeId);
            if (op == null) continue;

            branches.add(ExecutionPlan.CompiledBranch.builder()
                    .gateNodeId(gc.getNodeId())
                    .priority(op.getPriority())
                    .gateCondition(compileCondition(gc, null, false))
                    .positiveActions(compileActionsForGate(actions, gc.getNodeId(), "positive"))
                    .negativeActions(deduplicateActions(compileActionsForGate(actions, gc.getNodeId(), "negative")))
                    .build());
        }

        branches.sort(Comparator.comparingInt(ExecutionPlan.CompiledBranch::getPriority).reversed());
        return branches;
    }

    /**
     * Actions referencing a specific gate node with the given conditionGroup.
     */
    private List<ExecutionPlan.CompiledAction> compileActionsForGate(
            List<Automation.Action> actions, String gateNodeId, String group) {
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream()
                        .anyMatch(r -> r.getNodeId().equals(gateNodeId)))
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId)) // stable tiebreak
                .map(this::compileAction)
                .collect(Collectors.toList());
    }

    /**
     * Top-level positive or negative actions for no-branch automations.
     * These reference trigger condition nodes (not gate nodes).
     * <p>
     * Fix: deduplicated by (deviceId,key,data) — same as gate action compilation.
     * Fix: stable secondary sort by nodeId so equal-order actions always execute
     * in a deterministic order regardless of MongoDB document field order.
     */
    private List<ExecutionPlan.CompiledAction> compileTopLevelActions(
            List<Automation.Action> actions,
            Set<String> triggerNodeIds,
            String group) {
        List<ExecutionPlan.CompiledAction> raw = actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream()
                        .anyMatch(r -> triggerNodeIds.contains(r.getNodeId())))
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId)) // stable tiebreak
                .map(this::compileAction)
                .collect(Collectors.toList());
        return deduplicateActions(raw);
    }

    /**
     * c1-negative: negative actions referencing trigger nodes.
     * Used when c1 turns false and a branch was active (branch automations).
     * Deduplicated by (deviceId,key,data). Stable sort by nodeId on order tie.
     */
    private List<ExecutionPlan.CompiledAction> compileC1NegativeActions(
            List<Automation.Action> actions, Set<String> triggerNodeIds) {
        List<ExecutionPlan.CompiledAction> raw = actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "negative".equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream()
                        .anyMatch(r -> triggerNodeIds.contains(r.getNodeId())))
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId))
                .map(this::compileAction)
                .collect(Collectors.toList());
        return deduplicateActions(raw);
    }

    private List<ExecutionPlan.CompiledAction> compileByGroup(
            List<Automation.Action> actions, String group) {
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId))
                .map(this::compileAction)
                .collect(Collectors.toList());
    }

    private List<ExecutionPlan.CompiledAction> compileStatelessActions(
            List<Automation.Action> actions) {
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "none".equalsIgnoreCase(a.getConditionGroup()))
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId))
                .map(this::compileAction)
                .collect(Collectors.toList());
    }

    private ExecutionPlan.CompiledAction compileAction(Automation.Action a) {
        return ExecutionPlan.CompiledAction.builder()
                .nodeId(a.getNodeId())
                .deviceId(a.getDeviceId())
                .key(a.getKey())
                .data(a.getData())
                .order(a.getOrder())
                .delaySeconds(a.getDelaySeconds())
                .name(a.getName())
                .deviceType(resolveDeviceType(a.getDeviceId()))
                .build();
    }

    private ExecutionPlan.CompiledCondition compileCondition(
            Automation.Condition c, String parentId, boolean isChained) {
        return ExecutionPlan.CompiledCondition.builder()
                .nodeId(c.getNodeId())
                .conditionType(c.getCondition())
                .triggerKey(c.getTriggerKey())
                .deviceId(c.getDeviceId())
                .value(c.getValue()).above(c.getAbove()).below(c.getBelow())
                .isExact(Boolean.TRUE.equals(c.getIsExact()))
                .scheduleType(c.getScheduleType())
                .fromTime(c.getFromTime()).toTime(c.getToTime()).time(c.getTime())
                .days(c.getDays()).solarType(c.getSolarType())
                .offsetMinutes(c.getOffsetMinutes())
                .intervalMinutes(c.getIntervalMinutes())
                .durationMinutes(c.getDurationMinutes())
                .parentConditionNodeId(parentId)
                .isChained(isChained)
                .build();
    }

    /**
     * Deduplicate by (deviceId,key,data) — keeps first occurrence in order.
     */
    private List<ExecutionPlan.CompiledAction> deduplicateActions(
            List<ExecutionPlan.CompiledAction> actions) {
        Set<String> seen = new LinkedHashSet<>();
        return actions.stream()
                .filter(a -> seen.add(a.getDeviceId() + "|" + a.getKey() + "|" + a.getData()))
                .collect(Collectors.toList());
    }

    private String resolveDeviceType(String deviceId) {
        try {
            var d = mainService.getDevice(deviceId);
            return d != null ? d.getType() : "sensor";
        } catch (Exception e) {
            return "sensor";
        }
    }
}