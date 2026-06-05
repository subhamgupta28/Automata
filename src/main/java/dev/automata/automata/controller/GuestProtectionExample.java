package dev.automata.automata.controller;

import dev.automata.automata.security.DenyGuest;
import dev.automata.automata.model.Device;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Example controller showing how to protect endpoints from guest access
 * Use @DenyGuest annotation on methods that should block GUEST role users
 * 
 * GET endpoints: Allowed for all users including guests (read-only)
 * POST/PUT/DELETE endpoints: Should be protected with @DenyGuest
 */
@RestController
@RequestMapping("/api/v1/devices")
public class GuestProtectionExample {

    /**
     * GET endpoint - Available to all users including guests (READ-ONLY)
     */
    @GetMapping
    public ResponseEntity<?> getAllDevices() {
        // Available to all roles: USER, ADMIN, GUEST
        return ResponseEntity.ok("All devices");
    }

    /**
     * POST endpoint - BLOCKED for guests via GuestAccessFilter
     * Guests will receive 403 Forbidden
     * 
     * Option 1: Rely on GuestAccessFilter (recommended - applied globally)
     */
    @PostMapping
    public ResponseEntity<?> createDevice(@RequestBody Device device) {
        // Blocked for GUEST role by GuestAccessFilter
        return ResponseEntity.ok("Device created");
    }

    /**
     * PUT endpoint - BLOCKED for guests via GuestAccessFilter
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDevice(@PathVariable String id, @RequestBody Device device) {
        // Blocked for GUEST role by GuestAccessFilter
        return ResponseEntity.ok("Device updated");
    }

    /**
     * DELETE endpoint - BLOCKED for guests via GuestAccessFilter
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDevice(@PathVariable String id) {
        // Blocked for GUEST role by GuestAccessFilter
        return ResponseEntity.ok("Device deleted");
    }

    /**
     * Optional: Use @DenyGuest annotation for more specific error messages
     * This is optional since GuestAccessFilter already blocks all non-GET requests
     */
    @PostMapping("/custom-block")
    @DenyGuest(message = "Guests cannot perform custom operations")
    public ResponseEntity<?> customBlockedOperation() {
        return ResponseEntity.ok("This won't be reached by guests");
    }
}
