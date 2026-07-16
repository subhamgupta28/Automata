package dev.automata.automata.automation_engine;

import dev.automata.automata.dto.NodeRef;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.TriggerSource;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compiles a raw Automation into an ExecutionPlan.
 * <p>
 * Bug fix (this version)
 * ──────────────────────
 * Root node detection was broken for automations saved with the old
 * operator model. A condition is a root if its previousNodeRef points
 * ONLY to non-condition nodes (trigger, operator, or nothing). The old
 * code used a child-set derived only from condition→condition edges, so
 * any condition that was a gate (previousNodeRef → operator node) was
 * not in the child-set and was incorrectly treated as a root. The
 * evaluator then started a second walk from it in the rootConditionNodeIds
 * loop, but the condition had no connection to the primary tree walk, so
 * it evaluated independently and produced duplicate / out-of-context results
 * for the nodes that were reachable from it, while the nodes reachable only
 * from the first root were reported as "unevaluated".
 * <p>
 * Fix: a condition is a root if and only if NONE of its previousNodeRef
 * entries point to another ENABLED condition node. This correctly handles:
 * - New model: conditions whose parent is the trigger node (root ✓)
 * - Old gate model: conditions whose parent was an operator node (not root,
 * now wired as a child of whatever the operator fed — or treated as a
 * standalone child if the operator is gone)
 * - Disconnected conditions: no previousNodeRef at all (root ✓, will be
 * walked independently and likely produce a warn via observability)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionPlanCompiler {

    private final MainService mainService;


    // ─────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    public ExecutionPlan compile(Automation automation) {
        log.info("🔧 Compiling execution plan for '{}'", automation.getName());

        List<Automation.Condition> conditions =
                automation.getConditions() == null ? List.of() : automation.getConditions();
        List<Automation.Action> actions =
                automation.getActions() == null ? List.of() : automation.getActions();

        // ── Step 1: build enabled condition ID set ────────────────────────
        Set<String> conditionNodeIds = conditions.stream()
                .filter(Automation.Condition::isEnabled)
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        // ── Step 2: derive children from previousNodeRef ──────────────────
        Map<String, List<String>> positiveChildrenByParent = new LinkedHashMap<>();
        Map<String, List<String>> negativeChildrenByParent = new LinkedHashMap<>();
        Set<String> conditionChildIds = new HashSet<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            if (c.getPreviousNodeRef() == null) continue;

            for (NodeRef ref : c.getPreviousNodeRef()) {
                String parentId = ref.getNodeId();
                if (parentId == null) continue;
                if (!conditionNodeIds.contains(parentId)) continue;

                String handle = ref.getHandle() != null ? ref.getHandle() : "";
                conditionChildIds.add(c.getNodeId());

                if (handle.contains("cond-negative")) {
                    negativeChildrenByParent
                            .computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(c.getNodeId());
                } else {
                    positiveChildrenByParent
                            .computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(c.getNodeId());
                }
            }
        }

        // ── Step 3a: per-node action lists + "own" negative-action flag ───
        //
        // Split out of node compilation so we know, for every node, whether it
        // fires negative actions of its own — before we've decided the final
        // stateful flag. This "own" flag feeds Step 3c's branch propagation.

        Map<String, List<ExecutionPlan.CompiledAction>> posActionsByNode = new LinkedHashMap<>();
        Map<String, List<ExecutionPlan.CompiledAction>> negActionsByNode = new LinkedHashMap<>();
        Map<String, Boolean> ownHasNegative = new LinkedHashMap<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            String nodeId = c.getNodeId();

            List<ExecutionPlan.CompiledAction> posActions =
                    compileActionsForNode(actions, nodeId, "positive");
            List<ExecutionPlan.CompiledAction> negActions =
                    deduplicateActions(compileActionsForNode(actions, nodeId, "negative"));

            posActionsByNode.put(nodeId, posActions);
            negActionsByNode.put(nodeId, negActions);
            ownHasNegative.put(nodeId, !negActions.isEmpty());
        }

        // ── Step 3b: root node detection (moved earlier — needed for propagation) ──
        //
        // Same logic as before: a node not present in conditionChildIds is a root.
        // We compute this over the *enabled condition id set* directly (not nodeMap,
        // which doesn't exist yet at this point) — equivalent result, just earlier.

        List<String> rootConditionNodeIds = conditions.stream()
                .filter(Automation.Condition::isEnabled)
                .map(Automation.Condition::getNodeId)
                .filter(id -> !conditionChildIds.contains(id))
                .collect(Collectors.toList());

        if (rootConditionNodeIds.isEmpty() && !conditionNodeIds.isEmpty()) {
            log.error("❌ [{}] No root conditions found — possible cycle. Picking first node as fallback.",
                    automation.getName());
            rootConditionNodeIds = List.of(conditionNodeIds.iterator().next());
        }

        // ── Step 3c: propagate statefulness down each branch ───────────────
        //
        // A node's EFFECTIVE stateful flag is true if it (or ANY ancestor in its
        // trigger→...→node chain) fires negative actions. This is what lets a
        // node with zero negative actions of its own (e.g. node_condition_21)
        // still get edge-detection (wasActive tracking) when it's downstream of
        // a node that does have negatives (e.g. node_condition_1) — otherwise it
        // gets misclassified as "pure stateless" and refires every tick.
        //
        // Pure stateless branches (trigger→...→action with NO negative actions
        // anywhere in the chain) correctly keep effectiveStateful=false all the
        // way down, since inherited starts false at the root and nothing ORs it
        // true.

        Map<String, Boolean> effectiveStateful = new LinkedHashMap<>();
        Map<String, Boolean> inherited = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        for (String rootId : rootConditionNodeIds) {
            inherited.put(rootId, false);
            queue.add(rootId);
        }

        Set<String> visitedForPropagation = new HashSet<>(); // cycle guard
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visitedForPropagation.add(id)) continue; // already processed — avoid infinite loop on cycles

            boolean ownNeg = ownHasNegative.getOrDefault(id, false);
            boolean parentInherited = inherited.getOrDefault(id, false);
            boolean effective = ownNeg || parentInherited;
            effectiveStateful.put(id, effective);

            for (String child : positiveChildrenByParent.getOrDefault(id, List.of())) {
                inherited.merge(child, effective, Boolean::logicalOr);
                queue.add(child);
            }
            for (String child : negativeChildrenByParent.getOrDefault(id, List.of())) {
                inherited.merge(child, effective, Boolean::logicalOr);
                queue.add(child);
            }
        }

        // ── Step 3d: compile each condition into a CompiledConditionNode ───
        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = new LinkedHashMap<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;

            String nodeId = c.getNodeId();
            ConditionMemoryPolicy memPolicy = buildMemoryPolicy(c);

            List<ExecutionPlan.CompiledAction> posActions = posActionsByNode.get(nodeId);
            List<ExecutionPlan.CompiledAction> negActions = negActionsByNode.get(nodeId);

            // Branch-propagated statefulness — NOT just "!negActions.isEmpty()" anymore.
            boolean stateful = effectiveStateful.getOrDefault(nodeId, ownHasNegative.getOrDefault(nodeId, false));

            List<String> posChildren =
                    positiveChildrenByParent.getOrDefault(nodeId, List.of());
            List<String> negChildren =
                    negativeChildrenByParent.getOrDefault(nodeId, List.of());

            boolean isFanout = posChildren.size() > 1;
            String derivedFanoutMode = isFanout
                    ? (isFirstMatchFanout(c) ? "FIRST_MATCH" : "ALL")
                    : null;

            nodeMap.put(nodeId, ExecutionPlan.CompiledConditionNode.builder()
                    .nodeId(nodeId)
                    .condition(compileCondition(c, automation.getTrigger().getDeviceId()))
                    .positiveActions(posActions)
                    .negativeActions(negActions)
                    .positiveChildNodeIds(posChildren)
                    .negativeChildNodeIds(negChildren)
                    .stateful(stateful)
                    .memoryPolicy(memPolicy)
                    .fanout(isFanout)
                    .fanoutMode(derivedFanoutMode)
                    .firstMatch("FIRST_MATCH".equals(derivedFanoutMode))
                    .build());
        }

        log.debug("  roots={} (total nodes={})", rootConditionNodeIds, nodeMap.size());

        for (String rootId : rootConditionNodeIds) {
            Automation.Condition c = conditions.stream()
                    .filter(x -> rootId.equals(x.getNodeId()))
                    .findFirst().orElse(null);
            if (c != null && c.getPreviousNodeRef() != null) {
                boolean hasOperatorParent = c.getPreviousNodeRef().stream()
                        .anyMatch(ref -> ref.getNodeId() != null
                                && !conditionNodeIds.contains(ref.getNodeId())
                                && ref.getNodeId().contains("operator"));
                if (hasOperatorParent) {
                    log.warn("⚠️ [{}] Root node '{}' previously had an operator parent. "
                                    + "Re-save the automation in the editor to clean up the graph.",
                            automation.getName(), rootId);
                }
            }
        }

        // ── Step 5: global action groups ──────────────────────────────────
        List<ExecutionPlan.CompiledAction> informational = compileByGroup(actions, "informational");
        List<ExecutionPlan.CompiledAction> fallback = compileByGroup(actions, "fallback");
        List<ExecutionPlan.CompiledAction> stateless = compileStatelessActions(actions);

        List<ExecutionPlan.CompiledAction> topLevelPositive =
                nodeMap.values().stream()
                        .filter(n -> n.getPositiveActions() != null)
                        .flatMap(n -> n.getPositiveActions().stream())
                        .sorted(Comparator.comparingInt(
                                        (ExecutionPlan.CompiledAction a) ->
                                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                                .thenComparing(ExecutionPlan.CompiledAction::getNodeId))
                        .distinct()
                        .collect(Collectors.toList());

        List<ExecutionPlan.CompiledAction> topLevelNegative =
                deduplicateActions(
                        nodeMap.values().stream()
                                .filter(n -> n.getNegativeActions() != null)
                                .flatMap(n -> n.getNegativeActions().stream())
                                .sorted(Comparator.comparingInt(
                                                (ExecutionPlan.CompiledAction a) ->
                                                        a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                                        .thenComparing(ExecutionPlan.CompiledAction::getNodeId))
                                .collect(Collectors.toList()));

        TriggerCoalition coalition = buildCoalition(automation);

        ExecutionPlan plan = ExecutionPlan.builder()
                .automationId(automation.getId())
                .automationName(automation.getName())
                .homeId(automation.getHomeId())
                .triggerDeviceId(automation.getTrigger().getDeviceId())
                .schemaVersion(ExecutionPlan.CURRENT_SCHEMA_VERSION)
                .compiledAt(new Date())
                .conditionTree(new ArrayList<>(nodeMap.values()))
                .rootConditionNodeIds(rootConditionNodeIds)
                .statelessActions(stateless)
                .fallbackActions(fallback)
                .informationalActions(informational)
                .topLevelPositiveActions(topLevelPositive)
                .topLevelNegativeActions(topLevelNegative)
                .triggerCoalition(coalition)
                .build();

        log.info("✅ Plan compiled for '{}': {} nodes, roots={}, {} stateless, {} fallback, homeId={}",
                automation.getName(), nodeMap.size(), rootConditionNodeIds,
                stateless.size(), fallback.size(), automation.getHomeId());

        return plan;
    }

    /**
     * The only thing the UI's fanoutMode field is still trusted for: an explicit
     * opt-in to FIRST_MATCH (stop at the first passing branch). Topology alone
     * can't distinguish "evaluate every sibling independently" from "first one
     * wins" — that's a genuine authoring decision, not a graph-structure fact.
     * Anything other than a literal "FIRST_MATCH" defaults to ALL.
     */
    private boolean isFirstMatchFanout(Automation.Condition c) {
        return "FIRST_MATCH".equals(c.getFanoutMode());
    }

    // ─────────────────────────────────────────────────────────────────────
    // MEMORY POLICY
    // ─────────────────────────────────────────────────────────────────────

    private ConditionMemoryPolicy buildMemoryPolicy(Automation.Condition c) {
        if (c.getMemoryPolicy() == null || c.getMemoryPolicy().isBlank()) return null;
        try {
            ConditionMemoryPolicy.MemoryType type =
                    ConditionMemoryPolicy.MemoryType.valueOf(c.getMemoryPolicy());
            return ConditionMemoryPolicy.builder()
                    .type(type)
                    .requiredDurationSeconds(
                            type == ConditionMemoryPolicy.MemoryType.DURATION
                                    ? c.getMemoryPolicyValue() : 0)
                    .requiredTicks(
                            type == ConditionMemoryPolicy.MemoryType.CONSECUTIVE_TICKS
                                    ? c.getMemoryPolicyValue() : 0)
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown memory policy '{}' on node '{}' — ignored",
                    c.getMemoryPolicy(), c.getNodeId());
            return null;
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // ACTION COMPILATION
    // ─────────────────────────────────────────────────────────────────────

    private List<ExecutionPlan.CompiledAction> compileActionsForNode(
            List<Automation.Action> actions, String nodeId, String group) {
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null) return false;
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        if (!nodeId.equals(ref.getNodeId())) return false;
                        String handle = ref.getHandle() != null ? ref.getHandle() : "";
                        if (handle.contains("cond-positive"))
                            return "positive".equals(group);
                        if (handle.contains("cond-negative"))
                            return "negative".equals(group);
                        // Fallback: use conditionGroup field for legacy actions
                        return group.equalsIgnoreCase(a.getConditionGroup());
                    });
                })
                .sorted(Comparator
                        .comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId))
                .map(this::compileAction)
                .collect(Collectors.toList());
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


    // ─────────────────────────────────────────────────────────────────────
    // CONDITION COMPILATION
    // ─────────────────────────────────────────────────────────────────────

    private ExecutionPlan.CompiledCondition compileCondition(
            Automation.Condition c, String triggerDeviceId) {
        String deviceId = c.getDeviceId();
        if ("stale".equals(c.getCondition()) && (deviceId == null || deviceId.isBlank())) {
            deviceId = triggerDeviceId;
        }
        String triggerKey = c.getTriggerKey();
        if ("stale".equals(c.getCondition()) && (triggerKey == null || triggerKey.isBlank())) {
            triggerKey = "last_seen";
        }
        return ExecutionPlan.CompiledCondition.builder()
                .nodeId(c.getNodeId())
                .conditionType(c.getCondition())
                .triggerKey(triggerKey)
                .deviceId(deviceId)
                .valueType(c.getValueType())
                .value(c.getValue())
                .above(c.getAbove())
                .below(c.getBelow())
                .isExact(Boolean.TRUE.equals(c.getIsExact()))
                .scheduleType(c.getScheduleType())
                .fromTime(c.getFromTime())
                .toTime(c.getToTime())
                .time(c.getTime())
                .days(c.getDays())
                .solarType(c.getSolarType())
                .offsetMinutes(c.getOffsetMinutes())
                .intervalMinutes(c.getIntervalMinutes())
                .durationMinutes(c.getDurationMinutes())
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // DEDUPLICATION
    // ─────────────────────────────────────────────────────────────────────

    private List<ExecutionPlan.CompiledAction> deduplicateActions(
            List<ExecutionPlan.CompiledAction> actions) {
        Set<String> seen = new LinkedHashSet<>();
        return actions.stream()
                .filter(a -> seen.add(a.getDeviceId() + "|" + a.getKey() + "|" + a.getData()))
                .collect(Collectors.toList());
    }


    // ─────────────────────────────────────────────────────────────────────
    // COALITION
    // ─────────────────────────────────────────────────────────────────────

    private TriggerCoalition buildCoalition(Automation automation) {
        List<TriggerSource> sources = automation.getTrigger().getSources();
        if (sources == null || sources.size() <= 1) return null;

        TriggerCoalition.CoalitionMode mode = TriggerCoalition.CoalitionMode.ANY;
        int windowSeconds = 60;

        List<TriggerMember> members = sources.stream()
                .map(s -> TriggerMember.builder()
                        .deviceId(s.getDeviceId())
                        .keys(s.getKeys())
                        .role(s.getRole())
                        .sequenceIndex(0)
                        .build())
                .toList();

        return TriggerCoalition.builder()
                .mode(mode)
                .windowSeconds(windowSeconds)
                .members(members)
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private String resolveDeviceType(String deviceId) {
        try {
            var d = mainService.getDevice(deviceId);
            return d != null ? d.getType() : "sensor";
        } catch (Exception e) {
            return "sensor";
        }
    }
}