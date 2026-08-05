package dev.automata.automata.controller;

import dev.automata.automata.model.FeatureToggle;
import dev.automata.automata.service.FeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feature")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<FeatureToggle>> getAllFeatures() {
        return ResponseEntity.ok(featureService.getAllFeatures());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<FeatureToggle> createFeature(@RequestBody FeatureToggle featureToggle) {
        return ResponseEntity.ok(featureService.createFeature(featureToggle));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FeatureToggle> toggleFeature(@PathVariable String id) {
        return ResponseEntity.ok(featureService.toggleFeature(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeature(@PathVariable String id) {
        featureService.deleteFeature(id);
        return ResponseEntity.noContent().build();
    }
}