package dev.automata.automata.utils;

import dev.automata.automata.service.FeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// aspect/FeatureGateAspect.java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FeatureGateAspect {

    private final FeatureService featureService;

    @Around("@annotation(featureEnabled)")
    public Object gate(ProceedingJoinPoint pjp, FeatureEnabled featureEnabled) throws Throwable {
        Feature feature = featureEnabled.value();

        if (!featureService.isFeatureEnabled(feature.toString())) {
            log.debug("⛔ [feature-gate] '{}' is disabled — skipping {}#{}",
                    feature,
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName());

            if (!featureEnabled.silent()) {
                log.error("Feature '{}' is currently disabled", feature);
            }

            // Return safe default for the method's return type
            return safeDefault(((MethodSignature) pjp.getSignature()).getReturnType());
        }

        return pjp.proceed();
    }

    private Object safeDefault(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) return null;
        if (returnType == boolean.class || returnType == Boolean.class) return false;
        if (returnType == int.class || returnType == Integer.class) return 0;
        if (returnType == long.class || returnType == Long.class) return 0L;
        if (returnType == Optional.class) return Optional.empty();
        if (returnType == List.class) return List.of();
        if (returnType == Map.class) return Map.of();
        return null; // for Object types
    }
}
