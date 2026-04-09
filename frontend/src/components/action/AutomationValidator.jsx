import {useState} from "react";

const SEV_META = {
    FATAL: {color: "#ff2d55", bg: "#2a0a10", border: "#7a1525", icon: "✕", label: "Fatal"},
    ERROR: {color: "#ff6b35", bg: "#221008", border: "#7a3010", icon: "!", label: "Error"},
    WARNING: {color: "#ffc940", bg: "#1f1800", border: "#6b4d00", icon: "▲", label: "Warning"},
    INFO: {color: "#4fc3f7", bg: "#081520", border: "#0d4060", icon: "i", label: "Info"},
};

const CAT_ICONS = {
    TRIGGER: "⚡",
    CONDITION: "◈",
    ACTION: "▶",
    OPERATOR: "⊕",
    GRAPH: "⬡",
    GLOBAL: "◎",
};

export default function AutomationValidator() {
    const [mode, setMode] = useState("id");   // "id" | "all"
    const [inputId, setInputId] = useState("");
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);   // single ValidationResponse
    const [bulkResult, setBulkResult] = useState(null); // BulkValidationResponse
    const [error, setError] = useState(null);
    const [filter, setFilter] = useState("ALL");  // ALL | FATAL | ERROR | WARNING | INFO
    const [expandedIds, setExpandedIds] = useState(new Set());

    const BASE = "http://localhost:8010/api/automation/validate";

    async function runValidation() {
        setLoading(true);
        setResult(null);
        setBulkResult(null);
        setError(null);
        setExpandedIds(new Set());
        try {
            if (mode === "all") {
                const r = await fetch(`${BASE}/all`);
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                setBulkResult(await r.json());
            } else {
                if (!inputId.trim()) {
                    setError("Enter an automation ID");
                    setLoading(false);
                    return;
                }
                const r = await fetch(`${BASE}/${inputId.trim()}`);
                if (!r.ok) {
                    const body = await r.json().catch(() => ({}));
                    throw new Error(body.error || `HTTP ${r.status}`);
                }
                setResult(await r.json());
            }
        } catch (e) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    }

    function toggleExpand(id) {
        setExpandedIds(prev => {
            const n = new Set(prev);
            n.has(id) ? n.delete(id) : n.add(id);
            return n;
        });
    }

    const filteredIssues = (issues = []) =>
        filter === "ALL" ? issues : issues.filter(i => i.severity === filter);

    const sevCounts = (issues = []) =>
        issues.reduce((acc, i) => {
            acc[i.severity] = (acc[i.severity] || 0) + 1;
            return acc;
        }, {});

    return (
        <div style={{
            fontFamily: "'IBM Plex Mono', 'Fira Code', monospace",
            background: "#0a0c0f",
            minHeight: "100vh",
            color: "#c8d0db",
            padding: "32px 24px",
        }}>
            {/* Header */}
            <div style={{marginBottom: 32}}>
                <div style={{
                    fontSize: 11, letterSpacing: "0.2em", color: "#4a5568",
                    textTransform: "uppercase", marginBottom: 6,
                }}>Automata System
                </div>
                <h1 style={{
                    fontSize: 26, fontWeight: 700, color: "#e8edf2",
                    letterSpacing: "-0.5px", margin: 0,
                }}>Automation Validator</h1>
                <div style={{fontSize: 13, color: "#4a5568", marginTop: 4}}>
                    Deep structural &amp; semantic validation of automation graphs
                </div>
            </div>

            {/* Controls */}
            <div style={{
                background: "#0f1318",
                border: "1px solid #1e2530",
                borderRadius: 8,
                padding: "20px 24px",
                marginBottom: 24,
                display: "flex",
                gap: 12,
                alignItems: "flex-end",
                flexWrap: "wrap",
            }}>
                {/* Mode toggle */}
                <div>
                    <label style={{
                        fontSize: 11,
                        color: "#4a5568",
                        letterSpacing: "0.1em",
                        display: "block",
                        marginBottom: 6
                    }}>
                        MODE
                    </label>
                    <div style={{display: "flex", gap: 4}}>
                        {[["id", "By ID"], ["all", "Validate All"]].map(([v, l]) => (
                            <button key={v} onClick={() => setMode(v)} style={{
                                padding: "7px 14px", fontSize: 13, borderRadius: 4, cursor: "pointer",
                                border: mode === v ? "1px solid #3a8fff" : "1px solid #1e2530",
                                background: mode === v ? "#0d2a4a" : "#0a0c0f",
                                color: mode === v ? "#3a8fff" : "#6b7280",
                                transition: "all 0.15s",
                            }}>{l}</button>
                        ))}
                    </div>
                </div>

                {/* ID input */}
                {mode === "id" && (
                    <div style={{flex: 1, minWidth: 220}}>
                        <label style={{
                            fontSize: 11,
                            color: "#4a5568",
                            letterSpacing: "0.1em",
                            display: "block",
                            marginBottom: 6
                        }}>
                            AUTOMATION ID
                        </label>
                        <input
                            value={inputId}
                            onChange={e => setInputId(e.target.value)}
                            onKeyDown={e => e.key === "Enter" && runValidation()}
                            placeholder="e.g. 64f3a..."
                            style={{
                                width: "100%", padding: "8px 12px", fontSize: 13,
                                background: "#080a0d", border: "1px solid #1e2530",
                                borderRadius: 4, color: "#c8d0db", outline: "none",
                                boxSizing: "border-box",
                            }}
                        />
                    </div>
                )}

                {/* Run button */}
                <button onClick={runValidation} disabled={loading} style={{
                    padding: "9px 24px", fontSize: 13, fontWeight: 600,
                    background: loading ? "#1e2530" : "#1a4a8a",
                    border: "1px solid " + (loading ? "#2a3340" : "#2a6acc"),
                    borderRadius: 4, color: loading ? "#4a5568" : "#7ab8ff",
                    cursor: loading ? "not-allowed" : "pointer",
                    letterSpacing: "0.05em",
                    transition: "all 0.15s",
                    fontFamily: "inherit",
                }}>
                    {loading ? "Running…" : "▶ Run Validation"}
                </button>
            </div>

            {/* Error */}
            {error && (
                <div style={{
                    background: "#200a0a", border: "1px solid #5a1515",
                    borderRadius: 6, padding: "12px 16px",
                    color: "#ff6b6b", fontSize: 13, marginBottom: 20,
                }}>
                    ✕ {error}
                </div>
            )}

            {/* Single result */}
            {result && <SingleResult result={result} filter={filter} setFilter={setFilter}
                                     filteredIssues={filteredIssues} sevCounts={sevCounts}/>}

            {/* Bulk result */}
            {bulkResult && <BulkResult bulk={bulkResult} expandedIds={expandedIds}
                                       toggleExpand={toggleExpand} filteredIssues={filteredIssues}
                                       sevCounts={sevCounts}/>}
        </div>
    );
}

// ─── Single Automation Result ───────────────────────────────────────────────

function SingleResult({result, filter, setFilter, filteredIssues, sevCounts}) {
    const counts = sevCounts(result.issues);
    const shown = filteredIssues(result.issues);

    return (
        <div>
            <SummaryBar valid={result.valid} errors={result.errorCount}
                        warnings={result.warningCount} infos={result.infoCount}
                        id={result.automationId}/>
            <FilterBar filter={filter} setFilter={setFilter} counts={counts} total={result.issues.length}/>
            {shown.length === 0
                ? <EmptyState filter={filter} total={result.issues.length}/>
                : shown.map((issue, i) => <IssueRow key={i} issue={issue}/>)
            }
        </div>
    );
}

// ─── Bulk Result ─────────────────────────────────────────────────────────────

function BulkResult({bulk, expandedIds, toggleExpand, filteredIssues, sevCounts}) {
    return (
        <div>
            {/* Summary */}
            <div style={{
                display: "flex", gap: 16, marginBottom: 20, flexWrap: "wrap",
            }}>
                {[
                    ["Total", bulk.total, "#c8d0db"],
                    ["Valid", bulk.validCount, "#34d399"],
                    ["Invalid", bulk.invalidCount, "#ff6b35"],
                ].map(([label, val, color]) => (
                    <div key={label} style={{
                        background: "#0f1318", border: "1px solid #1e2530",
                        borderRadius: 6, padding: "12px 20px", minWidth: 90,
                    }}>
                        <div style={{fontSize: 22, fontWeight: 700, color}}>{val}</div>
                        <div style={{
                            fontSize: 11,
                            color: "#4a5568",
                            letterSpacing: "0.1em",
                            textTransform: "uppercase"
                        }}>{label}</div>
                    </div>
                ))}
            </div>

            {/* Individual results */}
            {bulk.results.map(r => {
                const expanded = expandedIds.has(r.automationId);
                const counts = sevCounts(r.issues);
                return (
                    <div key={r.automationId} style={{
                        background: "#0f1318",
                        border: "1px solid " + (r.valid ? "#0d3020" : "#3a1008"),
                        borderRadius: 6, marginBottom: 8, overflow: "hidden",
                    }}>
                        {/* Row header */}
                        <div onClick={() => toggleExpand(r.automationId)} style={{
                            display: "flex", alignItems: "center", gap: 12,
                            padding: "12px 16px", cursor: "pointer",
                        }}>
              <span style={{
                  fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 3,
                  background: r.valid ? "#0d3020" : "#2a0a08",
                  color: r.valid ? "#34d399" : "#ff6b35",
                  border: "1px solid " + (r.valid ? "#1a5030" : "#5a1510"),
              }}>{r.valid ? "PASS" : "FAIL"}</span>
                            <span style={{flex: 1, fontSize: 13, color: "#9ca3af", fontFamily: "monospace"}}>
                {r.automationId}
              </span>
                            <SevPills counts={counts}/>
                            <span style={{color: "#4a5568", fontSize: 12}}>{expanded ? "▲" : "▼"}</span>
                        </div>
                        {expanded && r.issues.length > 0 && (
                            <div style={{borderTop: "1px solid #1e2530", padding: "8px 0"}}>
                                {r.issues.map((issue, i) => <IssueRow key={i} issue={issue} compact/>)}
                            </div>
                        )}
                        {expanded && r.issues.length === 0 && (
                            <div style={{
                                borderTop: "1px solid #1e2530", padding: "16px",
                                color: "#34d399", fontSize: 13, textAlign: "center",
                            }}>✓ No issues found</div>
                        )}
                    </div>
                );
            })}
        </div>
    );
}

// ─── Shared subcomponents ────────────────────────────────────────────────────

function SummaryBar({valid, errors, warnings, infos, id}) {
    return (
        <div style={{
            display: "flex", alignItems: "center", gap: 16,
            background: "#0f1318", border: "1px solid #1e2530",
            borderRadius: 6, padding: "14px 20px", marginBottom: 16,
            flexWrap: "wrap",
        }}>
            <div style={{
                fontSize: 13, fontWeight: 700, padding: "3px 12px", borderRadius: 4,
                background: valid ? "#062018" : "#1a0808",
                color: valid ? "#34d399" : "#ff6b35",
                border: "1px solid " + (valid ? "#104030" : "#4a1010"),
                letterSpacing: "0.1em",
            }}>{valid ? "✓ VALID" : "✕ INVALID"}</div>
            {id && <span style={{fontSize: 12, color: "#4a5568", fontFamily: "monospace"}}>{id}</span>}
            <div style={{flex: 1}}/>
            {[["ERROR", errors], ["WARNING", warnings], ["INFO", infos]].map(([s, c]) =>
                    c > 0 ? (
                        <div key={s} style={{display: "flex", alignItems: "center", gap: 5}}>
            <span style={{
                fontSize: 11, fontWeight: 700, color: SEV_META[s]?.color,
            }}>{SEV_META[s]?.icon}</span>
                            <span style={{fontSize: 13, color: "#9ca3af"}}>
              {c} {s.toLowerCase()}{c !== 1 ? "s" : ""}
            </span>
                        </div>
                    ) : null
            )}
        </div>
    );
}

function FilterBar({filter, setFilter, counts, total}) {
    const tabs = ["ALL", "FATAL", "ERROR", "WARNING", "INFO"];
    return (
        <div style={{display: "flex", gap: 4, marginBottom: 12, flexWrap: "wrap"}}>
            {tabs.map(t => {
                const cnt = t === "ALL" ? total : (counts[t] || 0);
                if (t !== "ALL" && cnt === 0) return null;
                const meta = SEV_META[t];
                const active = filter === t;
                return (
                    <button key={t} onClick={() => setFilter(t)} style={{
                        padding: "5px 12px", fontSize: 12, borderRadius: 4,
                        border: active
                            ? `1px solid ${meta?.border || "#2a6acc"}`
                            : "1px solid #1e2530",
                        background: active ? (meta?.bg || "#0d2a4a") : "transparent",
                        color: active ? (meta?.color || "#7ab8ff") : "#4a5568",
                        cursor: "pointer", fontFamily: "inherit",
                        transition: "all 0.15s",
                    }}>
                        {t === "ALL" ? "All" : meta?.label} {cnt > 0 && (
                        <span style={{
                            marginLeft: 4,
                            background: active ? (meta?.color || "#7ab8ff") : "#1e2530",
                            color: active ? (meta?.bg || "#000") : "#6b7280",
                            borderRadius: 10, padding: "1px 6px", fontSize: 11,
                        }}>{cnt}</span>
                    )}
                    </button>
                );
            })}
        </div>
    );
}

function IssueRow({issue, compact}) {
    const meta = SEV_META[issue.severity] || SEV_META.INFO;
    return (
        <div style={{
            display: "flex", gap: 12, alignItems: "flex-start",
            padding: compact ? "8px 16px" : "12px 16px",
            borderBottom: "1px solid #0f1318",
            background: "transparent",
        }}>
            {/* Severity badge */}
            <div style={{
                minWidth: 20, height: 20, borderRadius: 4,
                background: meta.bg, border: `1px solid ${meta.border}`,
                display: "flex", alignItems: "center", justifyContent: "center",
                fontSize: 11, fontWeight: 700, color: meta.color, flexShrink: 0,
                marginTop: 1,
            }}>{meta.icon}</div>

            <div style={{flex: 1, minWidth: 0}}>
                {/* Category + nodeId */}
                <div style={{display: "flex", gap: 8, alignItems: "center", marginBottom: 4, flexWrap: "wrap"}}>
          <span style={{
              fontSize: 10, letterSpacing: "0.12em", color: "#4a5568",
              textTransform: "uppercase",
          }}>{CAT_ICONS[issue.category]} {issue.category}</span>
                    {issue.nodeId && (
                        <span style={{
                            fontSize: 10, color: "#1e4060", background: "#080c12",
                            border: "1px solid #1a2535", borderRadius: 3,
                            padding: "1px 6px", fontFamily: "monospace",
                        }}>node:{issue.nodeId}</span>
                    )}
                    <span style={{
                        fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase",
                        color: meta.color, fontWeight: 600,
                    }}>{meta.label}</span>
                </div>
                {/* Message */}
                <div style={{fontSize: 13, color: "#c8d0db", lineHeight: 1.5}}>
                    {issue.message}
                </div>
            </div>
        </div>
    );
}

function SevPills({counts}) {
    return (
        <div style={{display: "flex", gap: 4}}>
            {Object.entries(counts).map(([s, c]) => {
                const meta = SEV_META[s];
                if (!meta || c === 0) return null;
                return (
                    <span key={s} style={{
                        fontSize: 11, padding: "1px 7px", borderRadius: 10,
                        background: meta.bg, color: meta.color,
                        border: `1px solid ${meta.border}`,
                    }}>{meta.icon} {c}</span>
                );
            })}
        </div>
    );
}

function EmptyState({filter, total}) {
    return (
        <div style={{
            textAlign: "center", padding: "40px 20px",
            color: filter === "ALL" ? "#34d399" : "#4a5568",
            fontSize: 14,
        }}>
            {filter === "ALL"
                ? "✓ Automation is valid — no issues found"
                : `No ${filter.toLowerCase()} issues (${total} total issue${total !== 1 ? "s" : ""} in other categories)`}
        </div>
    );
}