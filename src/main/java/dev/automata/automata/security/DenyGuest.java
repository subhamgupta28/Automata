package dev.automata.automata.security;

import java.lang.annotation.*;

/**
 * DenyGuest annotation - Used to deny access to guest users (GUEST role)
 * Can be applied to controller methods to block guests from write operations
 * 
 * Example:
 * @PostMapping("/devices")
 * @DenyGuest(message = "Guests cannot create devices")
 * public ResponseEntity<?> createDevice(...) { }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DenyGuest {
    String message() default "This operation is not available in guest mode";
}
