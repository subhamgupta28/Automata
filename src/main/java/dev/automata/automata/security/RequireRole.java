package dev.automata.automata.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    String[] value();                          // required roles (OR logic)

    boolean matchAll() default false;          // set true for AND logic

    String message() default "Access denied";
}
