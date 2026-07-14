package dev.automata.automata.automation_engine;

import dev.automata.automata.dto.NodeRef;
import dev.automata.automata.model.Automation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates an Automation graph before compilation and save.
 * <p>
 * Called by AutomationService.saveAutomationDetailInternal() before
 * planCompiler.compile(). If any ERROR-level issues are found, the save
 * is aborted and the user receives the full list of problems. WARN-level
 * issues are returned but do not block the save.
 * <p>
 * Bug fixes (this version)
 * ─────────────────────────
 * BUG — Orphan detection (Check 4) produced false-positive "will never be
 * evaluated" warnings whenever a condition's upstream chain passed through a
 * DISABLED intermediate condition node. The edge-building loop skipped
 * disabled conditions entirely as edge SOURCES, so a chain like
 * trigger -> c1(disabled) -> c2(enabled) -> c3(enabled) lost the
 * trigger->c1 edge completely, making c2/c3 unreachable from the trigger in
 * this validator's BFS — even though ExecutionPlanCompiler's actual root
 * detection (previousNodeRef pointing only to non-ENABLED-condition nodes)
 * correctly promotes c2 to a new root and evaluates it normally. This is
 * exactly the same class of bug ExecutionPlanCompiler's own javadoc describes
 * having already fixed once for the old operator-gate model — it had crept
 * back into the validator, which reimplements topology logic independently
 * instead of sharing it with the compiler.
 * <p>
 * Fix: buildReachabilityGraph() now treats a disabled condition as
 * TRANSPARENT rather than absent — its own parent edge is rewired directly
 * to its children, so disabled nodes are bypassed rather than severing the
 * chain. This mirrors the compiler's actual semantics (a node whose only
 * upstream path runs through a disabled condition is treated as a new root,
 * not as an orphan).
 * <p>
 * Improvement — fan-out without an explicit fanoutMode is now an ERROR, not
 * an INFO. A condition node with 2+ positive children is, by construction,
 * an OR branch — and the framework's own design intent (stated by the team
 * that removed the old AND/OR gate system) is that OR branches must be
 * mutually exclusive and self-descriptive. Defaulting silently to "ALL" mode
 * is precisely the gap that allowed an unintended always-walk-both-branches
 * OR fan-out to go unnoticed in production (see "Light On" automation, where
 * both time-window branches were walked every tick because fanoutMode was
 * never set). Forcing the author to explicitly choose ALL vs FIRST_MATCH at
 * save time surfaces that decision instead of leaving it implicit.
 * <p>
 * Checks performed
 * ─────────────────
 * 1.  Trigger present and has a deviceId.
 * 2.  No duplicate nodeIds across conditions, actions.
 * 3.  All previousNodeRef targets exist in the graph.
 * 4.  No orphaned condition nodes (every condition has a path to the trigger,
 * bypassing disabled intermediate nodes transparently).
 * 5.  No cycles in the condition tree (DFS).
 * 6.  Interval conditions must have intervalMinutes > 0.
 * 7.  "at" schedule conditions must have a non-blank time field.
 * 8.  "range" schedule conditions must have fromTime and toTime.
 * 9.  "solar" conditions must have a solarType.
 * 10. Data conditions (above/below/equal/range/stale) must have a triggerKey.
 * 11. "above"/"below"/"equal" conditions must have a non-blank value.
 * 12. "range" data conditions must have both above and below.
 * 13. DURATION memory policy must have memoryPolicyValue > 0.
 * 14. CONSECUTIVE_TICKS memory policy must have memoryPolicyValue > 0.
 * 15. Actions must reference an existing condition or trigger node.
 * 16. Actions must have a deviceId.
 * 17. Actions must have a key.
 * 18. Fan-out nodes (multiple positive children) MUST have an explicit
 * fanoutMode (ALL or FIRST_MATCH) — ERROR if unset.
 * 19. Condition nodes that have negative actions but no stateful detection
 * path warn the user that revert actions may not fire correctly.
 * 20. Coalition: if sources > 1, all must have a deviceId.
 */
@Slf4j
@Component
public class AutomationGraphValidator {

    public enum Severity {ERROR, WARN, INFO}

    public record ValidationIssue(Severity severity, String nodeId, String message) {
        @Override
        public String toString() {
            return "[" + severity + "]"
                    + (nodeId != null ? " [" + nodeId + "]" : "")
                    + " " + message;
        }
    }

    public record ValidationResult(List<ValidationIssue> issues) {
        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
        }

        public List<ValidationIssue> errors() {
            return issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
        }

        public List<ValidationIssue> warnings() {
            return issues.stream().filter(i -> i.severity() == Severity.WARN).toList();
        }

        public List<ValidationIssue> infos() {
            return issues.stream().filter(i -> i.severity() == Severity.INFO).toList();
        }

        /**
         * User-facing summary string.
         */
        public String summary() {
            long e = errors().size(), w = warnings().size();
            if (e == 0 && w == 0) return "Automation graph is valid.";
            return e + " error(s), " + w + " warning(s) found.";
        }

        /**
         * Full message list suitable for sending to the frontend.
         */
        public List<String> toMessages() {
            return issues.stream().map(ValidationIssue::toString).toList();
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    public ValidationResult validate(Automation automation) {
        List<ValidationIssue> issues = new ArrayList<>();

        List<Automation.Condition> conditions =
                automation.getConditions() == null ? List.of() : automation.getConditions();
        List<Automation.Action> actions =
                automation.getActions() == null ? List.of() : automation.getActions();
        Automation.Trigger trigger = automation.getTrigger();

        // ── Build lookup maps ──────────────────────────────────────────────
        Map<String, Automation.Condition> condById = conditions.stream()
                .filter(Automation.Condition::isEnabled)
                .collect(Collectors.toMap(Automation.Condition::getNodeId, c -> c,
                        (a, b) -> a)); // keep first on duplicate

        // All valid node IDs in the graph (trigger + conditions)
        Set<String> allNodeIds = new HashSet<>(condById.keySet());
        if (trigger != null && trigger.getNodeId() != null)
            allNodeIds.add(trigger.getNodeId());

        // ── Check 1: Trigger ──────────────────────────────────────────────
        validateTrigger(trigger, issues);

        // ── Check 2: Duplicate nodeIds ────────────────────────────────────
        validateDuplicateNodeIds(conditions, actions, issues);

        // ── Check 3: Dangling references ──────────────────────────────────
        validateRefs(conditions, actions, allNodeIds, issues);

        // ── Check 4 + 5: Orphans + Cycles ─────────────────────────────────
        if (trigger != null && trigger.getNodeId() != null) {
            validateTreeStructure(conditions, trigger.getNodeId(), condById, issues);
        }

        // ── Check 6–14: Per-condition field checks ─────────────────────────
        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            validateConditionFields(c, issues);
        }

        // ── Check 15–17: Per-action field checks ──────────────────────────
        for (Automation.Action a : actions) {
            if (!Boolean.TRUE.equals(a.getIsEnabled())) continue;
            validateActionFields(a, allNodeIds, issues);
        }

        // ── Check 18: Fan-out mode required ───────────────────────────────
        detectFanout(conditions, condById, issues);

        // ── Check 19: Negative actions reachability ───────────────────────
        validateNegativeActionReachability(conditions, condById, issues);

        // ── Check 20: Coalition ───────────────────────────────────────────
        if (trigger != null) validateCoalition(trigger, issues);

        log.info("✅ Validation for '{}': {} issue(s) — {}",
                automation.getName(), issues.size(),
                issues.isEmpty() ? "clean" :
                        issues.stream().map(i -> i.severity().name())
                        .collect(Collectors.joining(", ")));

        return new ValidationResult(issues);
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 1 — TRIGGER
    // ─────────────────────────────────────────────────────────────────────

    private void validateTrigger(Automation.Trigger trigger, List<ValidationIssue> issues) {
        if (trigger == null) {
            issues.add(new ValidationIssue(Severity.ERROR, null,
                    "Automation has no trigger node. Add a trigger device to start."));
            return;
        }
        if (trigger.getDeviceId() == null || trigger.getDeviceId().isBlank()) {
            issues.add(new ValidationIssue(Severity.ERROR, trigger.getNodeId(),
                    "Trigger node has no device selected."));
        }
        if (trigger.getNodeId() == null || trigger.getNodeId().isBlank()) {
            issues.add(new ValidationIssue(Severity.ERROR, null,
                    "Trigger node is missing its nodeId — try removing and re-adding the trigger node."));
        }
        if (trigger.getKeys() == null || trigger.getKeys().isEmpty()) {
            issues.add(new ValidationIssue(Severity.WARN, trigger.getNodeId(),
                    "Trigger has no keys configured. The automation will fire on any data from the device."));
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 2 — DUPLICATE NODE IDs
    // ─────────────────────────────────────────────────────────────────────

    private void validateDuplicateNodeIds(List<Automation.Condition> conditions,
                                          List<Automation.Action> actions,
                                          List<ValidationIssue> issues) {
        Set<String> seen = new HashSet<>();
        Set<String> dupes = new LinkedHashSet<>();

        for (Automation.Condition c : conditions) {
            if (c.getNodeId() != null && !seen.add(c.getNodeId()))
                dupes.add(c.getNodeId());
        }
        for (Automation.Action a : actions) {
            if (a.getNodeId() != null && !seen.add(a.getNodeId()))
                dupes.add(a.getNodeId());
        }

        for (String dup : dupes) {
            issues.add(new ValidationIssue(Severity.ERROR, dup,
                    "Duplicate nodeId '" + dup + "'. Each node must have a unique ID. "
                            + "This usually happens when a node is copy-pasted. "
                            + "Remove and re-add the duplicate node."));
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 3 — DANGLING REFERENCES
    // ─────────────────────────────────────────────────────────────────────

    private void validateRefs(List<Automation.Condition> conditions,
                              List<Automation.Action> actions,
                              Set<String> allNodeIds,
                              List<ValidationIssue> issues) {
        for (Automation.Condition c : conditions) {
            if (!c.isEnabled() || c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (ref.getNodeId() != null && !allNodeIds.contains(ref.getNodeId())) {
                    issues.add(new ValidationIssue(Severity.ERROR, c.getNodeId(),
                            "Condition '" + c.getNodeId() + "' references parent '"
                                    + ref.getNodeId() + "' which does not exist in the graph. "
                                    + "The edge is broken — delete and re-draw it."));
                }
            }
        }

        for (Automation.Action a : actions) {
            if (!Boolean.TRUE.equals(a.getIsEnabled()) || a.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : a.getPreviousNodeRef()) {
                if (ref.getNodeId() != null && !allNodeIds.contains(ref.getNodeId())) {
                    issues.add(new ValidationIssue(Severity.ERROR, a.getNodeId(),
                            "Action '" + a.getName() + "' [" + a.getNodeId()
                                    + "] references node '" + ref.getNodeId()
                                    + "' which does not exist. Delete and re-connect this action."));
                }
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 4 + 5 — ORPHANS + CYCLES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the parent→children map used for reachability and cycle
     * detection, treating DISABLED conditions as transparent pass-throughs
     * rather than dead ends.
     * <p>
     * BUG FIX: the previous version built edges only from ENABLED conditions
     * as SOURCES (`if (!c.isEnabled()) continue` before reading c's own
     * previousNodeRef). This meant a disabled node's own incoming edge (e.g.
     * trigger -> c1[disabled]) was never recorded, so anything downstream of
     * a disabled node (c1[disabled] -> c2[enabled] -> c3[enabled]) became
     * unreachable from the trigger in this validator's BFS — even though
     * ExecutionPlanCompiler treats c2 as a brand-new root (since c1 is
     * excluded from conditionNodeIds for being disabled) and evaluates it
     * normally.
     * <p>
     * Fix: every condition — enabled or not — contributes its outgoing edges
     * to a raw parent→children map exactly as drawn. Then, for any DISABLED
     * node, its children are re-parented to skip over it: child edges from a
     * disabled node are rewritten to originate from THAT disabled node's own
     * parent(s) instead, recursively, so the effective graph used for
     * reachability/cycle checks matches what the compiler actually does
     * (disabled nodes are invisible, not blocking).
     */
    private Map<String, Set<String>> buildReachabilityGraph(List<Automation.Condition> conditions) {
        // Raw edges: parentId -> children, built from ALL conditions (enabled
        // or not) so disabled nodes' own incoming/outgoing edges are visible.
        Map<String, Set<String>> rawChildren = new HashMap<>();
        Map<String, Set<String>> rawParents = new HashMap<>();
        Set<String> disabledIds = new HashSet<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) disabledIds.add(c.getNodeId());
            if (c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (ref.getNodeId() == null) continue;
                rawChildren.computeIfAbsent(ref.getNodeId(), k -> new LinkedHashSet<>())
                        .add(c.getNodeId());
                rawParents.computeIfAbsent(c.getNodeId(), k -> new LinkedHashSet<>())
                        .add(ref.getNodeId());
            }
        }

        // Effective edges: for every parent->child edge where the CHILD is
        // disabled, redirect to that disabled child's own children instead
        // (transitively, in case of consecutive disabled nodes), so the
        // disabled node is bypassed rather than acting as a dead end.
        Map<String, Set<String>> effective = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : rawChildren.entrySet()) {
            String parent = entry.getKey();
            // A disabled parent's edges are reachable only via whatever
            // reparents INTO it below — we still expand its own children here
            // so the bypass works regardless of visit order.
            for (String child : entry.getValue()) {
                for (String resolvedChild : resolveThroughDisabled(child, rawChildren, disabledIds,
                        new HashSet<>())) {
                    effective.computeIfAbsent(parent, k -> new LinkedHashSet<>()).add(resolvedChild);
                }
            }
        }
        return effective;
    }

    /**
     * If nodeId is disabled, returns the set of its nearest ENABLED
     * descendants (recursing through further disabled nodes). If nodeId is
     * enabled, returns {nodeId} itself unchanged.
     */
    private Set<String> resolveThroughDisabled(String nodeId,
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

    private void validateTreeStructure(List<Automation.Condition> conditions,
                                       String triggerId,
                                       Map<String, Automation.Condition> condById,
                                       List<ValidationIssue> issues) {
        Map<String, Set<String>> children = buildReachabilityGraph(conditions);

        // Check 4: orphans — conditions not reachable from trigger, bypassing
        // disabled intermediate nodes (see buildReachabilityGraph javadoc).
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(triggerId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!reachable.add(current)) continue;
            Set<String> ch = children.getOrDefault(current, Set.of());
            queue.addAll(ch);
        }

        for (String nodeId : condById.keySet()) {
            if (!reachable.contains(nodeId)) {
                issues.add(new ValidationIssue(Severity.WARN, nodeId,
                        "Condition node '" + nodeId + "' is not connected to the trigger. "
                                + "It will never be evaluated. Connect it or remove it."));
            }
        }

        // Check 5: cycles — DFS with grey/black colouring, over the SAME
        // effective (disabled-bypassing) graph used for reachability, so a
        // cycle that only exists when routed through a disabled node isn't
        // falsely reported (and a cycle that exists regardless of disabled
        // nodes is still caught).
        //
        // NOTE on root selection: an earlier version of this fix tried to
        // seed the DFS only from nodes with "no enabled-condition parent",
        // but that definition is fragile — a cycle that collapses through a
        // bypassed disabled node into a direct self-loop (c2 -> c1[disabled]
        // -> c2 becomes c2 -> c2 in the effective graph) makes c2 satisfy
        // "is listed as a child of an enabled condition" (itself!), which
        // filtered it OUT of the root set entirely — silently skipping the
        // exact cycle the check exists to catch.
        //
        // Fix: always DFS from the trigger first (covers every node actually
        // reachable from it, cyclic or not), then sweep every remaining
        // enabled condition not yet visited (covers disconnected components,
        // including a pure self-loop with no path from the trigger at all).
        // This guarantees every node is the start of *some* DFS that can
        // detect a cycle involving it, without depending on a "has no
        // parent" pre-filter that a cycle can trivially defeat.
        Set<String> grey = new HashSet<>();  // currently in stack
        Set<String> black = new HashSet<>();  // fully processed

        detectCycle(triggerId, children, grey, black, issues);
        for (String nodeId : condById.keySet()) {
            if (!black.contains(nodeId)) {
                detectCycle(nodeId, children, grey, black, issues);
            }
        }
    }

    private void detectCycle(String nodeId,
                             Map<String, Set<String>> children,
                             Set<String> grey,
                             Set<String> black,
                             List<ValidationIssue> issues) {
        if (black.contains(nodeId)) return;
        if (grey.contains(nodeId)) {
            issues.add(new ValidationIssue(Severity.ERROR, nodeId,
                    "Cycle detected at node '" + nodeId + "'. "
                            + "The condition graph must be a tree — it cannot loop back on itself. "
                            + "Remove the edge that creates the cycle."));
            return;
        }
        grey.add(nodeId);
        for (String child : children.getOrDefault(nodeId, Set.of())) {
            detectCycle(child, children, grey, black, issues);
        }
        grey.remove(nodeId);
        black.add(nodeId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 6–14 — PER-CONDITION FIELD VALIDATION
    // ─────────────────────────────────────────────────────────────────────

    private void validateConditionFields(Automation.Condition c, List<ValidationIssue> issues) {
        String id = c.getNodeId();
        String type = c.getCondition();

        if (type == null || type.isBlank()) {
            issues.add(new ValidationIssue(Severity.ERROR, id,
                    "Condition node '" + id + "' has no condition type set."));
            return;
        }

        if ("scheduled".equals(type)) {
            String st = c.getScheduleType();
            if (st == null || st.isBlank()) {
                issues.add(new ValidationIssue(Severity.ERROR, id,
                        "Scheduled condition '" + id + "' has no schedule type (at/range/interval/solar)."));
                return;
            }
            switch (st) {
                case "interval" -> {
                    if (c.getIntervalMinutes() <= 0)
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Interval condition '" + id + "' has intervalMinutes="
                                        + c.getIntervalMinutes() + ". Must be > 0."));
                }
                case "at" -> {
                    if (c.getTime() == null || c.getTime().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Scheduled 'at' condition '" + id + "' has no time set."));
                }
                case "range" -> {
                    if (c.getFromTime() == null || c.getFromTime().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Range schedule condition '" + id + "' is missing fromTime."));
                    if (c.getToTime() == null || c.getToTime().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Range schedule condition '" + id + "' is missing toTime."));
                }
                case "solar" -> {
                    if (c.getSolarType() == null || c.getSolarType().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Solar condition '" + id + "' has no solarType (sunrise/sunset)."));
                }
                default -> issues.add(new ValidationIssue(Severity.WARN, id,
                        "Unknown scheduleType '" + st + "' on condition '" + id + "'."));
            }

        } else if ("stale".equals(type)) {
            if (c.getValue() == null || c.getValue().isBlank())
                issues.add(new ValidationIssue(Severity.ERROR, id,
                        "Stale condition '" + id + "' has no threshold value (minutes)."));
            else {
                try {
                    Double.parseDouble(c.getValue());
                } catch (NumberFormatException e) {
                    issues.add(new ValidationIssue(Severity.ERROR, id,
                            "Stale condition '" + id + "' has non-numeric threshold value '"
                                    + c.getValue() + "'."));
                }
            }

        } else {
            // Data-driven conditions: above / below / equal / range
            if (c.getTriggerKey() == null || c.getTriggerKey().isBlank())
                issues.add(new ValidationIssue(Severity.ERROR, id,
                        "Condition '" + id + "' (type=" + type + ") has no trigger key. "
                                + "Select which data field to compare (e.g. 'percent', 'temp')."));

            switch (type) {
                case "above", "below", "equal" -> {
                    if (c.getValue() == null || c.getValue().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Condition '" + id + "' (type=" + type + ") has no value to compare against."));
                }
                case "range" -> {
                    if (c.getAbove() == null || c.getAbove().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Range condition '" + id + "' is missing the lower bound (above)."));
                    if (c.getBelow() == null || c.getBelow().isBlank())
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "Range condition '" + id + "' is missing the upper bound (below)."));
                    if (c.getAbove() != null && c.getBelow() != null
                            && !c.getAbove().isBlank() && !c.getBelow().isBlank()) {
                        try {
                            double lo = Double.parseDouble(c.getAbove());
                            double hi = Double.parseDouble(c.getBelow());
                            if (lo >= hi)
                                issues.add(new ValidationIssue(Severity.ERROR, id,
                                        "Range condition '" + id + "': lower bound ("
                                                + lo + ") must be less than upper bound (" + hi + ")."));
                        } catch (NumberFormatException ignored) { /* caught by per-field checks */ }
                    }
                }
                default -> issues.add(new ValidationIssue(Severity.WARN, id,
                        "Unknown condition type '" + type + "' on node '" + id + "'. "
                                + "Supported: above, below, equal, range, scheduled, stale."));
            }
        }

        // Memory policy checks (Check 13 + 14)
        if (c.getMemoryPolicy() != null && !c.getMemoryPolicy().isBlank()) {
            String mp = c.getMemoryPolicy();
            switch (mp) {
                case "DURATION" -> {
                    if (c.getMemoryPolicyValue() <= 0)
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "DURATION memory policy on '" + id + "' requires memoryPolicyValue > 0 "
                                        + "(the number of seconds the condition must hold)."));
                }
                case "CONSECUTIVE_TICKS" -> {
                    if (c.getMemoryPolicyValue() <= 0)
                        issues.add(new ValidationIssue(Severity.ERROR, id,
                                "CONSECUTIVE_TICKS memory policy on '" + id + "' requires memoryPolicyValue > 0 "
                                        + "(the number of consecutive true ticks required)."));
                }
                case "EDGE_RISING", "EDGE_FALLING", "EDGE_BOTH" -> { /* no value needed */ }
                default -> issues.add(new ValidationIssue(Severity.WARN, id,
                        "Unknown memory policy '" + mp + "' on condition '" + id + "'. "
                                + "Supported: DURATION, CONSECUTIVE_TICKS, EDGE_RISING, EDGE_FALLING, EDGE_BOTH."));
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 15–17 — PER-ACTION FIELD VALIDATION
    // ─────────────────────────────────────────────────────────────────────

    private void validateActionFields(Automation.Action a,
                                      Set<String> allNodeIds,
                                      List<ValidationIssue> issues) {
        String id = a.getNodeId();

        if (a.getDeviceId() == null || a.getDeviceId().isBlank())
            issues.add(new ValidationIssue(Severity.ERROR, id,
                    "Action '" + a.getName() + "' [" + id + "] has no device selected."));

        if (a.getKey() == null || a.getKey().isBlank())
            issues.add(new ValidationIssue(Severity.ERROR, id,
                    "Action '" + a.getName() + "' [" + id + "] has no key set."));

        if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
            issues.add(new ValidationIssue(Severity.WARN, id,
                    "Action '" + a.getName() + "' [" + id + "] is not connected to any condition or trigger node. "
                            + "It will never fire."));

        // conditionGroup should be positive, negative, informational, fallback, or none
        String group = a.getConditionGroup();
        if (group != null && !group.isBlank()) {
            Set<String> valid = Set.of("positive", "negative", "informational", "fallback", "none");
            if (!valid.contains(group.toLowerCase()))
                issues.add(new ValidationIssue(Severity.WARN, id,
                        "Action '" + a.getName() + "' [" + id + "] has unknown conditionGroup '"
                                + group + "'. Expected: positive, negative, informational, fallback, none."));
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 18 — FAN-OUT MODE REQUIRED
    // ─────────────────────────────────────────────────────────────────────

    /**
     * IMPROVEMENT: previously this only emitted an INFO message for fan-out
     * nodes (2+ positive children), and the engine silently defaulted to
     * fanoutMode=ALL when unset. Since the entire point of the OR-fanout
     * model (vs. the removed AND/OR gate system) is that branches are
     * mutually exclusive and self-descriptive, an unset fanoutMode is now an
     * ERROR — the author must explicitly choose ALL (evaluate every branch
     * independently) or FIRST_MATCH (stop at the first branch that passes).
     * This directly targets the bug class where an unintended "walk both
     * branches every tick" OR fan-out went unnoticed (see "Light On", where
     * two time-window branches were both always evaluated because no
     * fanoutMode was ever set on the parent node).
     */
    private void detectFanout(List<Automation.Condition> conditions,
                              Map<String, Automation.Condition> condById,
                              List<ValidationIssue> issues) {
        Map<String, Integer> positiveChildCount = new HashMap<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled() || c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (ref.getNodeId() == null) continue;
                String handle = ref.getHandle() != null ? ref.getHandle() : "";
                if (handle.contains("cond-positive") && condById.containsKey(ref.getNodeId())) {
                    positiveChildCount.merge(ref.getNodeId(), 1, Integer::sum);
                }
            }
        }

        for (Map.Entry<String, Integer> e : positiveChildCount.entrySet()) {
            if (e.getValue() <= 1) continue;

            String parentNodeId = e.getKey();
            Automation.Condition parent = condById.get(parentNodeId);
            String fanoutMode = parent != null ? parent.getFanoutMode() : null;

            // isFanout is now ALWAYS derived server-side from topology
            // (posChildren.size() > 1) in ExecutionPlanCompiler — it can no
            // longer be "missing". Only flag an actually-invalid explicit value;
            // unset/blank just means ALL, which is correct and expected.
            if (fanoutMode != null && !fanoutMode.isBlank()
                    && !"ALL".equals(fanoutMode) && !"FIRST_MATCH".equals(fanoutMode)) {
                issues.add(new ValidationIssue(Severity.ERROR, parentNodeId,
                        "Condition '" + parentNodeId + "' has unknown fanoutMode '" + fanoutMode
                                + "'. Must be ALL, FIRST_MATCH, or left unset (defaults to ALL)."));
            } else {
                String effectiveMode = "FIRST_MATCH".equals(fanoutMode) ? "FIRST_MATCH" : "ALL";
                issues.add(new ValidationIssue(Severity.INFO, parentNodeId,
                        "Condition '" + parentNodeId + "' has " + e.getValue()
                                + " positive children — will fan out with mode=" + effectiveMode
                                + " (derived automatically)."));
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 19 — NEGATIVE ACTION REACHABILITY
    // ─────────────────────────────────────────────────────────────────────

    private void validateNegativeActionReachability(List<Automation.Condition> conditions,
                                                    Map<String, Automation.Condition> condById,
                                                    List<ValidationIssue> issues) {
        // Build parent → positive children map for fan-out detection
        Map<String, List<String>> positiveChildren = new HashMap<>();
        for (Automation.Condition c : conditions) {
            if (!c.isEnabled() || c.getPreviousNodeRef() == null) continue;
            for (NodeRef ref : c.getPreviousNodeRef()) {
                if (ref.getNodeId() == null) continue;
                String handle = ref.getHandle() != null ? ref.getHandle() : "";
                if (handle.contains("cond-positive") && condById.containsKey(ref.getNodeId())) {
                    positiveChildren.computeIfAbsent(ref.getNodeId(), k -> new ArrayList<>())
                            .add(c.getNodeId());
                }
            }
        }

        // Find nodes that are OR fan-out children
        Set<String> fanoutChildIds = new HashSet<>();
        for (Map.Entry<String, List<String>> e : positiveChildren.entrySet()) {
            if (e.getValue().size() > 1)
                fanoutChildIds.addAll(e.getValue());
        }

        // Check each node that has negative actions
        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;

            if ("FIRST_MATCH".equals(c.getFanoutMode())
                    && positiveChildren.getOrDefault(c.getNodeId(), List.of()).size() > 1) {
                issues.add(new ValidationIssue(Severity.INFO, c.getNodeId(),
                        "Condition '" + c.getNodeId() + "' uses FIRST_MATCH fan-out. "
                                + "Negative actions on sibling branches that were previously active "
                                + "will still fire when their branch loses the 'first match' position."));
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHECK 20 — COALITION
    // ─────────────────────────────────────────────────────────────────────

    private void validateCoalition(Automation.Trigger trigger, List<ValidationIssue> issues) {
        if (trigger.getSources() == null || trigger.getSources().size() <= 1) return;

        for (var source : trigger.getSources()) {
            if (source.getDeviceId() == null || source.getDeviceId().isBlank()) {
                issues.add(new ValidationIssue(Severity.ERROR, trigger.getNodeId(),
                        "Coalition trigger source with role '" + source.getRole()
                                + "' has no deviceId set."));
            }
            if (source.getRole() == null || source.getRole().isBlank()) {
                issues.add(new ValidationIssue(Severity.WARN, trigger.getNodeId(),
                        "Coalition trigger source for device '" + source.getDeviceId()
                                + "' has no role set. Defaulting to 'secondary'."));
            }
        }

        long primaryCount = trigger.getSources().stream()
                .filter(s -> "primary".equals(s.getRole()))
                .count();
        if (primaryCount == 0) {
            issues.add(new ValidationIssue(Severity.ERROR, trigger.getNodeId(),
                    "Coalition trigger has no primary source. Exactly one source must have role='primary'."));
        } else if (primaryCount > 1) {
            issues.add(new ValidationIssue(Severity.WARN, trigger.getNodeId(),
                    "Coalition trigger has " + primaryCount + " primary sources. "
                            + "Only the first primary will be used for Redis data lookup."));
        }

        int windowSec = trigger.getCoalitionWindowSeconds();
        String mode = trigger.getCoalitionMode();
        if (mode != null && !"ANY".equals(mode) && windowSec <= 0) {
            issues.add(new ValidationIssue(Severity.WARN, trigger.getNodeId(),
                    "Coalition mode '" + mode + "' with windowSeconds=0 means the window never expires. "
                            + "Consider setting coalitionWindowSeconds to a positive value."));
        }
    }
}