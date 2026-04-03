/**
 * useSystemStateMonitor — Drop-in React hook + component
 *
 * States:
 *   "active"   — tab is open (focused, unfocused, or user on another tab)
 *   "sleeping" — tab is hidden (device locked / screen off / browser minimized)
 */

import {useCallback, useEffect, useRef, useState} from "react";
import {sendAction} from "../../services/apis.jsx";

// ─── Hook ────────────────────────────────────────────────────────────────────

export function useSystemStateMonitor({
                                          reportInterval = 30_000,
                                          onStateChange,
                                      } = {}) {
    const [state, setState] = useState("active");
    const [lastChanged, setLastChanged] = useState(new Date());

    const stateRef = useRef("active");
    const reportTimerRef = useRef(null);

    // ── API reporter ─────────────────────────────────────────────────────────
    const report = useCallback(async (newState) => {
        try {
            await sendAction(
                "678d3ea52d39db1f7a8d8b43",
                {
                    key: "state",
                    state: newState,
                    device_id: "678d3ea52d39db1f7a8d8b43",
                    direct: false,
                },
                "System"
            );
        } catch (err) {
            console.warn("[SystemStateMonitor] API report failed:", err);
        }
    }, []);

    // ── State transition ─────────────────────────────────────────────────────
    const transition = useCallback(
        (newState) => {
            if (stateRef.current === newState) return;
            stateRef.current = newState;
            const now = new Date();
            setState(newState);
            setLastChanged(now);
            onStateChange?.(newState, now);
            report(newState);

            // Reset keep-alive interval on state change
            clearInterval(reportTimerRef.current);
            reportTimerRef.current = setInterval(() => {
                report(stateRef.current);
            }, reportInterval);
        },
        [onStateChange, report, reportInterval]
    );

    // ── Visibility change handler ─────────────────────────────────────────────
    // sleeping = tab hidden (minimized, screen locked, browser in background)
    // active   = everything else (tab open, focused or not, user on another tab)
    const handleVisibilityChange = useCallback(() => {
        transition(document.hidden ? "sleeping" : "active");
    }, [transition]);

    // ── Mount / unmount ──────────────────────────────────────────────────────
    useEffect(() => {
        document.addEventListener("visibilitychange", handleVisibilityChange);

        // Send initial state + start keep-alive
        const initial = document.hidden ? "sleeping" : "active";
        report(initial);
        reportTimerRef.current = setInterval(() => {
            report(stateRef.current);
        }, reportInterval);

        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            clearInterval(reportTimerRef.current);
        };
    }, [handleVisibilityChange, report, reportInterval]);

    return {state, lastChanged};
}

// ─── Component (invisible by default) ────────────────────────────────────────

export default function SystemStateMonitor({
                                               reportInterval,
                                               onStateChange,
                                               debug = false,
                                           }) {
    const {state, lastChanged} = useSystemStateMonitor({
        reportInterval,
        onStateChange,
    });

    if (!debug) return null;

    const colors = {
        active: {bg: "#ffd821", label: "⚡ Active"},
        sleeping: {bg: "#22537c", label: "🌙 Sleeping"},
    };

    const {bg, label} = colors[state] ?? {bg: "#94a3b8", label: state};

    return (
        <div
            style={{
                position: "fixed",
                bottom: 12,
                right: '44%',
                zIndex: 9999,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
                border: "1px solid #1e293b",
                borderRadius: 8,
                padding: "8px 12px",
                boxShadow: "0 4px 24px rgba(0,0,0,0.4)",
                display: "flex",
                flexDirection: "column",
                gap: 4,
                minWidth: 180,
            }}
        >
            <div style={{display: "flex", alignItems: "center", gap: 8}}>
        <span
            style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                backgroundColor: 'transparent',
                boxShadow: `0 0 6px`,
                flexShrink: 0,
            }}
        />
                <span style={{fontWeight: 700, letterSpacing: "0.05em"}}>{label}</span>
            </div>
            <div style={{fontSize: 10}}>
                Since {lastChanged.toLocaleTimeString()}
            </div>
        </div>
    );
}