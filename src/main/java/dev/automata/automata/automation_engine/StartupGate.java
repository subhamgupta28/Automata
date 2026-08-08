package dev.automata.automata.automation_engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Guards automation execution during the startup reconciliation window.
 * <p>
 * Two levels of gating:
 * <p>
 * 1. GLOBAL gate — closed from the moment ApplicationReadyEvent fires until
 * StartupReconciler has finished reading all states and scheduling all
 * per-automation reconciliation tasks (typically < 1s). Keeps ANY
 * execution from racing ahead while the reconciler is still deciding
 * which automations need reconciliation.
 * <p>
 * 2. PER-AUTOMATION gate — for each automation that needs reconciliation,
 * a dedicated latch is held until that automation's reconcileOne() has
 * finished dispatching (i.e. until the orchestrator.execute() call has
 * been submitted to automationExecutor and the compile lock released).
 * A real device tick arriving during this window waits at the latch
 * instead of running with a potentially mid-recompile plan.
 * <p>
 * Both levels have a hard timeout so a failure in the reconciler never
 * stalls the engine permanently.
 */
@Slf4j
@Component
public class StartupGate {

    /**
     * Hard ceiling on how long any caller waits for the global gate.
     */
    private static final long GLOBAL_TIMEOUT_MS = 10_000L;

    /**
     * Hard ceiling on how long any caller waits for a per-automation latch.
     */
    private static final long PER_AUTO_TIMEOUT_MS = 5_000L;

    // Global gate — open() called by StartupReconciler after scheduling all tasks
    private final CountDownLatch globalGate = new CountDownLatch(1);

    // Per-automation latches — one per automation that needs reconciliation.
    // Absent = no reconciliation needed = no wait required.
    private final ConcurrentHashMap<String, CountDownLatch> perAutoLatches =
            new ConcurrentHashMap<>();

    // ── Called by StartupReconciler ───────────────────────────────────────

    /**
     * Register that this automation needs reconciliation before execution.
     */
    public void holdFor(String automationId) {
        perAutoLatches.put(automationId, new CountDownLatch(1));
    }

    /**
     * Called after all per-automation tasks have been scheduled.
     */
    public void openGlobal() {
        globalGate.countDown();
    }

    /**
     * Called by reconcileOne() after orchestrator.execute() is submitted.
     */
    public void releaseFor(String automationId) {
        CountDownLatch latch = perAutoLatches.remove(automationId);
        if (latch != null) latch.countDown();
    }

    // ── Called by AutomationOrchestrator.executeInternal() ───────────────

    /**
     * Blocks until both the global gate is open AND the per-automation
     * latch (if any) has been released. Returns immediately if this
     * automation has no pending reconciliation.
     * <p>
     * Hard timeouts on both waits ensure a reconciler failure never
     * stalls execution permanently.
     */
    public void awaitReady(String automationId) {
        // 1. Wait for the global gate (reconciler has finished scheduling)
        if (globalGate.getCount() > 0) {
            try {
                boolean opened = globalGate.await(GLOBAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!opened) {
                    log.warn("⚠️ [gate] Global startup gate timed out after {}ms — " +
                            "proceeding anyway", GLOBAL_TIMEOUT_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ [gate] Interrupted waiting for global startup gate");
            }
        }

        // 2. Wait for this specific automation's reconciliation to complete
        CountDownLatch latch = perAutoLatches.get(automationId);
        if (latch == null || latch.getCount() == 0) return;

        try {
            boolean released = latch.await(PER_AUTO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!released) {
                log.warn("⚠️ [gate] Per-automation latch timed out for '{}' after {}ms — " +
                        "proceeding anyway", automationId, PER_AUTO_TIMEOUT_MS);
                perAutoLatches.remove(automationId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("⚠️ [gate] Interrupted waiting for per-automation latch '{}'", automationId);
        }
    }

    /**
     * True if the startup reconciliation window is still open.
     */
    public boolean isStartupWindowOpen() {
        return globalGate.getCount() > 0 || !perAutoLatches.isEmpty();
    }
}