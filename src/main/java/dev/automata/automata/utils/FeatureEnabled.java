package dev.automata.automata.utils;

import java.lang.annotation.*;

// annotation/FeatureEnabled.java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureEnabled {
    Feature value();

    /**
     * When true (default), the method silently returns null/void.
     * When false, throws FeatureDisabledException instead.
     */
    boolean silent() default false;
}
