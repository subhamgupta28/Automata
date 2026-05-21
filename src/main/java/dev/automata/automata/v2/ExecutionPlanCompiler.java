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
 * Compiles a raw Automation (node graph with handle strings) into an
 * ExecutionPlan (flat, typed, pre-sorted).
 * <p>
 * Called ONCE at save time from AutomationService.saveAutomationDetailInternal().
 * Never called at evaluation time.
 * <p>
 * Key changes vs previous version:
 * <p>
 * 1. CONDITION TREE (Bug 1 fix)
 * Previously: flat List<CompiledCondition> triggerConditions — the evaluator
 * could only answer "did all pass?" and then fire all negative actions in a
 * merged heap when any condition failed, regardless of which one failed.
 * <p>
 * Now: each condition node becomes a CompiledConditionNode that holds:
 * - its own positiveActions  (wired to out:cond-positive handle)
 * - its own negativeActions  (wired to out:cond-negative handle)
 * - positiveChildNodeId      (next condition to evaluate when true)
 * - negativeChildNodeId      (next condition when false, usually null)
 * The evaluator walks this tree recursively and fires exactly the right
 * actions for every outcome at every depth.
 * <p>
 * 2. LOGIC TYPE PROPAGATION (Bug 4 fix)
 * Operator logicType ("AND"/"OR") and sibling gate IDs are now stored on
 * each CompiledBranch.  The evaluator enforces AND: all sibling gates under
 * an AND operator must be true before any of them can TRIGGER.
 * <p>
 * 3. INTERVAL KEYS MOVED OUT OF COMPILER (Bug 2/3 fix)
 * The compiler no longer sets any Redis keys.  The evaluator signals intent
 * via EvalResult flags; the orchestrator writes keys post-CAS.
 * <p>
 * 4. DEAD OVERLOADS REMOVED
 * Only one dfsVisit overload (the one with cycle detection via "visiting" set).
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
        List<Automation.Operator> operators =
                automation.getOperators() == null ? List.of() : automation.getOperators();
        List<Automation.Action> actions =
                automation.getActions() == null ? List.of() : automation.getActions();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId).collect(Collectors.toSet());
        Set<String> conditionNodeIds = conditions.stream()
                .map(Automation.Condition::getNodeId).collect(Collectors.toSet());

        // ── Classify conditions ───────────────────────────────────────────
        // Gate   = previousNodeRef points to an operator node
        // Chained= previousNodeRef points to another condition node (not an operator)
        // Root   = everything else (previousNodeRef points to the trigger node)
        List<Automation.Condition> rootConditions = new ArrayList<>();
        List<Automation.Condition> chainedConditions = new ArrayList<>();
        List<Automation.Condition> gateConditions = new ArrayList<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            if (isGate(c, operatorIds)) gateConditions.add(c);
            else if (isChained(c, conditionNodeIds, operatorIds)) chainedConditions.add(c);
            else rootConditions.add(c);
        }

        // ── Build condition tree ──────────────────────────────────────────
        // All trigger-side nodes (root + chained) are turned into a tree where
        // each node knows its own per-handle actions and its child node IDs.
        List<Automation.Condition> allTriggerConditions = new ArrayList<>();
        allTriggerConditions.addAll(rootConditions);
        allTriggerConditions.addAll(topoSortChained(chainedConditions, conditionNodeIds));

        // Collect node IDs of all trigger-side conditions (needed for action routing)
        Set<String> triggerNodeIds = allTriggerConditions.stream()
                .map(Automation.Condition::getNodeId).collect(Collectors.toSet());

        // Build action lookup: nodeId → list of actions wired from that node's handles
        // We need to know which actions reference each condition via cond-positive / cond-negative
        Map<String, List<Automation.Action>> positiveActionsBySourceNode =
                buildActionIndex(actions, "positive", triggerNodeIds);
        Map<String, List<Automation.Action>> negativeActionsBySourceNode =
                buildActionIndex(actions, "negative", triggerNodeIds);

        // Build child-node lookups:
        // "which condition node is the positive/negative child of node X?"
        // A condition C is the positive child of P when:
        //   C.previousNodeRef contains P's nodeId with handle out:cond-positive
        Map<String, List<String>> positiveChildrenByNode = new HashMap<>();  // parentId → [childId...]
        Map<String, List<String>> negativeChildrenByNode = new HashMap<>();

        for (Automation.Condition c : chainedConditions) {
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (!triggerNodeIds.contains(ref.getNodeId())) continue;
                String handle = ref.getHandle() != null ? ref.getHandle() : "";
                if (handle.startsWith("out:cond-positive"))
                    positiveChildrenByNode.computeIfAbsent(ref.getNodeId(), k -> new ArrayList<>())
                            .add(c.getNodeId());
                else if (handle.startsWith("out:cond-negative"))
                    negativeChildrenByNode.computeIfAbsent(ref.getNodeId(), k -> new ArrayList<>())
                            .add(c.getNodeId());
            }
        }

        // Compile each trigger-side node into a CompiledConditionNode
        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = new LinkedHashMap<>();
        for (Automation.Condition c : allTriggerConditions) {
            String nodeId = c.getNodeId();
            ConditionMemoryPolicy memPolicy = buildMemoryPolicy(c);
            List<ExecutionPlan.CompiledAction> posActions =
                    compileActionList(positiveActionsBySourceNode.getOrDefault(nodeId, List.of()));
            List<ExecutionPlan.CompiledAction> negActions =
                    deduplicateActions(
                            compileActionList(negativeActionsBySourceNode.getOrDefault(nodeId, List.of())));

            boolean stateful = !negActions.isEmpty();

            nodeMap.put(nodeId, ExecutionPlan.CompiledConditionNode.builder()
                    .nodeId(nodeId)
                    .condition(compileCondition(c, automation.getTrigger().getDeviceId()))
                    .positiveActions(posActions)
                    .memoryPolicy(memPolicy)
                    .negativeActions(negActions)
                    .positiveChildNodeIds(positiveChildrenByNode.get(nodeId))
                    .negativeChildNodeIds(negativeChildrenByNode.get(nodeId))
                    .stateful(stateful)
                    .build());
        }

        // Root condition node IDs = trigger-side nodes with no condition parent
        List<String> rootConditionNodeIds = rootConditions.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toList());

        // ── Gate branches ─────────────────────────────────────────────────
        List<ExecutionPlan.CompiledBranch> branches =
                buildBranches(gateConditions, operators, operatorIds, actions, automation.getTrigger().getDeviceId());

        // ── Informational / fallback / stateless actions ──────────────────
        List<ExecutionPlan.CompiledAction> informational = compileByGroup(actions, "informational");
        List<ExecutionPlan.CompiledAction> fallback = compileByGroup(actions, "fallback");
        List<ExecutionPlan.CompiledAction> stateless = compileStatelessActions(actions);

        // ── Top-level positive / negative actions ─────────────────────────
        // These are the actions directly wired to trigger-side nodes (not gates).
        // Needed by handleNoBranch() as a fallback when the tree walk finds no
        // leaf positive actions, and by backward-compat automations compiled
        // before the condition-tree walk was introduced.
        List<ExecutionPlan.CompiledAction> topLevelPositive =
                compileActionList(positiveActionsBySourceNode.values().stream()
                        .flatMap(List::stream)
                        .sorted(Comparator
                                .comparingInt((Automation.Action a) ->
                                        a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                                .thenComparing(Automation.Action::getNodeId))
                        .toList());

        List<ExecutionPlan.CompiledAction> topLevelNegative =
                deduplicateActions(
                        compileActionList(negativeActionsBySourceNode.values().stream()
                                .flatMap(List::stream)
                                .sorted(Comparator
                                        .comparingInt((Automation.Action a) ->
                                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                                        .thenComparing(Automation.Action::getNodeId))
                                .toList()));

        TriggerCoalition coalition = buildCoalition(automation);


        ExecutionPlan plan = ExecutionPlan.builder()
                .automationId(automation.getId())
                .automationName(automation.getName())
                .triggerDeviceId(automation.getTrigger().getDeviceId())
                .schemaVersion(ExecutionPlan.CURRENT_SCHEMA_VERSION)
                .compiledAt(new Date())
                .conditionTree(new ArrayList<>(nodeMap.values()))
                .rootConditionNodeIds(rootConditionNodeIds)
                .branches(branches)
                .statelessActions(stateless)
                .fallbackActions(fallback)
                .informationalActions(informational)
                .topLevelPositiveActions(topLevelPositive)
                .topLevelNegativeActions(topLevelNegative)
                .triggerCoalition(coalition)
                .build();

        log.info("✅ Plan compiled for '{}': {} condition-tree nodes (roots: {}), {} branches, {} fallback, {} stateless",
                automation.getName(),
                nodeMap.size(), rootConditionNodeIds.size(),
                branches.size(), fallback.size(), stateless.size());

        return plan;
    }

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
    // CLASSIFICATION HELPERS
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


    // ─────────────────────────────────────────────────────────────────────
    // TOPOLOGICAL SORT (chained conditions)
    // ─────────────────────────────────────────────────────────────────────

    private List<Automation.Condition> topoSortChained(
            List<Automation.Condition> chained, Set<String> conditionNodeIds) {
        Map<String, Automation.Condition> byId = chained.stream()
                .collect(Collectors.toMap(Automation.Condition::getNodeId, c -> c));
        List<Automation.Condition> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>(); // cycle detection

        for (Automation.Condition c : chained)
            if (!visited.contains(c.getNodeId()))
                dfsVisit(c, byId, conditionNodeIds, visited, visiting, sorted);

        // Validate: every parent must appear before its child
        Map<String, Integer> sortIndex = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++)
            sortIndex.put(sorted.get(i).getNodeId(), i);

        for (Automation.Condition c : sorted) {
            if (c.getPreviousNodeRef() == null) continue;
            c.getPreviousNodeRef().stream()
                    .map(NodeRef::getNodeId)
                    .filter(byId::containsKey)
                    .forEach(parentId -> {
                        Integer parentIdx = sortIndex.get(parentId);
                        Integer childIdx = sortIndex.get(c.getNodeId());
                        if (parentIdx == null || childIdx == null || parentIdx > childIdx)
                            throw new IllegalStateException(
                                    "Topological sort failed: parent '" + parentId +
                                            "' (index " + parentIdx + ") must come before child '" +
                                            c.getNodeId() + "' (index " + childIdx + ")");
                    });
        }
        return sorted;
    }

    private void dfsVisit(Automation.Condition c,
                          Map<String, Automation.Condition> byId,
                          Set<String> conditionNodeIds,
                          Set<String> visited,
                          Set<String> visiting,
                          List<Automation.Condition> sorted) {
        if (visiting.contains(c.getNodeId()))
            throw new IllegalStateException(
                    "Circular dependency in chained conditions: " + c.getNodeId());
        if (visited.contains(c.getNodeId())) return;

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


    // ─────────────────────────────────────────────────────────────────────
    // ACTION INDEX
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Groups actions by the source condition node they reference, filtered by
     * conditionGroup (positive/negative) and restricted to trigger-side nodes.
     * <p>
     * This replaces the old compileTopLevelActions / compileC1NegativeActions
     * methods that merged all actions into flat lists regardless of which node
     * triggered them.
     */
    private Map<String, List<Automation.Action>> buildActionIndex(
            List<Automation.Action> actions,
            String conditionGroup,
            Set<String> allowedSourceNodeIds) {
        Map<String, List<Automation.Action>> index = new LinkedHashMap<>();
        for (Automation.Action a : actions) {
            if (!Boolean.TRUE.equals(a.getIsEnabled())) continue;
            if (!conditionGroup.equalsIgnoreCase(a.getConditionGroup())) continue;
            if (a.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : a.getPreviousNodeRef()) {
                if (allowedSourceNodeIds.contains(ref.getNodeId())) {
                    index.computeIfAbsent(ref.getNodeId(), k -> new ArrayList<>()).add(a);
                }
            }
        }
        // Sort each bucket by order, then nodeId for stability
        index.values().forEach(list -> list.sort(
                Comparator.comparingInt((Automation.Action a) ->
                                a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE)
                        .thenComparing(Automation.Action::getNodeId)));
        return index;
    }


    // ─────────────────────────────────────────────────────────────────────
    // BRANCH COMPILATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Compiles gate conditions into branches.
     * <p>
     * Key additions vs previous version:
     * - logicType is read from the operator and stored on CompiledBranch
     * - siblingGateNodeIds is computed per operator so the evaluator can
     * enforce AND semantics (all siblings must be true before triggering)
     * <p>
     * Real-life mapping:
     * node_and_6 (AND, priority=10) → gate: node_condition_1
     * logicType = "AND"
     * siblingGateNodeIds = []  (only one gate under this operator)
     * <p>
     * node_or_9 (OR, priority=7)  → gate: node_condition_8
     * node_or_17 (OR, priority=5) → gate: node_condition_10
     * Each has logicType = "OR", siblingGateNodeIds = []
     * (sibling list only matters for AND)
     */
    private List<ExecutionPlan.CompiledBranch> buildBranches(
            List<Automation.Condition> gateConditions,
            List<Automation.Operator> operators,
            Set<String> operatorIds,
            List<Automation.Action> actions,
            String triggerDeviceId) {

        Map<String, Automation.Operator> opById = operators.stream()
                .collect(Collectors.toMap(Automation.Operator::getNodeId, o -> o));

        // Map operator → list of gate node IDs under it (for AND sibling resolution)
        Map<String, List<String>> gatesByOperator = new LinkedHashMap<>();
        Map<String, List<String>> operatorForGate = new HashMap<>(); // gateNodeId → operatorId

        for (Automation.Condition gc : gateConditions) {
            if (gc.getPreviousNodeRef() == null) continue;

            // A gate can reference multiple operators — build a branch for each
            for (NodeRef ref : gc.getPreviousNodeRef()) {
                if (!operatorIds.contains(ref.getNodeId())) continue;
                String opId = ref.getNodeId();

                gatesByOperator.computeIfAbsent(opId, k -> new ArrayList<>()).add(gc.getNodeId());
                // Store as list since one gate can belong to multiple operators
                operatorForGate.computeIfAbsent(gc.getNodeId(), k -> new ArrayList<>()).add(opId);
            }
        }

        List<ExecutionPlan.CompiledBranch> branches = new ArrayList<>();

        for (Automation.Condition gc : gateConditions) {
            List<String> opIds = operatorForGate.getOrDefault(gc.getNodeId(), List.of());
            if (opIds.isEmpty()) {
                log.warn("Gate '{}' has no parent operator — skipping", gc.getNodeId());
                continue;
            }

            // Build one CompiledBranch per operator this gate feeds into
            for (String opId : opIds) {
                Automation.Operator op = opById.get(opId);
                if (op == null) continue;

                List<String> siblings = gatesByOperator.getOrDefault(opId, List.of()).stream()
                        .filter(id -> !id.equals(gc.getNodeId()))
                        .collect(Collectors.toList());

                // Use a compound gateNodeId when the same gate feeds multiple operators
                // so each branch has a unique key for state tracking
                String branchKey = opIds.size() > 1
                        ? gc.getNodeId() + "@" + opId
                        : gc.getNodeId();

                branches.add(ExecutionPlan.CompiledBranch.builder()
                        .gateNodeId(branchKey)      // unique per branch even if same gate
                        .priority(op.getPriority())
                        .logicType(op.getLogicType() != null ? op.getLogicType() : "OR")
                        .siblingGateNodeIds(siblings)
                        .gateCondition(compileCondition(gc, triggerDeviceId))  // same condition, evaluated independently
                        .positiveActions(compileActionsForGate(actions, gc.getNodeId(), "positive"))
                        .negativeActions(deduplicateActions(
                                compileActionsForGate(actions, gc.getNodeId(), "negative")))
                        .build());
            }
        }

        // Sort priority DESC — evaluator picks the highest-priority true branch
        branches.sort(Comparator.comparingInt(ExecutionPlan.CompiledBranch::getPriority).reversed());
        return branches;
    }

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
                        .thenComparing(Automation.Action::getNodeId))
                .map(this::compileAction)
                .collect(Collectors.toList());
    }


    // ─────────────────────────────────────────────────────────────────────
    // GLOBAL ACTION GROUPS
    // ─────────────────────────────────────────────────────────────────────

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


    // ─────────────────────────────────────────────────────────────────────
    // COMPILE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private List<ExecutionPlan.CompiledAction> compileActionList(
            List<Automation.Action> actions) {
        return actions.stream().map(this::compileAction).collect(Collectors.toList());
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

    private ExecutionPlan.CompiledCondition compileCondition(Automation.Condition c, String triggerDeviceId) {

        String deviceId = c.getDeviceId();
        if ("stale".equals(c.getCondition()) && (deviceId == null || deviceId.isBlank())) {
            deviceId = triggerDeviceId;  // so evalStale DB fallback knows which device
        }
        String triggerKey = c.getTriggerKey();
        if ("stale".equals(c.getCondition()) && (triggerKey == null || triggerKey.isBlank())) {
            triggerKey = "last_seen";   // default key for stale conditions
        }
        return ExecutionPlan.CompiledCondition.builder()
                .nodeId(c.getNodeId())
                .conditionType(c.getCondition())
                .triggerKey(triggerKey)
                .deviceId(c.getDeviceId())
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

    /**
     * Deduplicate by (deviceId, key, data) — keeps first occurrence in order.
     * Prevents the same device command from being dispatched twice when the
     * same action is wired to multiple upstream nodes.
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

    private TriggerCoalition buildCoalition(Automation automation) {
        List<TriggerSource> sources = automation.getTrigger().getSources();
        if (sources == null || sources.size() <= 1) return null;  // legacy single-trigger

        // Default mode: ANY (OR-like — existing behaviour)
        // Override via automation.trigger.coalitionMode if field added
        TriggerCoalition.CoalitionMode mode = TriggerCoalition.CoalitionMode.ANY;
        int windowSeconds = 60; // default

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
}