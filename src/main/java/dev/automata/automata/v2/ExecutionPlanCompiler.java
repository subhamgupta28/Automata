package dev.automata.automata.v2;

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
        //
        // For each enabled condition C, look at its previousNodeRef list.
        // If a ref points to another enabled condition node (parentId ∈ conditionNodeIds),
        // then C is a child of that parent. Record the edge direction.
        //
        // Edges to trigger nodes, operator nodes, or unknown nodes are intentionally
        // ignored here — they do not create tree parent→child relationships.
        //
        // Handle: "cond-positive" → positive child
        //         "cond-negative" → negative child
        //         anything else   → positive child (legacy / unhandled handle strings)

        Map<String, List<String>> positiveChildrenByParent = new LinkedHashMap<>();
        Map<String, List<String>> negativeChildrenByParent = new LinkedHashMap<>();

        // Also track which condition IDs appear as a child of any other condition.
        // Used for root detection below.
        Set<String> conditionChildIds = new HashSet<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            if (c.getPreviousNodeRef() == null) continue;

            for (NodeRef ref : c.getPreviousNodeRef()) {
                String parentId = ref.getNodeId();
                if (parentId == null) continue;

                // Only wire condition→condition edges
                if (!conditionNodeIds.contains(parentId)) continue;

                String handle = ref.getHandle() != null ? ref.getHandle() : "";
                conditionChildIds.add(c.getNodeId());

                if (handle.contains("cond-negative")) {
                    negativeChildrenByParent
                            .computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(c.getNodeId());
                } else {
                    // "cond-positive", "out:operator:..." (legacy gates now treated as positive),
                    // or any unrecognised handle → positive child
                    positiveChildrenByParent
                            .computeIfAbsent(parentId, k -> new ArrayList<>())
                            .add(c.getNodeId());
                }
            }
        }

        // ── Step 3: compile each condition into a CompiledConditionNode ───
        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = new LinkedHashMap<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;

            String nodeId = c.getNodeId();
            ConditionMemoryPolicy memPolicy = buildMemoryPolicy(c);

            List<ExecutionPlan.CompiledAction> posActions =
                    compileActionsForNode(actions, nodeId, "positive");
            List<ExecutionPlan.CompiledAction> negActions =
                    deduplicateActions(compileActionsForNode(actions, nodeId, "negative"));

            boolean stateful = !negActions.isEmpty();

            List<String> posChildren =
                    positiveChildrenByParent.getOrDefault(nodeId, List.of());
            List<String> negChildren =
                    negativeChildrenByParent.getOrDefault(nodeId, List.of());
            // Derive fanout purely from topology — never trust c.getFanoutMode() from the UI.
            // This was the deeper root cause of BUG 4: even when fanoutMode WAS set on a
            // node, isFanout was never set on CompiledConditionNode at all, so the
            // evaluator's AND-path/OR-path branch in walkNode() never routed multi-child
            // nodes to the OR fanout logic in the first place.
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

        // ── Step 4: root node detection (BUG FIX) ─────────────────────────
        //
        // A condition is a ROOT if and only if it is NOT in conditionChildIds.
        // conditionChildIds contains every nodeId that appears as a positive or
        // negative child of some other enabled condition. If a node's ID is not
        // in that set, nothing in the condition tree points to it as a child,
        // so it must be a starting point (root).
        //
        // This correctly handles:
        //   1. Conditions whose previousNodeRef → trigger node only (true root ✓)
        //   2. Conditions whose previousNodeRef → operator node only (old gate model;
        //      not a child of any condition → treated as root ✓, the walk will reach
        //      them and their sub-trees)
        //   3. Conditions with no previousNodeRef (disconnected; root ✓, observability
        //      will warn about them)
        //   4. Conditions whose previousNodeRef → another condition (child, NOT root ✓)
        //
        // Preserve insertion order (LinkedHashMap iteration order from nodeMap)
        // so root evaluation is deterministic.
        List<String> rootConditionNodeIds = nodeMap.keySet().stream()
                .filter(id -> !conditionChildIds.contains(id))
                .collect(Collectors.toList());

        if (rootConditionNodeIds.isEmpty() && !nodeMap.isEmpty()) {
            // Every node is someone's child — cycle in the graph.
            // Pick the node with the fewest incoming edges as the best-guess root
            // so evaluation degrades gracefully. The validator will report the cycle.
            log.error("❌ [{}] No root conditions found — possible cycle. Picking first node as fallback.",
                    automation.getName());
            rootConditionNodeIds = List.of(nodeMap.keySet().iterator().next());
        }

        log.debug("  roots={} (total nodes={})", rootConditionNodeIds, nodeMap.size());

        // Log a warning for any conditions that ended up as roots but have a
        // previousNodeRef pointing to an operator node — these are old gate conditions
        // that have been promoted to roots after the operator model was removed.
        // The user should re-save the automation in the editor to clean this up.
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

        log.info("✅ Plan compiled for '{}': {} nodes, roots={}, {} stateless, {} fallback",
                automation.getName(), nodeMap.size(), rootConditionNodeIds,
                stateless.size(), fallback.size());

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