package dev.automata.automata.v2;

import dev.automata.automata.dto.NodeRef;
import dev.automata.automata.model.Automation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Single source of truth for condition-graph topology: which conditions are
 * roots, which conditions are children of which, and how disabled nodes are
 * bypassed.
 * <p>
 * Why this exists
 * ───────────────
 * Both ExecutionPlanCompiler (which decides what the engine actually
 * evaluates at runtime) and AutomationGraphValidator (which decides what the
 * editor warns about at save time) need to answer the exact same question —
 * "given this set of conditions and their previousNodeRef edges, what is the
 * tree?" — and historically they have answered it with two independently
 * written, subtly different implementations:
 * <p>
 * 1. ExecutionPlanCompiler's own javadoc describes having already fixed a
 * root-detection bug for the old operator-gate model, where a condition
 * whose previousNodeRef pointed at a gate operator (not the trigger, not
 * another condition) was incorrectly treated as a root.
 * 2. AutomationGraphValidator's orphan-detection (Check 4) independently
 * reintroduced an analogous bug for DISABLED conditions: it built edges
 * only from ENABLED conditions as sources, so a chain routed through a
 * disabled intermediate node lost its edge entirely, producing
 * false-positive "will never be evaluated" warnings for nodes the
 * compiler would actually treat as new roots and evaluate normally.
 * <p>
 * Centralizing the edge-building, disabled-node-bypass, root-detection, and
 * cycle-detection logic here means both call sites read the SAME answer —
 * if one is fixed, the other can't silently drift out of sync again.
 */
public final class GraphTopology {

    private GraphTopology() {
    }

    /**
     * One resolved topology computation over a set of conditions.
     * <p>
     * effectiveChildren: parentId -> directly-reachable child ids, with
     * disabled nodes transparently bypassed (a chain through a disabled
     * node is rewired to skip it, exactly as ExecutionPlanCompiler's
     * conditionNodeIds-membership check already causes to happen for root
     * detection).
     * <p>
     * rootIds: condition ids with no enabled-condition parent in the
     * EFFECTIVE graph — i.e. every previousNodeRef entry points at the
     * trigger, an operator/unknown node, or a DISABLED condition.
     */
    public record Topology(
            Map<String, Set<String>> effectiveChildren,
            Set<String> rootIds,
            Set<String> enabledConditionIds,
            Set<String> disabledConditionIds
    ) {
        public Set<String> childrenOf(String nodeId) {
            return effectiveChildren.getOrDefault(nodeId, Set.of());
        }
    }

    /**
     * Resolves the full topology for a list of conditions, edges taken from
     * each condition's previousNodeRef. Edges are recorded when the parent
     * is either another condition (enabled or disabled — disabled ones are
     * bypassed, see resolveThroughDisabled) OR the trigger node itself.
     * Edges whose parent is an operator/unknown node are intentionally
     * dropped — those don't create condition-tree parent/child semantics,
     * they only make the child a root (since it then has no qualifying
     * condition OR trigger parent... unless it also has another edge that
     * does point at the trigger or a condition, which is handled normally).
     *
     * @param triggerId the trigger node's id — required so trigger->condition
     *                  edges are correctly recorded as the start of the
     *                  reachability graph, not silently dropped.
     */
    public static Topology resolve(List<Automation.Condition> conditions, String triggerId) {
        List<Automation.Condition> safe = conditions == null ? List.of() : conditions;

        Set<String> enabledIds = safe.stream()
                .filter(Automation.Condition::isEnabled)
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());
        Set<String> disabledIds = safe.stream()
                .filter(c -> !c.isEnabled())
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());
        Set<String> allConditionIds = new HashSet<>(enabledIds);
        allConditionIds.addAll(disabledIds);

        // BUG FIX: a parent is "valid" for raw-edge purposes when it is
        // either a condition (enabled or disabled) OR the trigger node.
        // The previous version only accepted condition ids, which silently
        // dropped every trigger->condition edge — meaning a root condition
        // whose ONLY parent is the trigger (by far the most common case)
        // could never be reached by reachableFromTrigger(), since the
        // trigger never appeared as a key in the edge map at all.
        Set<String> validParentIds = new HashSet<>(allConditionIds);
        if (triggerId != null) validParentIds.add(triggerId);

        // Raw condition-to-condition (and trigger-to-condition) edges, built
        // from EVERY condition (enabled or disabled) as a target, so a
        // disabled node's own incoming AND outgoing edges are visible for
        // the bypass step below. This is the fix for the false-positive-
        // orphan bug: the old validator code skipped disabled nodes as edge
        // SOURCES entirely, severing anything chained behind them.
        Map<String, Set<String>> rawChildren = new HashMap<>();
        for (Automation.Condition c : safe) {
            if (c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                String parentId = ref.getNodeId();
                if (parentId == null || !validParentIds.contains(parentId)) continue;
                rawChildren.computeIfAbsent(parentId, k -> new LinkedHashSet<>())
                        .add(c.getNodeId());
            }
        }

        // Effective edges: every parent->child edge where the child is
        // disabled gets redirected to that disabled child's own children
        // (recursively, in case of consecutive disabled nodes), so disabled
        // nodes are bypassed rather than acting as dead ends.
        Map<String, Set<String>> effective = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : rawChildren.entrySet()) {
            String parent = entry.getKey();
            for (String child : entry.getValue()) {
                Set<String> resolved = resolveThroughDisabled(child, rawChildren, disabledIds, new HashSet<>());
                effective.computeIfAbsent(parent, k -> new LinkedHashSet<>()).addAll(resolved);
            }
        }

        // Root = enabled condition with NO enabled-condition parent in the
        // EFFECTIVE graph. Built directly from rawChildren parentage (not
        // from "is a value in effective") so a self-loop collapsed through a
        // disabled node can't accidentally remove a node from its own root
        // candidacy — see detectCycles()'s seeding note for why this
        // distinction matters.
        Set<String> hasEnabledConditionParent = new HashSet<>();
        for (Automation.Condition c : safe) {
            if (!c.isEnabled() || c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (ref.getNodeId() != null && enabledIds.contains(ref.getNodeId())) {
                    hasEnabledConditionParent.add(c.getNodeId());
                }
            }
        }
        Set<String> roots = enabledIds.stream()
                .filter(id -> !hasEnabledConditionParent.contains(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new Topology(effective, roots, enabledIds, disabledIds);
    }

    /**
     * If nodeId is disabled, returns the set of its nearest ENABLED
     * descendants (recursing through further disabled nodes, guarded against
     * cycles purely among disabled nodes). If nodeId is enabled, returns
     * {nodeId} unchanged.
     */
    private static Set<String> resolveThroughDisabled(String nodeId,
                                                      Map<String, Set<String>> rawChildren,
                                                      Set<String> disabledIds,
                                                      Set<String> guard) {
        if (!disabledIds.contains(nodeId)) return Set.of(nodeId);
        if (!guard.add(nodeId)) return Set.of(); // cycle through disabled nodes — bail out safely

        Set<String> resolved = new LinkedHashSet<>();
        for (String child : rawChildren.getOrDefault(nodeId, Set.of())) {
            resolved.addAll(resolveThroughDisabled(child, rawChildren, disabledIds, guard));
        }
        return resolved;
    }

    /**
     * Detects cycles in the EFFECTIVE graph. Returns the set of node ids
     * where a cycle was detected (a node whose DFS re-entered it while still
     * on the stack).
     * <p>
     * Seeding note: this deliberately does NOT seed only from `topology.roots()`.
     * A cycle that collapses through a disabled node into a direct self-loop
     * (c2 -> c1[disabled] -> c2 becomes c2 -> c2 in the effective graph) makes
     * c2 satisfy "has an enabled-condition parent" (itself!), which would
     * incorrectly exclude it from a roots-only seed set and let the cycle go
     * undetected entirely. Instead: DFS from the trigger first, then sweep
     * every remaining enabled condition not yet visited, guaranteeing every
     * node is the start of some DFS that can detect a cycle involving it.
     */
    public static Set<String> detectCycles(Topology topology, String triggerId) {
        Set<String> grey = new HashSet<>();
        Set<String> black = new HashSet<>();
        Set<String> cycleNodes = new LinkedHashSet<>();

        dfsForCycles(triggerId, topology, grey, black, cycleNodes);
        for (String nodeId : topology.enabledConditionIds()) {
            if (!black.contains(nodeId)) {
                dfsForCycles(nodeId, topology, grey, black, cycleNodes);
            }
        }
        return cycleNodes;
    }

    private static void dfsForCycles(String nodeId,
                                     Topology topology,
                                     Set<String> grey,
                                     Set<String> black,
                                     Set<String> cycleNodes) {
        if (black.contains(nodeId)) return;
        if (grey.contains(nodeId)) {
            cycleNodes.add(nodeId);
            return;
        }
        grey.add(nodeId);
        for (String child : topology.childrenOf(nodeId)) {
            dfsForCycles(child, topology, grey, black, cycleNodes);
        }
        grey.remove(nodeId);
        black.add(nodeId);
    }

    /**
     * Returns every enabled condition id reachable from the trigger via the
     * effective (disabled-bypassing) graph. Conditions NOT in this set are
     * genuine orphans — they have no path from the trigger at all, even
     * after bypassing disabled intermediates.
     */
    public static Set<String> reachableFromTrigger(Topology topology, String triggerId) {
        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(triggerId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!reachable.add(current)) continue;
            stack.addAll(topology.childrenOf(current));
        }
        return reachable;
    }

    /**
     * Returns every enabled condition id reachable purely from the STATIC
     * structure of an already-compiled ExecutionPlan (rootConditionNodeIds +
     * positiveChildNodeIds on each CompiledConditionNode), regardless of any
     * condition's current runtime value. Used by AutomationLivePublisher to
     * distinguish "this node wasn't walked because an ancestor is currently
     * false" (routine, statically reachable) from "this node has no path
     * from any root at all" (a genuine anomaly).
     */
    public static Set<String> staticallyReachableFromPlan(ExecutionPlan plan) {
        if (plan.getConditionTree() == null || plan.getRootConditionNodeIds() == null) {
            return Set.of();
        }
        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(plan.getRootConditionNodeIds());
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!reachable.add(current)) continue;
            ExecutionPlan.CompiledConditionNode node = nodeMap.get(current);
            if (node == null || node.getPositiveChildNodeIds() == null) continue;
            stack.addAll(node.getPositiveChildNodeIds());
        }
        return reachable;
    }
}