package dev.automata.automata.controller;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import dev.automata.automata.v2.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Point 9 — State Inspection and Manual Override API.
 * <p>
 * GET  /api/automations/{id}/state
 * Returns the full runtime state of an automation: current state machine
 * position, per-node active flags, branch states, condition memory summaries,
 * coalition last-fired timestamps, and the last evaluation snapshot.
 * Zero risk — read-only Redis GET, no state mutation.
 * <p>
 * GET  /api/automations/{id}/plan
 * Returns the compiled ExecutionPlan for inspection. Useful for verifying
 * that the compiler produced the expected condition tree and branch list.
 * <p>
 * POST /api/automations/{id}/override
 * Manually advance or reset the state machine.
 * Body: { "action": "RESET" | "FORCE_ACTIVE" | "FORCE_IDLE" | "RESET_MEMORY" }
 * RESET:        Writes AutomationRuntimeState.idle() — full reset including
 * condition memories and coalition tracking.
 * FORCE_ACTIVE: Sets topLevelState="ACTIVE" and all stateful nodes active.
 * FORCE_IDLE:   Sets topLevelState="IDLE" and all nodes/branches inactive.
 * RESET_MEMORY: Clears condition memories only — leaves state machine position
 * intact. Useful for restarting DURATION timers without a full reset.
 * INJECT:       (reserved) — triggers a manual evaluation with a custom payload.
 * Not yet implemented; returns 501.
 * <p>
 * POST /api/automations/{id}/snooze
 * Body: { "minutes": 30 }
 * Arms the SNOOZE key — orchestrator will skip evaluation for this automation
 * for the given duration. Delegates to AutomationStateStore.snooze().
 * <p>
 * DELETE /api/automations/{id}/snooze
 * Clears the snooze key immediately.
 * <p>
 * All endpoints require the automation to have a compiled plan in the PlanCache.
 * If the plan is missing (e.g. compilation failed on last save), the endpoints
 * return 404 with a descriptive message rather than an empty 200.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/automations")
@RequiredArgsConstructor
public class AutomationInspectionController {

    private final AutomationStateStore stateStore;
    private final PlanCache planCache;
    private final AutomationEvaluator evaluator;


    // ─────────────────────────────────────────────────────────────────────
    // GET /api/automations/{id}/state
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/state")
    public ResponseEntity<AutomationStateResponse> getState(@PathVariable String id) {
        ExecutionPlan plan = planCache.get(id);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }

        AutomationRuntimeState state = stateStore.read(id);
        AutomationStateResponse response = buildStateResponse(id, plan, state);
        return ResponseEntity.ok(response);
    }


    // ─────────────────────────────────────────────────────────────────────
    // GET /api/automations/{id}/plan
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/plan")
    public ResponseEntity<PlanSummaryResponse> getPlan(@PathVariable String id) {
        ExecutionPlan plan = planCache.get(id);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildPlanSummary(plan));
    }


    // ─────────────────────────────────────────────────────────────────────
    // POST /api/automations/{id}/override
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/override")
    public ResponseEntity<OverrideResponse> override(
            @PathVariable String id,
            @RequestBody OverrideRequest request) {

        ExecutionPlan plan = planCache.get(id);
        if (plan == null) {
            return ResponseEntity.notFound().build();
        }

        String action = request.getAction();
        if (action == null || action.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(OverrideResponse.error("action is required"));
        }

        return switch (action.toUpperCase()) {
            case "RESET" -> {
                stateStore.forceWrite(id, AutomationRuntimeState.idle());
                log.info("🔄 Override RESET applied to automation '{}'", id);
                yield ResponseEntity.ok(OverrideResponse.ok("State reset to IDLE"));
            }
            case "FORCE_ACTIVE" -> {
                AutomationRuntimeState next = AutomationRuntimeState.idle();
                next.setTopLevelState("ACTIVE");
                if (plan.getConditionTree() != null)
                    plan.getConditionTree().stream()
                            .filter(ExecutionPlan.CompiledConditionNode::isStateful)
                            .forEach(n -> next.setNodeActive(n.getNodeId(), true));
                stateStore.forceWrite(id, next);
                log.info("🔄 Override FORCE_ACTIVE applied to automation '{}'", id);
                yield ResponseEntity.ok(OverrideResponse.ok("State forced to ACTIVE"));
            }
            case "FORCE_IDLE" -> {
                AutomationRuntimeState next = AutomationRuntimeState.idle();
                // Explicitly clear branch states
                if (plan.getBranches() != null)
                    plan.getBranches().forEach(b -> next.setBranchState(b.getGateNodeId(), "IDLE"));
                stateStore.forceWrite(id, next);
                log.info("🔄 Override FORCE_IDLE applied to automation '{}'", id);
                yield ResponseEntity.ok(OverrideResponse.ok("State forced to IDLE"));
            }
            case "RESET_MEMORY" -> {
                AutomationRuntimeState current = stateStore.read(id);
                // Clear only condition memories; leave state machine position intact
                current.getConditionMemories().clear();
                current.setSequenceProgress(0);
                stateStore.forceWrite(id, current);
                log.info("🔄 Override RESET_MEMORY applied to automation '{}'", id);
                yield ResponseEntity.ok(OverrideResponse.ok("Condition memories cleared"));
            }
            case "RESET_COALITION" -> {
                AutomationRuntimeState current = stateStore.read(id);
                current.resetCoalitionState();
                stateStore.forceWrite(id, current);
                log.info("🔄 Override RESET_COALITION applied to automation '{}'", id);
                yield ResponseEntity.ok(OverrideResponse.ok("Coalition state reset"));
            }
            case "INJECT" -> ResponseEntity.status(501)
                    .body(OverrideResponse.error(
                            "INJECT not yet implemented — use the live device event path"));
            default -> ResponseEntity.badRequest()
                    .body(OverrideResponse.error(
                            "Unknown action '" + action
                                    + "'. Valid: RESET, FORCE_ACTIVE, FORCE_IDLE, RESET_MEMORY, RESET_COALITION"));
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // POST /api/automations/{id}/snooze
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/snooze")
    public ResponseEntity<OverrideResponse> snooze(
            @PathVariable String id,
            @RequestBody SnoozeRequest request) {

        if (planCache.get(id) == null) return ResponseEntity.notFound().build();

        int minutes = request.getMinutes() > 0 ? request.getMinutes() : 30;
        stateStore.snooze(id, minutes * 60L);
        log.info("😴 Automation '{}' snoozed for {}min", id, minutes);
        return ResponseEntity.ok(OverrideResponse.ok("Snoozed for " + minutes + " minutes"));
    }

    @DeleteMapping("/{id}/snooze")
    public ResponseEntity<OverrideResponse> clearSnooze(@PathVariable String id) {
        if (planCache.get(id) == null) return ResponseEntity.notFound().build();
        stateStore.clearSnooze(id);
        log.info("⏰ Snooze cleared for automation '{}'", id);
        return ResponseEntity.ok(OverrideResponse.ok("Snooze cleared"));
    }


    // ─────────────────────────────────────────────────────────────────────
    // RESPONSE BUILDERS
    // ─────────────────────────────────────────────────────────────────────

    private AutomationStateResponse buildStateResponse(String id,
                                                       ExecutionPlan plan,
                                                       AutomationRuntimeState state) {
        // Condition node summaries
        List<NodeStateView> nodeViews = new ArrayList<>();
        if (plan.getConditionTree() != null) {
            for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
                boolean active = state.isNodeActive(node.getNodeId());
                ConditionMemory memory = state.getConditionMemory(node.getNodeId());
                String memorySummary = node.hasMemoryPolicy()
                        ? evaluator.summarizeMemory(node.getMemoryPolicy(), memory)
                        : null;

                // Last raw result from snapshot
                Boolean lastRawResult = null;
                if (state.getLastEvalSnapshot() != null
                        && state.getLastEvalSnapshot().getConditionResults() != null)
                    lastRawResult = state.getLastEvalSnapshot().getConditionResults()
                            .get(node.getNodeId());

                nodeViews.add(NodeStateView.builder()
                        .nodeId(node.getNodeId())
                        .conditionType(node.getCondition() != null
                                ? node.getCondition().getConditionType() : "unknown")
                        .triggerKey(node.getCondition() != null
                                ? node.getCondition().getTriggerKey() : null)
                        .stateful(node.isStateful())
                        .wasActive(active)
                        .hasMemoryPolicy(node.hasMemoryPolicy())
                        .memoryPolicyType(node.hasMemoryPolicy()
                                ? node.getMemoryPolicy().getType().name() : null)
                        .memorySummary(memorySummary)
                        .consecutiveTrueCount(memory.getConsecutiveTrueCount())
                        .firstTrueEpochMs(memory.getFirstTrueEpochMs())
                        .lastRawResult(lastRawResult)
                        .build());
            }
        }

        // Branch summaries
        List<BranchStateView> branchViews = new ArrayList<>();
        if (plan.getBranches() != null) {
            for (ExecutionPlan.CompiledBranch branch : plan.getBranches()) {
                String bState = state.getBranchStateStr(branch.getGateNodeId());
                branchViews.add(BranchStateView.builder()
                        .gateNodeId(branch.getGateNodeId())
                        .priority(branch.getPriority())
                        .logicType(branch.getLogicType())
                        .scheduleType(branch.getGateCondition() != null
                                ? branch.getGateCondition().getScheduleType() : null)
                        .state(bState)
                        .active("ACTIVE".equals(bState) || "HOLDING".equals(bState))
                        .build());
            }
        }

        // Coalition summary
        CoalitionStateView coalitionView = null;
        if (plan.hasCoalition()) {
            TriggerCoalition c = plan.getTriggerCoalition();
            List<MemberFiredView> memberViews = c.getMembers().stream()
                    .map(m -> {
                        long lastFired = state.getMemberLastFired(m.getDeviceId());
                        long agoSeconds = lastFired > 0
                                ? (System.currentTimeMillis() - lastFired) / 1000 : -1;
                        return MemberFiredView.builder()
                                .deviceId(m.getDeviceId())
                                .role(m.getRole())
                                .sequenceIndex(m.getSequenceIndex())
                                .lastFiredEpochMs(lastFired)
                                .secondsAgo(agoSeconds)
                                .withinWindow(lastFired > 0
                                        && agoSeconds < c.getWindowSeconds())
                                .build();
                    })
                    .collect(Collectors.toList());

            coalitionView = CoalitionStateView.builder()
                    .mode(c.getMode().name())
                    .windowSeconds(c.getWindowSeconds())
                    .sequenceProgress(state.getSequenceProgress())
                    .members(memberViews)
                    .build();
        }

        return AutomationStateResponse.builder()
                .automationId(id)
                .automationName(plan.getAutomationName())
                .schemaVersion(plan.getSchemaVersion())
                .compiledAt(plan.getCompiledAt())
                .topLevelState(state.getTopLevelState())
                .isTopLevelActive(state.isTopLevelActive())
                .hasBranches(plan.hasBranches())
                .hasCoalition(plan.hasCoalition())
                .conditionNodes(nodeViews)
                .branches(branchViews)
                .coalition(coalitionView)
                .lastExecutionTime(state.getLastExecutionTime())
                .lastEvalSnapshot(state.getLastEvalSnapshot())
                .build();
    }

    private PlanSummaryResponse buildPlanSummary(ExecutionPlan plan) {
        List<Map<String, Object>> condNodes = new ArrayList<>();
        if (plan.getConditionTree() != null) {
            for (ExecutionPlan.CompiledConditionNode n : plan.getConditionTree()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nodeId", n.getNodeId());
                m.put("stateful", n.isStateful());
                m.put("positiveChildNodeId", n.getPositiveChildNodeIds());
                m.put("negativeChildNodeId", n.getNegativeChildNodeIds());
                m.put("positiveActionsCount",
                        n.getPositiveActions() != null ? n.getPositiveActions().size() : 0);
                m.put("negativeActionsCount",
                        n.getNegativeActions() != null ? n.getNegativeActions().size() : 0);
                if (n.getCondition() != null) {
                    m.put("conditionType", n.getCondition().getConditionType());
                    m.put("triggerKey", n.getCondition().getTriggerKey());
                    m.put("value", n.getCondition().getValue());
                    m.put("deviceId", n.getCondition().getDeviceId());
                }
                if (n.hasMemoryPolicy()) {
                    m.put("memoryPolicy", Map.of(
                            "type", n.getMemoryPolicy().getType().name(),
                            "requiredDurationSeconds", n.getMemoryPolicy().getRequiredDurationSeconds(),
                            "requiredTicks", n.getMemoryPolicy().getRequiredTicks()
                    ));
                }
                condNodes.add(m);
            }
        }

        List<Map<String, Object>> branchList = new ArrayList<>();
        if (plan.getBranches() != null) {
            for (ExecutionPlan.CompiledBranch b : plan.getBranches()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gateNodeId", b.getGateNodeId());
                m.put("priority", b.getPriority());
                m.put("logicType", b.getLogicType());
                m.put("positiveActionsCount",
                        b.getPositiveActions() != null ? b.getPositiveActions().size() : 0);
                m.put("negativeActionsCount",
                        b.getNegativeActions() != null ? b.getNegativeActions().size() : 0);
                if (b.getGateCondition() != null) {
                    m.put("scheduleType", b.getGateCondition().getScheduleType());
                    m.put("intervalMinutes", b.getGateCondition().getIntervalMinutes());
                    m.put("durationMinutes", b.getGateCondition().getDurationMinutes());
                    m.put("fromTime", b.getGateCondition().getFromTime());
                    m.put("toTime", b.getGateCondition().getToTime());
                }
                branchList.add(m);
            }
        }

        return PlanSummaryResponse.builder()
                .automationId(plan.getAutomationId())
                .automationName(plan.getAutomationName())
                .schemaVersion(plan.getSchemaVersion())
                .compiledAt(plan.getCompiledAt())
                .triggerDeviceId(plan.getTriggerDeviceId())
                .rootConditionNodeIds(plan.getRootConditionNodeIds())
                .conditionNodes(condNodes)
                .branches(branchList)
                .hasCoalition(plan.hasCoalition())
                .coalitionMode(plan.hasCoalition()
                        ? plan.getTriggerCoalition().getMode().name() : null)
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // REQUEST / RESPONSE DTOs
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    public static class AutomationStateResponse {
        String automationId;
        String automationName;
        int schemaVersion;
        Date compiledAt;
        String topLevelState;
        boolean isTopLevelActive;
        boolean hasBranches;
        boolean hasCoalition;
        List<NodeStateView> conditionNodes;
        List<BranchStateView> branches;
        CoalitionStateView coalition;
        Date lastExecutionTime;
        AutomationRuntimeState.EvalSnapshot lastEvalSnapshot;
    }

    @Value
    @Builder
    public static class NodeStateView {
        String nodeId;
        String conditionType;
        String triggerKey;
        boolean stateful;
        boolean wasActive;
        boolean hasMemoryPolicy;
        String memoryPolicyType;
        String memorySummary;
        int consecutiveTrueCount;
        long firstTrueEpochMs;
        Boolean lastRawResult;
    }

    @Value
    @Builder
    public static class BranchStateView {
        String gateNodeId;
        int priority;
        String logicType;
        String scheduleType;
        String state;
        boolean active;
    }

    @Value
    @Builder
    public static class CoalitionStateView {
        String mode;
        int windowSeconds;
        int sequenceProgress;
        List<MemberFiredView> members;
    }

    @Value
    @Builder
    public static class MemberFiredView {
        String deviceId;
        String role;
        int sequenceIndex;
        long lastFiredEpochMs;
        long secondsAgo;
        boolean withinWindow;
    }

    @Value
    @Builder
    public static class PlanSummaryResponse {
        String automationId;
        String automationName;
        int schemaVersion;
        Date compiledAt;
        String triggerDeviceId;
        List<String> rootConditionNodeIds;
        List<Map<String, Object>> conditionNodes;
        List<Map<String, Object>> branches;
        boolean hasCoalition;
        String coalitionMode;
    }

    @lombok.Data
    public static class OverrideRequest {
        private String action;
    }

    @lombok.Data
    public static class SnoozeRequest {
        private int minutes;
    }

    @Value
    @Builder
    public static class OverrideResponse {
        boolean success;
        String message;

        static OverrideResponse ok(String msg) {
            return OverrideResponse.builder().success(true).message(msg).build();
        }

        static OverrideResponse error(String msg) {
            return OverrideResponse.builder().success(false).message(msg).build();
        }
    }
}