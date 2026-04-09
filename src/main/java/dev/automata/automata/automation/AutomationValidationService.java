package dev.automata.automata.automation;

import dev.automata.automata.model.AutomationDetail;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AutomationValidationService
 * <p>
 * Performs deep structural and semantic validation of an AutomationDetail graph
 * before it is persisted. Returns a rich list of {@link ValidationIssue} objects
 * so the caller (UI or API) can surface exactly what is wrong and where.
 * <p>
 * Validation is grouped into categories:
 * TRIGGER   — trigger node existence and configuration
 * CONDITION — condition node values, ranges, schedule fields
 * ACTION    — action node configuration and connectivity
 * GRAPH     — node connectivity, orphaned nodes, missing edges
 * OPERATOR  — logic operator placement and type
 * GLOBAL    — automation-level rules (e.g. no actions at all)
 */
@Slf4j
@Service
public class AutomationValidationService {

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full validation. Returns all issues found.
     * An empty list means the automation is valid.
     */
    public List<ValidationIssue> validateDetail(AutomationDetail detail) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (detail == null) {
            issues.add(ValidationIssue.fatal(Category.GLOBAL, null, "AutomationDetail is null"));
            return issues;
        }

        if (detail.getNodes() == null || detail.getNodes().isEmpty()) {
            issues.add(ValidationIssue.fatal(Category.GLOBAL, null, "Automation has no nodes — add at least a trigger and one action"));
            return issues;
        }

        // Collect typed nodes once
        List<AutomationDetail.Node> triggerNodes = filterNodes(detail, NodeRole.TRIGGER);
        List<AutomationDetail.Node> conditionNodes = filterNodes(detail, NodeRole.CONDITION);
        List<AutomationDetail.Node> actionNodes = filterNodes(detail, NodeRole.ACTION);
        List<AutomationDetail.Node> operatorNodes = filterNodes(detail, NodeRole.OPERATOR);

        // Run each validation group
        validateTrigger(triggerNodes, issues);
        validateConditions(conditionNodes, issues);
        validateActions(actionNodes, issues);
        validateOperators(operatorNodes, conditionNodes, issues);
        validateGraph(detail.getNodes(), triggerNodes, conditionNodes, actionNodes, operatorNodes, issues);
        validateGlobalRules(triggerNodes, actionNodes, conditionNodes, issues);

        log.info("Validation complete — {} issue(s) found for automation '{}'",
                issues.size(), detail.getId());
        return issues;
    }

    /**
     * Convenience method: returns only the human-readable error strings.
     * Used by the existing saveAutomationDetailWithValidation() method signature.
     */
    public List<String> validate(AutomationDetail detail) {
        return validateDetail(detail).stream()
                .filter(i -> i.getSeverity() != Severity.INFO)
                .map(ValidationIssue::getMessage)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the automation has zero ERROR or FATAL issues.
     */
    public boolean isValid(AutomationDetail detail) {
        return validateDetail(detail).stream()
                .noneMatch(i -> i.getSeverity() == Severity.ERROR
                        || i.getSeverity() == Severity.FATAL);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRIGGER VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateTrigger(List<AutomationDetail.Node> triggerNodes,
                                 List<ValidationIssue> issues) {

        if (triggerNodes.isEmpty()) {
            issues.add(ValidationIssue.fatal(Category.TRIGGER, null,
                    "No trigger node found — every automation needs exactly one trigger"));
            return;
        }
        if (triggerNodes.size() > 1) {
            issues.add(ValidationIssue.error(Category.TRIGGER, null,
                    "Multiple trigger nodes found (" + triggerNodes.size() + ") — only one trigger is allowed"));
        }

        for (AutomationDetail.Node node : triggerNodes) {
            var td = node.getData().getTriggerData();
            String nodeId = node.getId();

            if (td == null) {
                issues.add(ValidationIssue.fatal(Category.TRIGGER, nodeId,
                        "Trigger node '" + nodeId + "' has no trigger data configured"));
                continue;
            }

            if (isBlank(td.getDeviceId())) {
                issues.add(ValidationIssue.error(Category.TRIGGER, nodeId,
                        "Trigger has no device selected — pick a device to listen to"));
            }
            if (isBlank(td.getType())) {
                issues.add(ValidationIssue.error(Category.TRIGGER, nodeId,
                        "Trigger type is missing (e.g. 'sensor', 'time', 'mqtt')"));
            }
            if (isBlank(td.getName())) {
                issues.add(ValidationIssue.warning(Category.TRIGGER, nodeId,
                        "Trigger has no name — give it a descriptive name for easier management"));
            }
            if ((td.getKeys() == null || td.getKeys().isEmpty()) && isBlank(td.getKey())) {
                issues.add(ValidationIssue.warning(Category.TRIGGER, nodeId,
                        "Trigger has no keys configured — specify which data keys this trigger watches"));
            }
            if ("time".equals(td.getType()) && isBlank(td.getValue())) {
                issues.add(ValidationIssue.warning(Category.TRIGGER, nodeId,
                        "Time trigger has no value set — specify the time to fire"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONDITION VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateConditions(List<AutomationDetail.Node> conditionNodes,
                                    List<ValidationIssue> issues) {

        for (AutomationDetail.Node node : conditionNodes) {
            AutomationDetail.Node.Data.ConditionData cd = node.getData().getConditionData();
            String nodeId = node.getId();

            if (cd == null) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition node '" + nodeId + "' has no condition data — configure the condition or remove the node"));
                continue;
            }

            String condType = cd.getCondition();
            if (isBlank(condType)) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition node '" + label(cd.getNodeId(), nodeId) + "' has no condition type set (e.g. above, below, range, scheduled)"));
                continue;
            }

            switch (condType) {
                case "above" -> validateThresholdCondition(cd, nodeId, "above", issues);
                case "below" -> validateThresholdCondition(cd, nodeId, "below", issues);
                case "range" -> validateRangeCondition(cd, nodeId, issues);
                case "scheduled" -> validateScheduledCondition(cd, nodeId, issues);
                case "equal" -> validateEqualCondition(cd, nodeId, issues);
                default -> issues.add(ValidationIssue.warning(Category.CONDITION, nodeId,
                        "Unknown condition type '" + condType + "' — expected: above, below, range, scheduled, equal"));
            }

            // Duration / interval cross-checks
            if (cd.getDurationMinutes() < 0) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "' has a negative durationMinutes (" + cd.getDurationMinutes() + ")"));
            }
            if (cd.getIntervalMinutes() < 0) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "' has a negative intervalMinutes (" + cd.getIntervalMinutes() + ")"));
            }
            if (cd.getDurationMinutes() > 0 && cd.getIntervalMinutes() > 0
                    && cd.getDurationMinutes() >= cd.getIntervalMinutes()) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "': durationMinutes (" + cd.getDurationMinutes()
                                + ") must be less than intervalMinutes (" + cd.getIntervalMinutes()
                                + ") — the automation would re-fire before its own duration expires"));
            }
            if (cd.getOffsetMinutes() < -120 || cd.getOffsetMinutes() > 120) {
                issues.add(ValidationIssue.warning(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "' has an unusually large solar offset ("
                                + cd.getOffsetMinutes() + " min) — is this intentional?"));
            }
        }
    }

    private void validateThresholdCondition(AutomationDetail.Node.Data.ConditionData cd,
                                            String nodeId, String type,
                                            List<ValidationIssue> issues) {
        if (isBlank(cd.getValue())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (" + type + "): threshold value is missing"));
        } else if (!isNumericString(cd.getValue())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (" + type + "): threshold value '"
                            + cd.getValue() + "' is not a valid number"));
        }
        if (isBlank(cd.getTriggerKey())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (" + type + "): no trigger key selected — which sensor value should be checked?"));
        }
    }

    private void validateRangeCondition(AutomationDetail.Node.Data.ConditionData cd,
                                        String nodeId,
                                        List<ValidationIssue> issues) {
        boolean aboveOk = !isBlank(cd.getAbove()) && isNumericString(cd.getAbove());
        boolean belowOk = !isBlank(cd.getBelow()) && isNumericString(cd.getBelow());

        if (!aboveOk) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (range): 'above' (lower bound) is missing or not a valid number"));
        }
        if (!belowOk) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (range): 'below' (upper bound) is missing or not a valid number"));
        }
        if (aboveOk && belowOk) {
            double above = Double.parseDouble(cd.getAbove());
            double below = Double.parseDouble(cd.getBelow());
            if (above >= below) {
                issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "' (range): lower bound ("
                                + above + ") must be strictly less than upper bound (" + below + ")"));
            }
            if (below - above < 0.01) {
                issues.add(ValidationIssue.warning(Category.CONDITION, nodeId,
                        "Condition '" + label(cd.getNodeId(), nodeId) + "' (range): the window is extremely narrow ("
                                + (below - above) + ") — the condition may rarely trigger"));
            }
        }
        if (isBlank(cd.getTriggerKey())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (range): no trigger key selected"));
        }
    }

    private void validateScheduledCondition(AutomationDetail.Node.Data.ConditionData cd,
                                            String nodeId,
                                            List<ValidationIssue> issues) {
        String schedType = cd.getScheduleType();
        if (isBlank(schedType)) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled): scheduleType is missing (e.g. 'exact', 'range', 'solar', 'interval')"));
            return;
        }

        switch (schedType) {
            case "exact" -> {
                if (parseTime(cd.getTime()) == null) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/exact): time '"
                                    + cd.getTime() + "' is missing or not a valid HH:mm:ss / hh:mm:ss a time"));
                }
            }
            case "range" -> {
                LocalTime from = parseTime(cd.getFromTime());
                LocalTime to = parseTime(cd.getToTime());
                if (from == null) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/range): fromTime '"
                                    + cd.getFromTime() + "' is invalid or missing"));
                }
                if (to == null) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/range): toTime '"
                                    + cd.getToTime() + "' is invalid or missing"));
                }
                if (from != null && to != null && from.equals(to)) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/range): fromTime and toTime are identical — the window has zero duration"));
                }
            }
            case "solar" -> {
                if (isBlank(cd.getSolarType())) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/solar): solarType is missing (use 'sunrise' or 'sunset')"));
                } else if (!"sunrise".equalsIgnoreCase(cd.getSolarType())
                        && !"sunset".equalsIgnoreCase(cd.getSolarType())) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/solar): unknown solarType '"
                                    + cd.getSolarType() + "' — use 'sunrise' or 'sunset'"));
                }
            }
            case "interval" -> {
                if (cd.getIntervalMinutes() <= 0) {
                    issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                            "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled/interval): intervalMinutes must be > 0"));
                }
            }
            default -> issues.add(ValidationIssue.warning(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (scheduled): unknown scheduleType '"
                            + schedType + "'"));
        }
    }

    private void validateEqualCondition(AutomationDetail.Node.Data.ConditionData cd,
                                        String nodeId,
                                        List<ValidationIssue> issues) {
        if (isBlank(cd.getValue())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (equal): expected value is missing"));
        }
        if (isBlank(cd.getTriggerKey())) {
            issues.add(ValidationIssue.error(Category.CONDITION, nodeId,
                    "Condition '" + label(cd.getNodeId(), nodeId) + "' (equal): no trigger key selected"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACTION VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateActions(List<AutomationDetail.Node> actionNodes,
                                 List<ValidationIssue> issues) {

        if (actionNodes.isEmpty()) {
            issues.add(ValidationIssue.fatal(Category.ACTION, null,
                    "No action nodes found — add at least one action for the automation to do something"));
            return;
        }

        Set<Integer> usedOrders = new HashSet<>();

        for (AutomationDetail.Node node : actionNodes) {
            var ad = node.getData().getActionData();
            String nodeId = node.getId();

            if (ad == null) {
                issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                        "Action node '" + nodeId + "' has no action data — configure it or remove it"));
                continue;
            }

            if (isBlank(ad.getDeviceId())) {
                issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                        "Action '" + label(ad.getName(), nodeId) + "' has no target device selected"));
            }
            if (isBlank(ad.getKey())) {
                issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                        "Action '" + label(ad.getName(), nodeId) + "' has no key configured (e.g. 'power', 'brightness', 'alert')"));
            }
            if (isBlank(ad.getData())) {
                // alert and app_notify with empty data is a problem; for others it might be valid
                if ("alert".equals(ad.getKey()) || "app_notify".equals(ad.getKey())) {
                    issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                            "Action '" + label(ad.getName(), nodeId) + "' (" + ad.getKey() + "): message/data is empty"));
                } else {
                    issues.add(ValidationIssue.warning(Category.ACTION, nodeId,
                            "Action '" + label(ad.getName(), nodeId) + "' has no data/value — is this intentional?"));
                }
            }

            // Duplicate order detection
            if (ad.getOrder() != 0) {
                if (!usedOrders.add(ad.getOrder())) {
                    issues.add(ValidationIssue.warning(Category.ACTION, nodeId,
                            "Action '" + label(ad.getName(), nodeId) + "' has duplicate order (" + ad.getOrder()
                                    + ") — execution order may be unpredictable"));
                }
            }

            // Negative delay
            if (ad.getDelaySeconds() < 0) {
                issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                        "Action '" + label(ad.getName(), nodeId) + "' has a negative delaySeconds (" + ad.getDelaySeconds() + ")"));
            }
            // Warn on very long delay
            if (ad.getDelaySeconds() > 300) {
                issues.add(ValidationIssue.warning(Category.ACTION, nodeId,
                        "Action '" + label(ad.getName(), nodeId) + "' has a very long delay (" + ad.getDelaySeconds()
                                + "s) which may cause automation timeout (limit: 30s per action)"));
            }

            // conditionGroup must be positive or negative
            String group = ad.getConditionGroup();
            if (!isBlank(group) && !"positive".equalsIgnoreCase(group) && !"negative".equalsIgnoreCase(group)) {
                issues.add(ValidationIssue.error(Category.ACTION, nodeId,
                        "Action '" + label(ad.getName(), nodeId) + "' has invalid conditionGroup '"
                                + group + "' — must be 'positive' or 'negative'"));
            }
        }

        // At least one positive action must exist
        boolean hasPositiveAction = actionNodes.stream()
                .map(n -> n.getData().getActionData())
                .filter(Objects::nonNull)
                .anyMatch(ad -> isBlank(ad.getConditionGroup())
                        || "positive".equalsIgnoreCase(ad.getConditionGroup()));

        if (!hasPositiveAction) {
            issues.add(ValidationIssue.error(Category.ACTION, null,
                    "No 'positive' (trigger) actions found — the automation has nothing to do when its condition is met"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OPERATOR VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateOperators(List<AutomationDetail.Node> operatorNodes,
                                   List<AutomationDetail.Node> conditionNodes,
                                   List<ValidationIssue> issues) {

        for (AutomationDetail.Node node : operatorNodes) {
            var op = node.getData().getOperators();
            String nodeId = node.getId();

            if (op == null) {
                issues.add(ValidationIssue.error(Category.OPERATOR, nodeId,
                        "Operator node '" + nodeId + "' has no operator data configured"));
                continue;
            }

            String logicType = op.getLogicType();
            if (isBlank(logicType)) {
                issues.add(ValidationIssue.error(Category.OPERATOR, nodeId,
                        "Operator node '" + nodeId + "' has no logicType — set it to 'AND' or 'OR'"));
            } else if (!"AND".equalsIgnoreCase(logicType) && !"OR".equalsIgnoreCase(logicType)) {
                issues.add(ValidationIssue.error(Category.OPERATOR, nodeId,
                        "Operator node '" + nodeId + "' has unknown logicType '" + logicType
                                + "' — must be 'AND' or 'OR'"));
            }
        }

        // Operator present with only one condition — it's a no-op but warn
        if (!operatorNodes.isEmpty() && conditionNodes.size() == 1) {
            issues.add(ValidationIssue.warning(Category.OPERATOR, null,
                    "An operator node is present but there is only one condition — operators only apply when there are multiple conditions"));
        }

        // Multiple conditions without any operator
        if (conditionNodes.size() > 1 && operatorNodes.isEmpty()) {
            issues.add(ValidationIssue.warning(Category.OPERATOR, null,
                    "Multiple conditions exist but no operator node is connected — AND logic will be applied by default; add an operator node to make the intent explicit"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GRAPH / CONNECTIVITY VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    private void validateGraph(List<AutomationDetail.Node> allNodes,
                               List<AutomationDetail.Node> triggerNodes,
                               List<AutomationDetail.Node> conditionNodes,
                               List<AutomationDetail.Node> actionNodes,
                               List<AutomationDetail.Node> operatorNodes,
                               List<ValidationIssue> issues) {

        // Build a set of all node IDs referenced by previousNodeRef (i.e. "connected from")
        Set<String> referencedIds = allNodes.stream()
                .map(AutomationDetail.Node::getData)
                .filter(Objects::nonNull)
                .flatMap(d -> {
                    List<String> refs = new ArrayList<>();
                    if (d.getTriggerData() != null && d.getTriggerData().getNodeId() != null)
                        refs.add(d.getTriggerData().getNodeId());
                    if (d.getConditionData() != null && d.getConditionData().getPreviousNodeRef() != null && !d.getConditionData().getPreviousNodeRef().isEmpty())
                        refs.add(d.getConditionData().getPreviousNodeRef().getFirst().getNodeId());
                    if (d.getActionData() != null && d.getActionData().getPreviousNodeRef() != null && !d.getActionData().getPreviousNodeRef().isEmpty())
                        refs.add(d.getActionData().getPreviousNodeRef().getFirst().getNodeId());
                    if (d.getOperators() != null && d.getOperators().getPreviousNodeRef() != null && !d.getOperators().getPreviousNodeRef().isEmpty())
                        refs.add(d.getOperators().getPreviousNodeRef().getFirst().getNodeId());
                    return refs.stream();
                })
                .collect(Collectors.toSet());

        Set<String> allNodeIds = allNodes.stream()
                .map(AutomationDetail.Node::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Orphaned nodes: action nodes with no previousNodeRef pointing to anything
        for (AutomationDetail.Node node : actionNodes) {
            var ad = node.getData().getActionData();
            if (ad == null) continue;
            if (ad.getPreviousNodeRef() != null && !ad.getPreviousNodeRef().isEmpty()) {
                if (isBlank(ad.getPreviousNodeRef().getFirst().getNodeId())) {
                    issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                            "Action node '" + label(ad.getName(), node.getId())
                                    + "' is not connected — draw a connection from a trigger or condition node to this action"));
                } else if (!allNodeIds.contains(ad.getPreviousNodeRef().getFirst().getNodeId())) {
                    issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                            "Action node '" + label(ad.getName(), node.getId())
                                    + "' references a non-existent node '" + ad.getPreviousNodeRef()
                                    + "' — the source node may have been deleted"));
                }
            }

        }

        // Orphaned conditions
        for (AutomationDetail.Node node : conditionNodes) {
            var cd = node.getData().getConditionData();
            if (cd == null) continue;
            if (cd.getPreviousNodeRef() != null && !cd.getPreviousNodeRef().isEmpty()) {
                if (isBlank(cd.getPreviousNodeRef().getFirst().getNodeId())) {
                    issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                            "Condition node '" + label(cd.getNodeId(), node.getId())
                                    + "' is not connected — connect it from the trigger node"));
                } else if (!allNodeIds.contains(cd.getPreviousNodeRef().getFirst().getNodeId())) {
                    issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                            "Condition node '" + label(cd.getNodeId(), node.getId())
                                    + "' references a non-existent node '" + cd.getPreviousNodeRef() + "'"));
                }
            }
        }

        // Orphaned operators
        for (AutomationDetail.Node node : operatorNodes) {
            var op = node.getData().getOperators();
            if (op == null) continue;
            if (isBlank(op.getPreviousNodeRef().getFirst().getNodeId())) {
                issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                        "Operator node '" + node.getId() + "' is not connected to any upstream node"));
            } else if (!allNodeIds.contains(op.getPreviousNodeRef().getFirst().getNodeId())) {
                issues.add(ValidationIssue.error(Category.GRAPH, node.getId(),
                        "Operator node '" + node.getId() + "' references a non-existent upstream node '"
                                + op.getPreviousNodeRef() + "'"));
            }
        }

        // Trigger node has no downstream connections at all
        if (!triggerNodes.isEmpty()) {
            AutomationDetail.Node triggerNode = triggerNodes.getFirst();
            String triggerId = triggerNode.getId();
            if (triggerId != null && !referencedIds.contains(triggerId)) {
                issues.add(ValidationIssue.error(Category.GRAPH, triggerId,
                        "Trigger node is not connected to any condition or action — draw an edge from the trigger to at least one node"));
            }
        }

        // Completely isolated nodes (no id referenced AND no previousNodeRef)
        for (AutomationDetail.Node node : allNodes) {
            if (node.getId() == null) {
                issues.add(ValidationIssue.warning(Category.GRAPH, null,
                        "A node exists with a null ID — this may cause serialisation issues"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GLOBAL RULES
    // ─────────────────────────────────────────────────────────────────────────

    private void validateGlobalRules(List<AutomationDetail.Node> triggerNodes,
                                     List<AutomationDetail.Node> actionNodes,
                                     List<AutomationDetail.Node> conditionNodes,
                                     List<ValidationIssue> issues) {

        // Trigger → condition → action is the minimum viable chain
        if (!triggerNodes.isEmpty() && conditionNodes.isEmpty() && !actionNodes.isEmpty()) {
            issues.add(ValidationIssue.info(Category.GLOBAL, null,
                    "This automation has no conditions — it will execute actions every time the trigger fires"));
        }

        // Revert actions should exist when duration is used
        boolean hasDuration = conditionNodes.stream()
                .map(n -> n.getData().getConditionData())
                .filter(Objects::nonNull)
                .anyMatch(cd -> cd.getDurationMinutes() > 0);

        if (hasDuration) {
            boolean hasNegativeAction = actionNodes.stream()
                    .map(n -> n.getData().getActionData())
                    .filter(Objects::nonNull)
                    .anyMatch(ad -> "negative".equalsIgnoreCase(ad.getConditionGroup()));
            if (!hasNegativeAction) {
                issues.add(ValidationIssue.warning(Category.GLOBAL, null,
                        "A condition with durationMinutes is set but no 'negative' (revert) actions exist — the automation will not undo anything when the duration expires"));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private List<AutomationDetail.Node> filterNodes(AutomationDetail detail, NodeRole role) {
        return detail.getNodes().stream()
                .filter(n -> n.getData() != null)
                .filter(n -> switch (role) {
                    case TRIGGER -> n.getData().getTriggerData() != null;
                    case CONDITION -> n.getData().getConditionData() != null;
                    case ACTION -> n.getData().getActionData() != null;
                    case OPERATOR -> n.getData().getOperators() != null;
                })
                .collect(Collectors.toList());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isNumericString(String s) {
        if (s == null) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String label(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private LocalTime parseTime(String timeText) {
        if (isBlank(timeText)) return null;
        try {
            return LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                DateTimeFormatter fmt12 = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("hh:mm:ss a")
                        .toFormatter(Locale.ENGLISH);
                return LocalTime.parse(timeText.trim(), fmt12);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private enum NodeRole {TRIGGER, CONDITION, ACTION, OPERATOR}

    // ─────────────────────────────────────────────────────────────────────────
    // VALUE OBJECTS
    // ─────────────────────────────────────────────────────────────────────────

    public enum Severity {INFO, WARNING, ERROR, FATAL}

    public enum Category {TRIGGER, CONDITION, ACTION, OPERATOR, GRAPH, GLOBAL}

    /**
     * A single validation finding with full context for UI rendering.
     */
    @Getter
    public static class ValidationIssue {
        private final Severity severity;
        private final Category category;
        private final String nodeId;      // null = automation-level issue
        private final String message;

        private ValidationIssue(Severity severity, Category category, String nodeId, String message) {
            this.severity = severity;
            this.category = category;
            this.nodeId = nodeId;
            this.message = message;
        }

        public static ValidationIssue fatal(Category c, String n, String m) {
            return new ValidationIssue(Severity.FATAL, c, n, m);
        }

        public static ValidationIssue error(Category c, String n, String m) {
            return new ValidationIssue(Severity.ERROR, c, n, m);
        }

        public static ValidationIssue warning(Category c, String n, String m) {
            return new ValidationIssue(Severity.WARNING, c, n, m);
        }

        public static ValidationIssue info(Category c, String n, String m) {
            return new ValidationIssue(Severity.INFO, c, n, m);
        }

        @Override
        public String toString() {
            return "[" + severity + "][" + category + "]"
                    + (nodeId != null ? "[node:" + nodeId + "] " : " ")
                    + message;
        }
    }
}