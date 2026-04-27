package dev.automata.automata.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.model.AutomationVersion;
import dev.automata.automata.repository.AutomationVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationVersionService {

    private final AutomationVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Maximum number of versions retained per automation.
     */
    private static final int MAX_VERSIONS = 20;

    // ─────────────────────────────────────────────────────────────────────────
    // SNAPSHOT ON SAVE
    // Called from AutomationService.saveAutomationDetailInternal() after
    // the automation and detail are persisted.
    // ─────────────────────────────────────────────────────────────────────────

    public AutomationVersion snapshot(Automation saved,
                                      AutomationDetail detail,
                                      String savedBy,
                                      String changeNote) {
        // Determine next version number
        int nextVersion = versionRepository
                .findTopByAutomationIdOrderByVersionDesc(saved.getId())
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        // Load the previous version for diff computation
        AutomationVersion previous = versionRepository
                .findTopByAutomationIdOrderByVersionDesc(saved.getId())
                .orElse(null);

        AutomationVersion.VersionDiff diff = computeDiff(
                previous != null ? previous.getAutomationSnapshot() : null,
                saved);

        AutomationVersion version = AutomationVersion.builder()
                .automationId(saved.getId())
                .version(nextVersion)
                .savedBy(savedBy != null ? savedBy : "system")
                .changeNote(changeNote)
                .savedAt(new Date())
                .automationSnapshot(saved)
                .detailSnapshot(detail)
                .diff(diff)
                .build();

        AutomationVersion saved_version = versionRepository.save(version);

        // Enforce cap — delete oldest versions if over limit
        enforceCap(saved.getId());

        log.info("📸 Version {} snapshotted for '{}' by {}",
                nextVersion, saved.getName(), savedBy);
        return saved_version;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<AutomationVersion> getVersions(String automationId) {
        return versionRepository.findByAutomationIdOrderByVersionDesc(automationId);
    }

    public Optional<AutomationVersion> getVersion(String automationId, int version) {
        return versionRepository.findByAutomationIdOrderByVersionDesc(automationId)
                .stream()
                .filter(v -> v.getVersion() == version)
                .findFirst();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROLLBACK
    // Returns the snapshotted Automation and AutomationDetail from the
    // requested version. The caller (AutomationService) then re-saves them,
    // which automatically creates a new snapshot with a rollback note.
    // ─────────────────────────────────────────────────────────────────────────

    public RollbackResult rollback(String automationId, int targetVersion) {
        AutomationVersion target = getVersion(automationId, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + targetVersion + " not found for automation " + automationId));

        log.info("⏪ Rolling back '{}' to version {}",
                target.getAutomationSnapshot().getName(), targetVersion);

        return new RollbackResult(
                target.getAutomationSnapshot(),
                target.getDetailSnapshot(),
                "Rolled back to version " + targetVersion
                        + " (saved at " + target.getSavedAt() + ")"
        );
    }

    public void deleteByAutomationId(String id) {
        versionRepository.deleteByAutomationId(id);
    }

    public record RollbackResult(Automation automation,
                                 AutomationDetail detail,
                                 String changeNote) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIFF COMPUTATION
    // ─────────────────────────────────────────────────────────────────────────

    private AutomationVersion.VersionDiff computeDiff(Automation previous, Automation current) {
        if (previous == null) {
            // First version — everything is "added"
            List<String> addedIds = new ArrayList<>();
            if (current.getConditions() != null)
                current.getConditions().forEach(c -> addedIds.add(c.getNodeId()));
            if (current.getActions() != null)
                current.getActions().forEach(a -> addedIds.add(a.getNodeId()));
            if (current.getOperators() != null)
                current.getOperators().forEach(o -> addedIds.add(o.getNodeId()));
            return AutomationVersion.VersionDiff.builder()
                    .addedNodeIds(addedIds)
                    .removedNodeIds(List.of())
                    .changedNodes(List.of())
                    .changedFields(List.of("initial"))
                    .build();
        }

        List<String> addedNodeIds = new ArrayList<>();
        List<String> removedNodeIds = new ArrayList<>();
        List<AutomationVersion.ChangedNode> changedNodes = new ArrayList<>();
        List<String> changedFields = new ArrayList<>();

        // Top-level field changes
        if (!Objects.equals(previous.getName(), current.getName()))
            changedFields.add("name");
        if (!Objects.equals(previous.getIsEnabled(), current.getIsEnabled()))
            changedFields.add("isEnabled");
        if (!Objects.equals(previous.getTrigger(), current.getTrigger()))
            changedFields.add("trigger");

        // Condition diffs
        diffNodes(
                previous.getConditions() == null ? List.of() : previous.getConditions(),
                current.getConditions() == null ? List.of() : current.getConditions(),
                "condition",
                addedNodeIds, removedNodeIds, changedNodes
        );

        // Action diffs
        diffNodes(
                previous.getActions() == null ? List.of() : previous.getActions(),
                current.getActions() == null ? List.of() : current.getActions(),
                "action",
                addedNodeIds, removedNodeIds, changedNodes
        );

        // Operator diffs
        diffNodes(
                previous.getOperators() == null ? List.of() : previous.getOperators(),
                current.getOperators() == null ? List.of() : current.getOperators(),
                "operator",
                addedNodeIds, removedNodeIds, changedNodes
        );

        return AutomationVersion.VersionDiff.builder()
                .addedNodeIds(addedNodeIds)
                .removedNodeIds(removedNodeIds)
                .changedNodes(changedNodes)
                .changedFields(changedFields)
                .build();
    }

    /**
     * Generic node diff — works for conditions, actions, and operators
     * because all have a getNodeId() method. Uses JSON serialisation for
     * change detection — simple and schema-agnostic.
     */
    private <T> void diffNodes(List<T> previousList,
                               List<T> currentList,
                               String nodeType,
                               List<String> addedIds,
                               List<String> removedIds,
                               List<AutomationVersion.ChangedNode> changed) {
        Map<String, String> prevJson = toJsonMap(previousList);
        Map<String, String> currJson = toJsonMap(currentList);

        // Added
        currJson.keySet().stream()
                .filter(id -> !prevJson.containsKey(id))
                .forEach(addedIds::add);

        // Removed
        prevJson.keySet().stream()
                .filter(id -> !currJson.containsKey(id))
                .forEach(removedIds::add);

        // Changed — same nodeId but different JSON
        currJson.forEach((nodeId, currStr) -> {
            String prevStr = prevJson.get(nodeId);
            if (prevStr != null && !prevStr.equals(currStr)) {
                changed.add(AutomationVersion.ChangedNode.builder()
                        .nodeId(nodeId)
                        .nodeType(nodeType)
                        .changedFields(List.of("modified"))  // field-level diff can be added later
                        .before(abbreviate(prevStr, 200))
                        .after(abbreviate(currStr, 200))
                        .build());
            }
        });
    }

    private <T> Map<String, String> toJsonMap(List<T> nodes) {
        Map<String, String> map = new LinkedHashMap<>();
        for (T node : nodes) {
            try {
                String nodeId = extractNodeId(node);
                if (nodeId != null)
                    map.put(nodeId, objectMapper.writeValueAsString(node));
            } catch (Exception e) {
                log.warn("Failed to serialise node for diff: {}", e.getMessage());
            }
        }
        return map;
    }

    private String extractNodeId(Object node) {
        // Reflection-free: use the common getNodeId() pattern via type checks
        if (node instanceof Automation.Condition c) return c.getNodeId();
        if (node instanceof Automation.Action a) return a.getNodeId();
        if (node instanceof Automation.Operator o) return o.getNodeId();
        return null;
    }

    private String abbreviate(String s, int maxLen) {
        return s == null ? "" : s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    private void enforceCap(String automationId) {
        long count = versionRepository.countByAutomationId(automationId);
        if (count <= MAX_VERSIONS) return;

        // Delete oldest (lowest version numbers) until under cap
        List<AutomationVersion> oldest = versionRepository
                .findByAutomationIdOrderByVersionAsc(automationId);
        long toDelete = count - MAX_VERSIONS;
        oldest.stream().limit(toDelete).forEach(v -> {
            versionRepository.deleteById(v.getId());
            log.debug("🗑️ Purged version {} for automation {}", v.getVersion(), automationId);
        });
    }
}
