// AutomationSummaryBar.jsx
// AutomationAnalyticsList.jsx
import {useEffect, useState} from "react";
import {
    Box,
    Card,
    CardContent,
    Chip,
    Collapse,
    Divider,
    IconButton,
    LinearProgress,
    Skeleton,
    Stack,
    Tooltip,
    Typography,
} from "@mui/material";
import {
    AccessTime,
    BoltOutlined,
    CheckCircleOutline,
    ErrorOutline,
    ExpandMore,
    HourglassEmpty,
    SignalWifiStatusbarConnectedNoInternet4,
    SlowMotionVideo,
    WarningAmberOutlined,
} from "@mui/icons-material";
import {getAutomationAnalyticsSummary, getAutomationAnalyticsV2} from "../../services/apis.jsx";

// ─── Stat pill ────────────────────────────────────────────────────────────────
function Pill({icon, value, label, color, tooltip}) {
    return (
        <Tooltip title={tooltip} placement="bottom" arrow>
            <Stack
                direction="row"
                alignItems="center"
                spacing={0.75}
                sx={{
                    px: 1.25,
                    py: 0.6,
                    borderRadius: 1.5,
                    bgcolor: `${color}11`,
                    border: `1px solid ${color}28`,
                    cursor: "default",
                    userSelect: "none",
                    transition: "background .2s",
                    "&:hover": {bgcolor: `${color}1e`},
                }}
            >
                <Box sx={{color, display: "flex", fontSize: 13}}>{icon}</Box>
                <Typography
                    variant="caption"
                    sx={{fontWeight: 700, color, lineHeight: 1}}
                >
                    {value}
                </Typography>
                <Typography
                    variant="caption"
                    sx={{lineHeight: 1, fontSize: 10}}
                >
                    {label}
                </Typography>
            </Stack>
        </Tooltip>
    );
}

// ─── Component ────────────────────────────────────────────────────────────────
export default function AutomationSummaryBar() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getAutomationAnalyticsSummary()
            .then(setData)
            .catch(() => setData(null))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <Stack direction="row" spacing={1}>
                {[80, 70, 80, 90].map((w, i) => (
                    <Skeleton key={i} variant="rounded" width={w} height={28}/>
                ))}
            </Stack>
        );
    }

    if (!data) return null;

    const {total, healthy, warnings, errors, totalUndelivered, totalSlowEvals} = data;

    return (
        <Stack direction="row" flexWrap="wrap" gap={1} alignItems="center">
            {/* Healthy */}
            <Pill
                icon={<CheckCircleOutline fontSize="inherit"/>}
                value={`${healthy}/${total}`}
                label="healthy"
                color="#22c55e"
                tooltip="Automations with no errors or delivery failures"
            />

            {/* Warnings */}
            {warnings > 0 && (
                <Pill
                    icon={<WarningAmberOutlined fontSize="inherit"/>}
                    value={warnings}
                    label="warn"
                    color="#f59e0b"
                    tooltip="Automations with slow evaluations or minor issues"
                />
            )}

            {/* Errors */}
            {errors > 0 && (
                <Pill
                    icon={<ErrorOutline fontSize="inherit"/>}
                    value={errors}
                    label="error"
                    color="#ef4444"
                    tooltip="Automations with dispatch errors or evaluation exceptions"
                />
            )}

            {/* Undelivered */}
            {totalUndelivered > 0 && (
                <Pill
                    icon={<SignalWifiStatusbarConnectedNoInternet4 fontSize="inherit"/>}
                    value={totalUndelivered}
                    label="undelivered"
                    color="#f59e0b"
                    tooltip="Total actions with no device ACK across all automations"
                />
            )}

            {/* Slow evals */}
            {totalSlowEvals > 0 && (
                <Pill
                    icon={<WarningAmberOutlined fontSize="inherit"/>}
                    value={totalSlowEvals}
                    label="slow evals"
                    color="#60a5fa"
                    tooltip="Total evaluations exceeding the 200ms threshold"
                />
            )}
        </Stack>
    );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function timeAgo(isoString) {
    if (!isoString) return "never";
    const s = Math.floor((Date.now() - new Date(isoString)) / 1000);
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
}

function evalColor(ms) {
    if (!ms || ms < 100) return "#22c55e";
    if (ms < 250) return "#f59e0b";
    return "#ef4444";
}

function delayColor(ms) {
    if (!ms || ms < 500) return "#22c55e";
    if (ms < 1500) return "#f59e0b";
    return "#ef4444";
}

function statusColor(a) {
    if (a.errors > 0) return "#ef4444";
    if (a.undelivered > 0 || a.slowEvals > 0) return "#f59e0b";
    return "#22c55e";
}

// ─── Inline stat ──────────────────────────────────────────────────────────────
function Stat({icon, label, value, color, tooltip}) {
    return (
        <Tooltip title={tooltip} placement="top" arrow>
            <Stack direction="row" alignItems="center" spacing={0.5} sx={{cursor: "default"}}>
                <Box sx={{color, display: "flex", fontSize: 13}}>{icon}</Box>
                <Typography variant="caption" sx={{color: "#64748b",}}>
                    {label}
                </Typography>
                <Typography variant="caption" sx={{color, fontWeight: 700,}}>
                    {value}
                </Typography>
            </Stack>
        </Tooltip>
    );
}

// ─── Single row ───────────────────────────────────────────────────────────────
function AutomationRow({a}) {
    const [open, setOpen] = useState(false);
    const sc = statusColor(a);
    const hasIssue = a.errors > 0 || a.undelivered > 0 || a.slowEvals > 0;

    return (
        <Card
            variant="outlined"
            sx={{
                borderColor: hasIssue ? `${sc}44` : "#1e293b",
                borderRadius: 2,
                transition: "border-color .2s, box-shadow .2s",
                "&:hover": {
                    borderColor: `${sc}77`,
                    boxShadow: `0 0 0 1px ${sc}22`,
                },
            }}
        >
            <CardContent sx={{py: 1.25, px: 2, "&:last-child": {pb: 1.25}}}>

                {/* ── Header ── */}
                <Stack direction="row" alignItems="center" spacing={1.25}>

                    {/* Status dot */}
                    <Box sx={{
                        width: 7, height: 7, borderRadius: "50%",
                        backgroundColor: sc, flexShrink: 0,
                        boxShadow: `0 0 5px ${sc}`,
                    }}/>

                    {/* Name */}
                    <Typography
                        variant="body2"
                        sx={{
                            fontWeight: 600, color: "#f1f5f9", flexGrow: 1,
                            letterSpacing: "-0.02em",
                            fontSize: "0.8rem",
                        }}
                    >
                        {a.automationName}
                    </Typography>

                    {/* Last triggered */}
                    <Tooltip title={a.lastTriggered ?? "Never triggered"} arrow placement="top">
                        <Stack direction="row" alignItems="center" spacing={0.4} sx={{cursor: "default"}}>
                            <AccessTime sx={{fontSize: 11, color: "#334155"}}/>
                            <Typography variant="caption" sx={{color: "#334155",}}>
                                {timeAgo(a.lastTriggered)}
                            </Typography>
                        </Stack>
                    </Tooltip>

                    {/* Alert chips */}
                    {a.errors > 0 && (
                        <Chip label={`${a.errors} err`} size="small" sx={{
                            height: 17, fontSize: 9,
                            bgcolor: "#7f1d1d22", color: "#ef4444", border: "1px solid #ef444433",
                        }}/>
                    )}
                    {a.undelivered > 0 && (
                        <Chip label={`${a.undelivered} lost`} size="small" sx={{
                            height: 17, fontSize: 9,
                            bgcolor: "#78350f22", color: "#f59e0b", border: "1px solid #f59e0b33",
                        }}/>
                    )}
                    {a.slowEvals > 0 && (
                        <Chip label="slow" size="small" sx={{
                            height: 17, fontSize: 9,
                            bgcolor: "#1e3a5f22", color: "#60a5fa", border: "1px solid #60a5fa33",
                        }}/>
                    )}

                    {/* Expand */}
                    <IconButton size="small" onClick={() => setOpen(o => !o)}
                                sx={{color: "#334155", p: 0.25}}>
                        <ExpandMore sx={{
                            fontSize: 15,
                            transition: "transform .2s",
                            transform: open ? "rotate(180deg)" : "none",
                        }}/>
                    </IconButton>
                </Stack>

                {/* ── Expanded detail ── */}
                <Collapse in={open} unmountOnExit>
                    <Divider sx={{my: 1, borderColor: "#1e293b"}}/>

                    <Stack direction="row" flexWrap="wrap" gap={1.75} sx={{px: 0.25}}>
                        <Stat
                            icon={<SlowMotionVideo fontSize="inherit"/>}
                            label="eval"
                            value={a.avgEvalDurationMs != null ? `${a.avgEvalDurationMs}ms` : "—"}
                            color={evalColor(a.avgEvalDurationMs)}
                            tooltip="Average evaluation duration — >200ms means slow secondary Redis reads"
                        />
                        <Stat
                            icon={<HourglassEmpty fontSize="inherit"/>}
                            label="fire delay"
                            value={a.avgActionFireDelayMs != null ? `${a.avgActionFireDelayMs}ms` : "—"}
                            color={delayColor(a.avgActionFireDelayMs)}
                            tooltip="Average action dispatch latency to device"
                        />
                        <Stat
                            icon={<SignalWifiStatusbarConnectedNoInternet4 fontSize="inherit"/>}
                            label="undelivered"
                            value={a.undelivered ?? 0}
                            color={a.undelivered > 0 ? "#ef4444" : "#22c55e"}
                            tooltip="Actions with no device ACK within the confirmation window"
                        />
                        <Stat
                            icon={<ErrorOutline fontSize="inherit"/>}
                            label="errors"
                            value={a.errors ?? 0}
                            color={a.errors > 0 ? "#ef4444" : "#22c55e"}
                            tooltip="Dispatch errors or evaluation exceptions"
                        />
                        <Stat
                            icon={<BoltOutlined fontSize="inherit"/>}
                            label="triggers"
                            value={a.totalTriggers ?? 0}
                            color="#64748b"
                            tooltip="Total trigger count (all time)"
                        />
                        <Stat
                            icon={<WarningAmberOutlined fontSize="inherit"/>}
                            label="slow evals"
                            value={a.slowEvals ?? 0}
                            color={a.slowEvals > 0 ? "#60a5fa" : "#22c55e"}
                            tooltip="Evaluations that exceeded the 200ms threshold"
                        />
                    </Stack>

                    {/* Eval speed bar */}
                    <Box sx={{mt: 1.5, px: 0.25}}>
                        <Stack direction="row" justifyContent="space-between" sx={{mb: 0.4}}>
                            <Typography variant="caption" sx={{color: "#334155",}}>
                                eval speed
                            </Typography>
                            <Typography variant="caption" sx={{
                                color: evalColor(a.avgEvalDurationMs),
                                fontWeight: 700, fontSize: 10,
                            }}>
                                {!a.avgEvalDurationMs || a.avgEvalDurationMs < 100
                                    ? "fast"
                                    : a.avgEvalDurationMs < 250 ? "moderate" : "slow"}
                            </Typography>
                        </Stack>
                        <LinearProgress
                            variant="determinate"
                            value={Math.min(((a.avgEvalDurationMs ?? 0) / 400) * 100, 100)}
                            sx={{
                                height: 2, borderRadius: 2,
                                "& .MuiLinearProgress-bar": {
                                    backgroundColor: evalColor(a.avgEvalDurationMs), borderRadius: 2,
                                },
                            }}
                        />
                    </Box>
                </Collapse>
            </CardContent>
        </Card>
    );
}

// ─── Skeleton row ─────────────────────────────────────────────────────────────
function RowSkeleton() {
    return (
        <Card variant="outlined" sx={{borderRadius: 2}}>
            <CardContent sx={{py: 1.25, px: 2, "&:last-child": {pb: 1.25}}}>
                <Stack direction="row" alignItems="center" spacing={1.25}>
                    <Skeleton variant="circular" width={7} height={7}/>
                    <Skeleton variant="text" width={120}/>
                    <Box sx={{flexGrow: 1}}/>
                    <Skeleton variant="text" width={50}/>
                </Stack>
            </CardContent>
        </Card>
    );
}

// ─── Root component ───────────────────────────────────────────────────────────
export function AutomationAnalyticsList() {
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        getAutomationAnalyticsV2()
            .then(data => {
                const sorted = [...data].sort((a, b) => {
                    const rank = r => (r.errors > 0 ? 0 : r.undelivered > 0 || r.slowEvals > 0 ? 1 : 2);
                    return rank(a) - rank(b);
                });
                setRows(sorted);
            })
            .catch(() => setRows([]))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return (
            <Stack spacing={1}>
                {[1, 2, 3, 4].map(i => <RowSkeleton key={i}/>)}
            </Stack>
        );
    }

    if (!rows.length) {
        return (
            <Typography variant="caption" sx={{color: "#334155",}}>
                no automations found
            </Typography>
        );
    }

    return (
        <Stack spacing={1}>
            {rows.map(a => <AutomationRow key={a.automationId} a={a}/>)}
        </Stack>
    );
}