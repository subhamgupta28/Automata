package dev.automata.automata.controller;

import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.automation.AutomationValidationService.ValidationIssue;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.repository.AutomationDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Standalone validation endpoint — callable from the UI independently of save.
 * <p>
 * POST /api/automation/validate          — validate a detail object from request body
 * GET  /api/automation/validate/{id}     — validate an already-persisted automation by ID
 * GET  /api/automation/validate/all      — validate every automation and return a summary
 */
@RestController
@RequestMapping("/api/automation/validate")
@RequiredArgsConstructor
public class AutomationValidationController {

    private final AutomationValidationService validationService;
    private final AutomationDetailRepository automationDetailRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/automation/validate  — validate a detail object in the request body
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ValidationResponse> validateBody(@RequestBody AutomationDetail detail) {
        List<ValidationIssue> issues = validationService.validateDetail(detail);
        return ResponseEntity.ok(ValidationResponse.from(detail.getId(), issues));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/automation/validate/{id}  — validate a persisted automation
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<?> validateById(@PathVariable String id) {
        AutomationDetail detail = automationDetailRepository.findById(id).orElse(null);
        if (detail == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Automation detail not found for id: " + id));
        }
        List<ValidationIssue> issues = validationService.validateDetail(detail);
        return ResponseEntity.ok(ValidationResponse.from(id, issues));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/automation/validate/all  — validate every persisted automation
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/all")
    public ResponseEntity<BulkValidationResponse> validateAll() {
        List<AutomationDetail> all = automationDetailRepository.findAll();
        List<ValidationResponse> results = all.stream()
                .map(d -> ValidationResponse.from(d.getId(), validationService.validateDetail(d)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(BulkValidationResponse.from(results));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESPONSE TYPES
    // ─────────────────────────────────────────────────────────────────────────

    public record IssueDto(
            String severity,
            String category,
            String nodeId,
            String message
    ) {
        static IssueDto from(ValidationIssue i) {
            return new IssueDto(
                    i.getSeverity().name(),
                    i.getCategory().name(),
                    i.getNodeId(),
                    i.getMessage()
            );
        }
    }

    public record ValidationResponse(
            String automationId,
            boolean valid,
            int errorCount,
            int warningCount,
            int infoCount,
            List<IssueDto> issues
    ) {
        static ValidationResponse from(String id, List<ValidationIssue> raw) {
            List<IssueDto> dtos = raw.stream().map(IssueDto::from).toList();
            long errors = raw.stream().filter(i -> i.getSeverity() == AutomationValidationService.Severity.ERROR
                    || i.getSeverity() == AutomationValidationService.Severity.FATAL).count();
            long warnings = raw.stream().filter(i -> i.getSeverity() == AutomationValidationService.Severity.WARNING).count();
            long infos = raw.stream().filter(i -> i.getSeverity() == AutomationValidationService.Severity.INFO).count();
            return new ValidationResponse(id, errors == 0, (int) errors, (int) warnings, (int) infos, dtos);
        }
    }

    public record BulkValidationResponse(
            int total,
            int validCount,
            int invalidCount,
            List<ValidationResponse> results
    ) {
        static BulkValidationResponse from(List<ValidationResponse> results) {
            long valid = results.stream().filter(ValidationResponse::valid).count();
            return new BulkValidationResponse(
                    results.size(), (int) valid, results.size() - (int) valid, results);
        }
    }
}