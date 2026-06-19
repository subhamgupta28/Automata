import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Alert,
    Badge,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Collapse,
    Divider,
    Drawer,
    FormControlLabel,
    Grid,
    IconButton,
    InputAdornment,
    LinearProgress,
    List,
    ListItemButton,
    ListItemText,
    Paper,
    Snackbar,
    Stack,
    Switch,
    Tab,
    Tabs,
    TextField,
    Tooltip,
    Typography,
    useMediaQuery,
    useTheme,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import AccountTreeIcon from "@mui/icons-material/AccountTree";
import CallSplitIcon from "@mui/icons-material/CallSplit";
import GroupsIcon from "@mui/icons-material/Groups";
import TuneIcon from "@mui/icons-material/Tune";
import MemoryIcon from "@mui/icons-material/Memory";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import CancelOutlinedIcon from "@mui/icons-material/CancelOutlined";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import StopIcon from "@mui/icons-material/Stop";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import PauseIcon from "@mui/icons-material/Pause";
import AlarmOffIcon from "@mui/icons-material/AlarmOff";
import FiberManualRecordIcon from "@mui/icons-material/FiberManualRecord";
import BoltIcon from "@mui/icons-material/Bolt";
import HistoryIcon from "@mui/icons-material/History";
import SearchIcon from "@mui/icons-material/Search";
import FlashOnIcon from "@mui/icons-material/FlashOn";
import FormatListBulletedIcon from "@mui/icons-material/FormatListBulleted";
import ElectricBoltIcon from "@mui/icons-material/ElectricBolt";
import DevicesIcon from "@mui/icons-material/Devices";
import {
    deleteAutomationSnooze,
    getAutomations,
    getAutomationStateAndPlan,
    postAutomationOverride,
    postAutomationSnooze,
} from "../../services/apis.jsx";
import {useAutomationWebSocket} from "./useAutomationWebSocket.jsx";

const getAccessToken = () => JSON.parse(localStorage.getItem("user"))?.access_token ?? "";

// ─── Constants ────────────────────────────────────────────────────────────────
const MAX_LOG = 50;
const MAX_ACTIONS = 100;
const DRAWER_WIDTH = 320;

const T = {
    surface: "rgba(255,255,255,0.03)",
    border: "rgba(255,255,255,0.08)",
};

// ─── Helpers ──────────────────────────────────────────────────────────────────
const fmtDate = d => d ? new Date(d).toLocaleTimeString() : "—";
const fmtAgo = ms => {
    if (!ms || ms <= 0) return "never";
    const s = Math.round((Date.now() - ms) / 1000);
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.round(s / 60)}m ago`;
    return `${Math.round(s / 3600)}h ago`;
};

function parseMemProgress(summary) {
    if (!summary) return null;
    const d = /DURATION:\s*(\d+)\/(\d+)/.exec(summary);
    if (d) return {pct: Math.min(100, Math.round(d[1] / d[2] * 100)), label: `${d[1]}s / ${d[2]}s`};
    const c = /CONSECUTIVE:\s*(\d+)\/(\d+)/.exec(summary);
    if (c) return {pct: Math.min(100, Math.round(c[1] / c[2] * 100)), label: `${c[1]} / ${c[2]} ticks`};
    return null;
}

const OUTCOME_COLOR = {
    TRIGGERED: "success", RESTORED: "warning", SKIPPED: "default",
    NOT_MET: "default", C1_NEGATIVE: "error", STATELESS_FIRE: "info", FALLBACK: "info",
};
const OUTCOME_ICON = {
    TRIGGERED: "🚀", RESTORED: "⏹", SKIPPED: "⏭",
    NOT_MET: "💤", C1_NEGATIVE: "⛔", STATELESS_FIRE: "⚡", FALLBACK: "↩",
};

// ─── Shared small components ──────────────────────────────────────────────────
function Mono({children, sx = {}}) {
    return <Typography sx={{fontSize: 12, ...sx}}>{children}</Typography>;
}

function SLabel({children}) {
    return (
        <Typography sx={{
            fontSize: 10, fontWeight: 600, letterSpacing: "1px",
            textTransform: "uppercase", color: "text.disabled", mb: 1,
        }}>{children}</Typography>
    );
}

function ResultDot({value}) {
    if (value === true) return <CheckCircleOutlineIcon sx={{fontSize: 15, color: "success.main"}}/>;
    if (value === false) return <CancelOutlinedIcon sx={{fontSize: 15, color: "error.main"}}/>;
    return <HelpOutlineIcon sx={{fontSize: 15, color: "text.disabled"}}/>;
}

// ─── Connection status pill ───────────────────────────────────────────────────
function ConnPill({status}) {
    const map = {
        connected: {color: "success", label: "live"},
        connecting: {color: "warning", label: "connecting…"},
        disconnected: {color: "default", label: "disconnected"},
    };
    const {color, label} = map[status] || map.disconnected;
    return (
        <Chip
            icon={<FiberManualRecordIcon sx={{fontSize: "10px !important"}}/>}
            label={label} size="small" color={color}
            variant={status === "connected" ? "filled" : "outlined"}
            sx={{fontSize: 10, height: 20}}
        />
    );
}

// ─── NodeCard / BranchCard / LogRow / ActionFiredRow / ActionsTab / OverrideTab
// (unchanged — copied verbatim from original)

function NodeCard({node, prevNode, flash}) {
    const [open, setOpen] = useState(true);
    const prog = parseMemProgress(node.memorySummary);
    const changed = prevNode && prevNode.lastRawResult !== node.lastRawResult;
    const borderColor = node.lastRawResult === true ? "success.main"
        : node.lastRawResult === false ? "error.main" : "divider";
    return (
        <Card elevation={0} sx={{
            mb: 1, borderRadius: 2, background: T.surface,
            border: "0.5px solid", borderColor,
            transition: "border-color 0.3s, box-shadow 0.3s",
            boxShadow: flash && changed ? "0 0 0 2px rgba(255,213,33,0.5)" : "none",
        }}>
            <CardContent sx={{p: "10px 14px !important"}}>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                    <Stack direction="row" alignItems="center" spacing={1}>
                        <ResultDot value={node.lastRawResult}/>
                        <Box>
                            <Mono sx={{color: "text.primary"}}>{node.nodeId}</Mono>
                            <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                                {node.conditionType}{node.triggerKey ? ` · ${node.triggerKey}` : ""}
                            </Typography>
                        </Box>
                    </Stack>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                        {node.stateful &&
                            <Chip label="stateful" size="small" variant="outlined" sx={{fontSize: 10, height: 20}}/>}
                        {node.wasActive
                            ? <Chip label="active" size="small" color="success" sx={{fontSize: 10, height: 20}}/>
                            : <Chip label="inactive" size="small" sx={{fontSize: 10, height: 20}}/>}
                        {node.hasMemoryPolicy && (
                            <IconButton size="small" onClick={() => setOpen(p => !p)} sx={{p: 0.5}}>
                                {open ? <ExpandLessIcon sx={{fontSize: 15}}/> : <ExpandMoreIcon sx={{fontSize: 15}}/>}
                            </IconButton>
                        )}
                    </Stack>
                </Stack>
                {node.hasMemoryPolicy && (
                    <Collapse in={open}>
                        <Box sx={{
                            mt: 1.5,
                            p: "8px 10px",
                            borderRadius: 1.5,
                            background: "rgba(255,255,255,0.04)",
                            border: `0.5px solid ${T.border}`
                        }}>
                            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: 0.5}}>
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                    <MemoryIcon sx={{fontSize: 13, color: "primary.main"}}/>
                                    <Typography sx={{fontSize: 11, color: "text.secondary"}}>
                                        {node.memoryPolicyType?.replace(/_/g, " ").toLowerCase()}
                                    </Typography>
                                </Stack>
                                <Mono sx={{color: "text.secondary"}}>{node.memorySummary || "—"}</Mono>
                            </Stack>
                            {prog && (
                                <>
                                    <LinearProgress variant="determinate" value={prog.pct} sx={{
                                        height: 4, borderRadius: 2,
                                        backgroundColor: "rgba(255,255,255,0.08)",
                                        "& .MuiLinearProgress-bar": {
                                            borderRadius: 2,
                                            backgroundColor: prog.pct >= 100 ? "success.main" : "primary.main"
                                        },
                                    }}/>
                                    <Typography
                                        sx={{fontSize: 10, color: "text.disabled", mt: 0.5}}>{prog.label}</Typography>
                                </>
                            )}
                            {node.firstTrueEpochMs > 0 && (
                                <Typography sx={{fontSize: 10, color: "text.disabled"}}>continuous
                                    since {fmtAgo(node.firstTrueEpochMs)}</Typography>
                            )}
                            {node.consecutiveTrueCount > 0 && (
                                <Typography
                                    sx={{fontSize: 10, color: "text.disabled"}}>{node.consecutiveTrueCount} consecutive
                                    ticks</Typography>
                            )}
                        </Box>
                    </Collapse>
                )}
            </CardContent>
        </Card>
    );
}

function BranchCard({branch, prevBranch, flash}) {
    const st = (branch.state || "IDLE").toUpperCase();
    const changed = prevBranch && prevBranch.state !== branch.state;
    const color = st === "ACTIVE" ? "success.main" : st === "HOLDING" ? "warning.main" : "divider";
    const scheduleLabel = () => {
        if (branch.scheduleType === "range" && branch.fromTime) return `${branch.fromTime} – ${branch.toTime}`;
        if (branch.scheduleType === "interval" && branch.intervalMinutes) return `every ${branch.intervalMinutes}min`;
        return branch.scheduleType || "data";
    };
    return (
        <Card elevation={0} sx={{
            mb: 1, borderRadius: 2, background: T.surface,
            border: "0.5px solid", borderColor: color,
            transition: "border-color 0.3s, box-shadow 0.3s",
            boxShadow: flash && changed ? "0 0 0 2px rgba(255,213,33,0.5)" : "none",
        }}>
            <CardContent sx={{p: "10px 14px !important"}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                        <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap
                               sx={{mb: 0.5}}>
                            <Mono sx={{color: "text.primary"}}>{branch.gateNodeId}</Mono>
                            <Chip label={branch.logicType} size="small" color="info" sx={{fontSize: 10, height: 20}}/>
                            <Chip label={`pri ${branch.priority}`} size="small" variant="outlined"
                                  sx={{fontSize: 10, height: 20}}/>
                        </Stack>
                        <Typography sx={{fontSize: 11, color: "text.secondary"}}>{scheduleLabel()}</Typography>
                        <Stack direction="row" spacing={0.5} sx={{mt: 0.5}}>
                            <Chip label={`+${branch.positiveActionsCount}`} size="small" color="success"
                                  variant="outlined" sx={{fontSize: 10, height: 18}}/>
                            <Chip label={`-${branch.negativeActionsCount}`} size="small" color="error"
                                  variant="outlined" sx={{fontSize: 10, height: 18}}/>
                        </Stack>
                    </Box>
                    <Chip label={st} size="small"
                          color={st === "ACTIVE" ? "success" : st === "HOLDING" ? "warning" : "default"}
                          sx={{fontSize: 11}}/>
                </Stack>
                {st === "HOLDING" && (
                    <Box sx={{mt: 1.5}}>
                        <LinearProgress sx={{
                            height: 3, borderRadius: 2,
                            backgroundColor: "rgba(255,255,255,0.08)",
                            "& .MuiLinearProgress-bar": {borderRadius: 2, backgroundColor: "warning.main"},
                        }}/>
                        <Typography sx={{fontSize: 10, color: "text.disabled", mt: 0.5}}>running — duration key
                            active</Typography>
                    </Box>
                )}
            </CardContent>
        </Card>
    );
}

function LogRow({event}) {
    const icon = OUTCOME_ICON[event.outcome] || "•";
    const color = OUTCOME_COLOR[event.outcome] || "default";
    return (
        <Box sx={{display: "flex", gap: 1, alignItems: "baseline", py: "5px", borderBottom: `0.5px solid ${T.border}`}}>
            <Typography sx={{
                fontSize: 11,
                color: "text.disabled",
                minWidth: 68,

            }}>{fmtDate(event.evaluatedAt)}</Typography>
            <Chip label={`${icon} ${event.outcome}`} size="small" color={color}
                  sx={{fontSize: 10, height: 18, minWidth: 90}}/>
            <Typography sx={{fontSize: 11, color: "text.secondary",}}>
                {event.c1True ? "c1✓" : "c1✗"}
                {event.reason ? ` · ${event.reason}` : ""}
                {event.evalDurationMs != null ? ` · ${event.evalDurationMs}ms` : ""}
            </Typography>
        </Box>
    );
}

function ActionFiredRow({event, isNew}) {
    const [open, setOpen] = useState(false);
    const hasData = event.data != null && event.data !== "";
    return (
        <Box sx={{
            py: "6px", borderBottom: `0.5px solid ${T.border}`,
            transition: "background 0.4s",
            background: isNew ? "rgba(99,202,183,0.06)" : "transparent",
        }}>
            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" useFlexGap>
                <Typography
                    sx={{fontSize: 11, color: "text.disabled", minWidth: 68, flexShrink: 0}}>
                    {fmtDate(event.firedAt)}
                </Typography>
                {event.success
                    ? <CheckCircleOutlineIcon sx={{fontSize: 14, color: "success.main", flexShrink: 0}}/>
                    : <CancelOutlinedIcon sx={{fontSize: 14, color: "error.main", flexShrink: 0}}/>}
                <Stack direction="row" spacing={0.5} alignItems="center" sx={{flexShrink: 0}}>
                    <DevicesIcon sx={{fontSize: 13, color: "text.disabled"}}/>
                    <Typography sx={{fontSize: 12, color: "text.primary",}}>
                        {event.deviceName || event.deviceId}
                    </Typography>
                </Stack>
                <Chip label={event.key} size="small" color="info" variant="outlined"
                      sx={{fontSize: 10, height: 18,}}/>
                {hasData && (
                    <Box sx={{display: "flex", alignItems: "center", gap: 0.5, ml: "auto"}}>
                        <Mono sx={{
                            color: "text.disabled",
                            maxWidth: 160,
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                            fontSize: 11
                        }}>
                            {open ? "" : event.data}
                        </Mono>
                        <IconButton size="small" onClick={() => setOpen(p => !p)} sx={{p: 0.25}}>
                            {open ? <ExpandLessIcon sx={{fontSize: 13}}/> : <ExpandMoreIcon sx={{fontSize: 13}}/>}
                        </IconButton>
                    </Box>
                )}
                {event.nodeId &&
                    <Mono sx={{color: "text.disabled", fontSize: 10, ml: hasData ? 0 : "auto"}}>{event.nodeId}</Mono>}
            </Stack>
            {hasData && (
                <Collapse in={open}>
                    <Box sx={{
                        mt: 1,
                        ml: "76px",
                        p: "8px 10px",
                        borderRadius: 1.5,
                        background: "rgba(255,255,255,0.04)",
                        border: `0.5px solid ${T.border}`
                    }}>
                        <SLabel>payload</SLabel>
                        <Mono sx={{color: "text.secondary", whiteSpace: "pre-wrap", wordBreak: "break-all"}}>
                            {(() => {
                                try {
                                    return JSON.stringify(JSON.parse(event.data), null, 2);
                                } catch {
                                    return event.data;
                                }
                            })()}
                        </Mono>
                        {event.traceId &&
                            <Mono sx={{color: "text.disabled", fontSize: 10, mt: 1}}>trace: {event.traceId}</Mono>}
                    </Box>
                </Collapse>
            )}
        </Box>
    );
}

function ActionsTab({actionLog, onClear, connStatus}) {
    const [filterFailed, setFilterFailed] = useState(false);
    const [newIds, setNewIds] = useState(new Set());
    const prevLenRef = useRef(0);

    useEffect(() => {
        if (actionLog.length > prevLenRef.current) {
            const added = new Set(actionLog.slice(0, actionLog.length - prevLenRef.current).map((_, i) => i));
            setNewIds(added);
            const t = setTimeout(() => setNewIds(new Set()), 800);
            prevLenRef.current = actionLog.length;
            return () => clearTimeout(t);
        }
        prevLenRef.current = actionLog.length;
    }, [actionLog]);

    const filtered = filterFailed ? actionLog.filter(e => !e.success) : actionLog;
    const groups = useMemo(() => {
        const out = [];
        let currentTrace = null, currentGroup = [];
        for (const evt of filtered) {
            if (evt.traceId && evt.traceId !== currentTrace) {
                if (currentGroup.length) out.push({traceId: currentTrace, events: currentGroup});
                currentTrace = evt.traceId;
                currentGroup = [evt];
            } else {
                currentGroup.push(evt);
            }
        }
        if (currentGroup.length) out.push({traceId: currentTrace, events: currentGroup});
        return out;
    }, [filtered]);

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: 1.5}}>
                <Stack direction="row" spacing={1} alignItems="center">
                    <FormControlLabel
                        control={<Switch size="small" checked={filterFailed}
                                         onChange={e => setFilterFailed(e.target.checked)}/>}
                        label={<Typography sx={{fontSize: 12}}>Failed only</Typography>}
                    />
                    <Chip label={`${actionLog.length} dispatched`} size="small"
                          color={actionLog.length > 0 ? "primary" : "default"} sx={{fontSize: 10, height: 20}}/>
                    {actionLog.filter(e => !e.success).length > 0 && (
                        <Chip label={`${actionLog.filter(e => !e.success).length} failed`} size="small" color="error"
                              sx={{fontSize: 10, height: 20}}/>
                    )}
                </Stack>
                <Button size="small" onClick={onClear} sx={{fontSize: 11, color: "text.secondary"}}>Clear</Button>
            </Stack>
            {filtered.length === 0 ? (
                <Box sx={{py: 6, textAlign: "center"}}>
                    <ElectricBoltIcon sx={{fontSize: 36, color: "text.disabled", mb: 1}}/>
                    <Typography color="text.disabled" fontSize={13}>
                        {connStatus === "connected" ? (filterFailed ? "No failed actions yet." : "Waiting for actions to fire…") : "Connect to see live action dispatches"}
                    </Typography>
                </Box>
            ) : (
                groups.map((group, gi) => (
                    <Box key={group.traceId || gi} sx={{mb: 1.5}}>
                        {group.traceId && (
                            <Stack direction="row" spacing={0.75} alignItems="center" sx={{mb: 0.5}}>
                                <Box sx={{
                                    height: "1px",
                                    flex: 1,
                                    background: `linear-gradient(to right, ${T.border}, transparent)`
                                }}/>
                                <Mono sx={{color: "text.disabled", fontSize: 10}}>trace {group.traceId.slice(-8)}</Mono>
                                <Chip label={`${group.events.length} action${group.events.length !== 1 ? "s" : ""}`}
                                      size="small" variant="outlined" sx={{fontSize: 10, height: 16}}/>
                                <Box sx={{
                                    height: "1px",
                                    flex: 1,
                                    background: `linear-gradient(to left, ${T.border}, transparent)`
                                }}/>
                            </Stack>
                        )}
                        {group.events.map((evt, ei) => (
                            <ActionFiredRow key={ei} event={evt} isNew={gi === 0 && newIds.has(ei)}/>
                        ))}
                    </Box>
                ))
            )}
        </Box>
    );
}

function OverrideTab({automationId, hasCoalition, onSuccess}) {
    const [loading, setLoading] = useState(null);
    const [snoozeMin, setSnoozeMin] = useState("");

    const doOverride = async action => {
        setLoading(action);
        try {
            const d = await postAutomationOverride(automationId, action);
            onSuccess(d.success ? d.message : "Error: " + d.message, d.success);
        } catch {
            onSuccess("Request failed", false);
        } finally {
            setLoading(null);
        }
    };
    const doSnooze = async min => {
        setLoading("snooze");
        try {
            const d = await postAutomationSnooze(automationId, parseInt(min));
            onSuccess(d.message, d.success);
        } catch {
            onSuccess("Request failed", false);
        } finally {
            setLoading(null);
        }
    };
    const doClearSnooze = async () => {
        setLoading("clear");
        try {
            const d = await deleteAutomationSnooze(automationId);
            onSuccess(d.message, d.success);
        } catch {
            onSuccess("Request failed", false);
        } finally {
            setLoading(null);
        }
    };
    const Btn = ({action, label, icon, color = "inherit"}) => (
        <Button fullWidth variant="outlined" color={color} size="small"
                startIcon={loading === action ? <CircularProgress size={12}/> : icon}
                disabled={loading !== null} onClick={() => doOverride(action)}
                sx={{fontSize: 11, fontWeight: 500, justifyContent: "flex-start"}}>
            {label}
        </Button>
    );
    return (
        <Stack spacing={1.5}>
            <Card elevation={0}
                  sx={{border: "0.5px solid", borderColor: "divider", borderRadius: 2, background: T.surface}}>
                <CardContent sx={{p: "12px 14px !important"}}>
                    <SLabel>state machine</SLabel>
                    <Grid container spacing={1}>
                        <Grid item xs={6}><Btn action="FORCE_ACTIVE" label="Force active" icon={<PlayArrowIcon/>}
                                               color="success"/></Grid>
                        <Grid item xs={6}><Btn action="FORCE_IDLE" label="Force idle" icon={<StopIcon/>}/></Grid>
                        <Grid item xs={6}><Btn action="RESET" label="Full reset" icon={<RestartAltIcon/>}
                                               color="error"/></Grid>
                        <Grid item xs={6}><Btn action="RESET_MEMORY" label="Reset memory" icon={<MemoryIcon/>}/></Grid>
                        {hasCoalition && <Grid item xs={12}><Btn action="RESET_COALITION" label="Reset coalition"
                                                                 icon={<GroupsIcon/>}/></Grid>}
                    </Grid>
                </CardContent>
            </Card>
            <Card elevation={0}
                  sx={{border: "0.5px solid", borderColor: "divider", borderRadius: 2, background: T.surface}}>
                <CardContent sx={{p: "12px 14px !important"}}>
                    <SLabel>snooze</SLabel>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{mb: 1.5}}>
                        {[15, 30, 60, 120].map(m => (
                            <Button key={m} variant="outlined" size="small" disabled={loading !== null}
                                    startIcon={loading === "snooze" ? <CircularProgress size={11}/> : <PauseIcon/>}
                                    onClick={() => doSnooze(m)} sx={{fontSize: 11}}>{m}m</Button>
                        ))}
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center">
                        <TextField size="small" type="number" placeholder="custom min" value={snoozeMin}
                                   onChange={e => setSnoozeMin(e.target.value)} inputProps={{min: 1, max: 1440}}
                                   sx={{flex: 1, "& input": {fontSize: 12}}}/>
                        <Button variant="outlined" size="small" disabled={loading !== null || !snoozeMin}
                                onClick={() => {
                                    doSnooze(snoozeMin);
                                    setSnoozeMin("");
                                }} sx={{fontSize: 11}}>Set</Button>
                        <Button variant="outlined" color="error" size="small" disabled={loading !== null}
                                startIcon={loading === "clear" ? <CircularProgress size={11}/> : <AlarmOffIcon/>}
                                onClick={doClearSnooze} sx={{fontSize: 11, whiteSpace: "nowrap"}}>Clear</Button>
                    </Stack>
                </CardContent>
            </Card>
        </Stack>
    );
}

function AutomationListPanel({liveSummaries, onSelect, loading}) {
    const [automations, setAutomations] = useState([]);
    const [listLoading, setListLoading] = useState(false);
    const [search, setSearch] = useState("");

    const fetchList = useCallback(async () => {
        setListLoading(true);
        try {
            setAutomations(await getAutomations());
        } catch (e) {
            console.error("Failed to fetch automations", e);
        } finally {
            setListLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchList();
    }, [fetchList]);

    const filtered = useMemo(() => {
        const q = search.toLowerCase();
        return automations.filter(a => !q || a.name?.toLowerCase().includes(q) || a.id?.toLowerCase().includes(q));
    }, [automations, search]);

    const liveCount = Object.keys(liveSummaries).length;

    return (
        <Box sx={{display: "flex", flexDirection: "column", height: "100%"}}>
            <Box sx={{p: "12px 14px", borderBottom: `0.5px solid ${T.border}`}}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: 1.5}}>
                    <Stack direction="row" spacing={0.75} alignItems="center">
                        <Typography sx={{fontSize: 13, fontWeight: 500}}>Automations</Typography>
                        {liveCount > 0 && (
                            <Tooltip title={`${liveCount} automation(s) receiving live events`}>
                                <Chip icon={<FlashOnIcon sx={{fontSize: "11px !important"}}/>} label={liveCount}
                                      size="small" color="success" sx={{fontSize: 10, height: 18}}/>
                            </Tooltip>
                        )}
                    </Stack>
                    <Tooltip title="Refresh list">
                        <IconButton size="small" onClick={fetchList} disabled={listLoading}>
                            {listLoading ? <CircularProgress size={13}/> : <RefreshIcon sx={{fontSize: 15}}/>}
                        </IconButton>
                    </Tooltip>
                </Stack>
                <TextField fullWidth size="small" placeholder="Search by name or ID…"
                           value={search} onChange={e => setSearch(e.target.value)}
                           InputProps={{
                               startAdornment: <InputAdornment position="start"><SearchIcon
                                   sx={{fontSize: 16, color: "text.disabled"}}/></InputAdornment>
                           }}
                           sx={{"& input": {fontSize: 12}}}/>
            </Box>
            <Box sx={{flex: 1, overflowY: "auto"}}>
                {listLoading &&
                    <Box sx={{py: 3, display: "flex", justifyContent: "center"}}><CircularProgress size={18}/></Box>}
                {!listLoading && filtered.length === 0 && (
                    <Box sx={{py: 4, textAlign: "center"}}>
                        <Typography color="text.disabled"
                                    fontSize={12}>{search ? "No matches" : "No automations found"}</Typography>
                    </Box>
                )}
                <List dense disablePadding>
                    {filtered.map(a => {
                        const summary = liveSummaries[a.id];
                        const isEnabled = a.isEnabled;
                        const outcome = summary?.outcome;
                        return (
                            <ListItemButton key={a.id} onClick={() => onSelect(a)} disabled={loading}
                                            sx={{
                                                borderBottom: `0.5px solid ${T.border}`,
                                                py: "10px",
                                                px: "14px",
                                                "&:hover": {background: "rgba(255,255,255,0.04)"},
                                                transition: "background 0.12s"
                                            }}>
                                <ListItemText
                                    primary={
                                        <Stack direction="row" alignItems="center" spacing={0.75} flexWrap="wrap"
                                               useFlexGap>
                                            <Typography sx={{
                                                fontSize: 13,
                                                fontWeight: 500,
                                                lineHeight: 1.3
                                            }}>{a.name}</Typography>
                                            {!isEnabled && <Chip label="disabled" size="small" color="default"
                                                                 sx={{fontSize: 10, height: 18}}/>}
                                            {outcome &&
                                                <Chip label={`${OUTCOME_ICON[outcome] || ""} ${outcome}`} size="small"
                                                      color={OUTCOME_COLOR[outcome] || "default"}
                                                      sx={{fontSize: 10, height: 18}}/>}
                                        </Stack>
                                    }
                                    secondary={
                                        <Stack direction="row" alignItems="center" spacing={0.75} sx={{mt: 0.25}}
                                               flexWrap="wrap" useFlexGap>
                                            <Mono sx={{color: "text.disabled", fontSize: 11}}>{a.id?.slice(-12)}</Mono>
                                            {a.trigger?.deviceId && <Typography sx={{
                                                fontSize: 11,
                                                color: "text.disabled"
                                            }}>· {a.trigger.name || a.trigger.deviceId}</Typography>}
                                            {summary && <Typography sx={{
                                                fontSize: 10,
                                                color: "text.disabled",

                                            }}>· {new Date(summary.evaluatedAt).toLocaleTimeString()}</Typography>}
                                        </Stack>
                                    }
                                />
                            </ListItemButton>
                        );
                    })}
                </List>
            </Box>
        </Box>
    );
}

// ─── Main component ───────────────────────────────────────────────────────────
export function AutomationLiveInspector({defaultId = ""}) {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down("md"));

    const [liveSummaries, setLiveSummaries] = useState({});
    const [drawerOpen, setDrawerOpen] = useState(!isMobile);

    useEffect(() => {
        if (isMobile) setDrawerOpen(false);
    }, [isMobile]);

    // ── Automation selection ──────────────────────────────────────────────────
    const [inputId, setInputId] = useState(defaultId);
    const [automationId, setAutomationId] = useState(defaultId);
    const resolvedIdRef = useRef(defaultId);

    // ── HTTP state ────────────────────────────────────────────────────────────
    const [httpState, setHttpState] = useState(null);
    const [planData, setPlanData] = useState(null);
    const [httpLoading, setHttpLoading] = useState(false);
    const [httpError, setHttpError] = useState(null);

    // ── Live event state ──────────────────────────────────────────────────────
    const [liveEvent, setLiveEvent] = useState(null);
    const prevEventRef = useRef(null);
    const [flash, setFlash] = useState(false);
    const [log, setLog] = useState([]);
    const [actionLog, setActionLog] = useState([]);
    const [showSkipped, setShowSkipped] = useState(false);

    // ── UI state ──────────────────────────────────────────────────────────────
    const [tab, setTab] = useState(0);
    const [toast, setToast] = useState({open: false, message: "", severity: "success"});

    // ── Eval event handler ────────────────────────────────────────────────────
    const handleEvalEvent = useCallback((event) => {
        setLiveEvent(prev => {
            prevEventRef.current = prev;
            return event;
        });
        setFlash(true);
        setTimeout(() => setFlash(false), 800);
        setLog(prev => [event, ...prev].slice(0, MAX_LOG));
    }, []);

    // ── Action event handler ──────────────────────────────────────────────────
    const handleActionEvent = useCallback((event) => {
        setActionLog(prev => [event, ...prev].slice(0, MAX_ACTIONS));
    }, []);

    // ─────────────────────────────────────────────────────────────────────────
    // 🔌  WebSocket — all connection logic lives in the hook.
    //
    //     onReconnect must NOT close over `subscribe` directly — the hook
    //     hasn't returned yet when the callback is first constructed, which
    //     causes "Cannot access 'subscribe' before initialization".
    //     We use a ref instead: subscribeRef is updated every render so
    //     onReconnect always calls the real, initialised function.
    // ─────────────────────────────────────────────────────────────────────────
    const subscribeRef = useRef(null);

    const {connStatus, subscribe, unsubscribe} = useAutomationWebSocket({
        getToken: getAccessToken,

        onBroadcast: useCallback((event) => {
            setLiveSummaries(prev => ({...prev, [event.automationId]: event}));
        }, []),

        // No `subscribe` in the dep array — we read it via subscribeRef instead.
        onReconnect: useCallback(() => {
            const id = resolvedIdRef.current;
            if (!id || !subscribeRef.current) return;
            setTimeout(() => {
                subscribeRef.current(`/topic/automation.${id}`, handleEvalEvent, "eval");
                subscribeRef.current(`/topic/automation.${id}.actions`, handleActionEvent, "action");
            }, 0);
        }, [handleEvalEvent, handleActionEvent]),
    });

    // Keep ref pointing at the latest subscribe after every render.
    subscribeRef.current = subscribe;

    // ── Subscribe helper (reused by select + manual fetch) ───────────────────
    const subscribeToAutomation = useCallback((id) => {
        unsubscribe("eval");
        unsubscribe("action");
        subscribe(`/topic/automation.${id}`, handleEvalEvent, "eval");
        subscribe(`/topic/automation.${id}.actions`, handleActionEvent, "action");
    }, [subscribe, unsubscribe, handleEvalEvent, handleActionEvent]);

    // ── HTTP fetch ────────────────────────────────────────────────────────────
    const fetchHttp = useCallback(async (id) => {
        const target = (id || resolvedIdRef.current || "").trim();
        if (!target) {
            setHttpError("Enter an automation ID");
            return;
        }
        resolvedIdRef.current = target;
        setHttpLoading(true);
        setHttpError(null);
        try {
            const {state, plan} = await getAutomationStateAndPlan(target);
            setHttpState(state);
            setPlanData(plan);
            setAutomationId(target);
        } catch (e) {
            setHttpError(e.message);
            setHttpState(null);
            setPlanData(null);
        } finally {
            setHttpLoading(false);
        }
    }, []);

    // Re-subscribe when automationId changes (e.g. after fetchHttp sets it)
    useEffect(() => {
        if (automationId) subscribeToAutomation(automationId);
    }, [automationId, subscribeToAutomation]);

    useEffect(() => {
        if (defaultId) fetchHttp(defaultId);
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // ── Handlers ─────────────────────────────────────────────────────────────
    const resetLiveState = () => {
        setLiveEvent(null);
        prevEventRef.current = null;
        setLog([]);
        setActionLog([]);
    };

    const handleFetch = () => {
        const id = inputId.trim();
        if (!id) {
            setHttpError("Enter an automation ID");
            return;
        }
        resolvedIdRef.current = id;
        setAutomationId(id);
        resetLiveState();
        fetchHttp(id);
        subscribeToAutomation(id);
    };

    const handleSelectAutomation = useCallback((automation) => {
        const id = automation.id;
        setInputId(id);
        resolvedIdRef.current = id;
        setAutomationId(id);
        resetLiveState();
        setTab(0);
        fetchHttp(id);
        subscribeToAutomation(id);
        if (isMobile) setDrawerOpen(false);
    }, [isMobile, fetchHttp, subscribeToAutomation]);

    // ── Derived state ─────────────────────────────────────────────────────────
    const currentNodes = liveEvent?.conditionNodes || httpState?.conditionNodes || [];
    const currentBranches = liveEvent?.branchStates || httpState?.branches || [];
    const currentOutcome = liveEvent?.outcome || httpState?.lastEvalSnapshot?.outcome;
    const currentTopState = liveEvent?.topLevelState || httpState?.topLevelState;
    const autoName = liveEvent?.automationName || httpState?.automationName || planData?.automationName || automationId;

    const filteredLog = useMemo(() =>
            showSkipped ? log : log.filter(e => e.outcome !== "SKIPPED" && e.outcome !== "NOT_MET"),
        [log, showSkipped]);

    const showToast = (message, success = true) => {
        setToast({open: true, message, severity: success ? "success" : "error"});
        if (success) setTimeout(() => fetchHttp(resolvedIdRef.current), 600);
    };

    // ── Tabs ──────────────────────────────────────────────────────────────────
    const failedActionsCount = actionLog.filter(e => !e.success).length;
    const tabs = [
        {label: "Condition tree", icon: <AccountTreeIcon sx={{fontSize: 15}}/>},
        {label: "Branches", icon: <CallSplitIcon sx={{fontSize: 15}}/>},
        {
            label: "Live log",
            icon: <Badge badgeContent={log.length} color="primary" max={99}><HistoryIcon sx={{fontSize: 15}}/></Badge>
        },
        {
            label: "Actions fired",
            icon: <Badge badgeContent={failedActionsCount > 0 ? failedActionsCount : actionLog.length}
                         color={failedActionsCount > 0 ? "error" : "primary"} max={99}><ElectricBoltIcon
                sx={{fontSize: 15}}/></Badge>
        },
        {label: "Coalition", icon: <GroupsIcon sx={{fontSize: 15}}/>},
        {label: "Override", icon: <TuneIcon sx={{fontSize: 15}}/>},
    ];

    const hasData = httpState !== null;
    const listPanel = (
        <AutomationListPanel liveSummaries={liveSummaries} onSelect={handleSelectAutomation} loading={httpLoading}/>
    );

    return (
        <Box sx={{display: "flex", height: "100vh", overflow: "hidden"}}>

            {/* Sidebar */}
            {isMobile ? (
                <Drawer open={drawerOpen} onClose={() => setDrawerOpen(false)} PaperProps={{sx: {width: DRAWER_WIDTH}}}>
                    {listPanel}
                </Drawer>
            ) : (
                <Box sx={{
                    width: drawerOpen ? DRAWER_WIDTH : 0, flexShrink: 0, overflow: "hidden",
                    borderRight: drawerOpen ? `0.5px solid ${T.border}` : "none", transition: "width 0.22s ease"
                }}>
                    <Box sx={{width: DRAWER_WIDTH, height: "100%", display: "flex", flexDirection: "column"}}>
                        {listPanel}
                    </Box>
                </Box>
            )}

            {/* Inspector pane */}
            <Box sx={{flex: 1, overflowY: "auto"}}>
                <Box sx={{p: 2}}>
                    <Stack direction="row" alignItems="flex-start" spacing={1} sx={{mb: 2}}>
                        <Tooltip title={drawerOpen ? "Hide list" : "Show automation list"}>
                            <IconButton size="small" onClick={() => setDrawerOpen(p => !p)} sx={{mt: "6px"}}>
                                <FormatListBulletedIcon sx={{fontSize: 18}}/>
                            </IconButton>
                        </Tooltip>
                    </Stack>

                    {hasData && (
                        <>
                            {/* Header */}
                            <Stack direction="row" justifyContent="space-between" alignItems="flex-start"
                                   sx={{mb: 1.5, flexWrap: "wrap", gap: 1}}>
                                <Box>
                                    <Typography sx={{fontSize: 16, fontWeight: 500}}>{autoName}</Typography>
                                    <Mono sx={{color: "text.disabled"}}>{automationId}</Mono>
                                </Box>
                                <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap>
                                    <ConnPill status={connStatus}/>
                                    <Chip label={currentTopState || "IDLE"} size="small"
                                          color={currentTopState === "ACTIVE" ? "success" : "default"}
                                          sx={{fontSize: 11}}/>
                                    {currentOutcome && (
                                        <Chip label={`${OUTCOME_ICON[currentOutcome] || ""} ${currentOutcome}`}
                                              size="small" color={OUTCOME_COLOR[currentOutcome] || "default"}
                                              sx={{fontSize: 10}}/>
                                    )}
                                    {httpState?.hasBranches && (
                                        <Chip icon={<CallSplitIcon sx={{fontSize: 13}}/>} label="branches" size="small"
                                              color="info" variant="outlined" sx={{fontSize: 10}}/>
                                    )}
                                    <Tooltip title="Manual refresh from server">
                                        <IconButton size="small" onClick={() => fetchHttp(resolvedIdRef.current)}
                                                    disabled={httpLoading}>
                                            {httpLoading ? <CircularProgress size={13}/> :
                                                <RefreshIcon sx={{fontSize: 15}}/>}
                                        </IconButton>
                                    </Tooltip>
                                </Stack>
                            </Stack>

                            {/* Metrics */}
                            <Grid container spacing={1} sx={{mb: 2}}>
                                {[
                                    {
                                        label: "schema",
                                        value: `v${httpState?.schemaVersion || planData?.schemaVersion || "—"}`
                                    },
                                    {label: "tree nodes", value: currentNodes.length},
                                    {label: "branches", value: currentBranches.length},
                                    {label: "actions fired", value: actionLog.length},
                                ].map(({label, value}) => (
                                    <Grid item xs={3} key={label}>
                                        <Paper elevation={0} sx={{
                                            p: "10px 14px",
                                            background: T.surface,
                                            border: `0.5px solid ${T.border}`,
                                            borderRadius: 2
                                        }}>
                                            <Typography sx={{
                                                fontSize: 11,
                                                color: "text.secondary",
                                                mb: 0.5
                                            }}>{label}</Typography>
                                            <Typography sx={{fontSize: 20, fontWeight: 500}}>{value}</Typography>
                                        </Paper>
                                    </Grid>
                                ))}
                            </Grid>

                            {/* Live eval banner */}
                            {liveEvent && (
                                <Alert
                                    severity={liveEvent.outcome === "TRIGGERED" ? "success" : liveEvent.outcome === "C1_NEGATIVE" || liveEvent.outcome === "RESTORED" ? "warning" : "info"}
                                    icon={<BoltIcon/>}
                                    sx={{
                                        mb: 1.5,
                                        py: "4px",
                                        fontSize: 12,

                                        "& .MuiAlert-message": {width: "100%"}
                                    }}
                                >
                                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                                        <Typography sx={{fontSize: 12,}}>
                                            {OUTCOME_ICON[liveEvent.outcome]} <strong>{liveEvent.outcome}</strong>
                                            {liveEvent.reason ? ` · ${liveEvent.reason}` : ""}
                                            {liveEvent.c1True ? " · c1 ✓" : " · c1 ✗"}
                                        </Typography>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            {liveEvent.evalDurationMs != null && <Typography sx={{
                                                fontSize: 11,
                                                color: "text.secondary",

                                            }}>{liveEvent.evalDurationMs}ms</Typography>}
                                            <Typography sx={{
                                                fontSize: 11,
                                                color: "text.secondary",

                                            }}>{fmtDate(liveEvent.evaluatedAt)}</Typography>
                                            <Mono sx={{
                                                color: "text.disabled",
                                                fontSize: 10
                                            }}>{liveEvent.traceId?.slice(-8)}</Mono>
                                        </Stack>
                                    </Stack>
                                    {liveEvent.triggerPayload && Object.keys(liveEvent.triggerPayload).length > 0 && (
                                        <Stack direction="row" spacing={0.5} sx={{mt: 0.5}} flexWrap="wrap" useFlexGap>
                                            {Object.entries(liveEvent.triggerPayload).slice(0, 6).map(([k, v]) => (
                                                <Chip key={k} label={`${k}: ${v}`} size="small" variant="outlined"
                                                      sx={{fontSize: 10, height: 18,}}/>
                                            ))}
                                        </Stack>
                                    )}
                                </Alert>
                            )}

                            {/* Tabs */}
                            <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto"
                                  sx={{
                                      mb: 1.5, borderBottom: "0.5px solid", borderColor: "divider", minHeight: 36,
                                      "& .MuiTab-root": {
                                          minHeight: 36,
                                          fontSize: 12,
                                          textTransform: "none",
                                          fontWeight: 400,
                                          py: 0
                                      },
                                      "& .MuiTab-root.Mui-selected": {fontWeight: 500},
                                      "& .MuiTabs-indicator": {height: 2}
                                  }}>
                                {tabs.map((t, i) => <Tab key={i} label={t.label} icon={t.icon} iconPosition="start"/>)}
                            </Tabs>

                            {tab === 0 && (currentNodes.length === 0
                                    ? <Typography color="text.disabled" fontSize={13} sx={{py: 4, textAlign: "center"}}>No
                                        condition tree nodes.</Typography>
                                    : currentNodes.map(n => <NodeCard key={n.nodeId} node={n} flash={flash}
                                                                      prevNode={prevEventRef.current?.conditionNodes?.find(p => p.nodeId === n.nodeId)}/>)
                            )}

                            {tab === 1 && (currentBranches.length === 0
                                    ? <Typography color="text.disabled" fontSize={13} sx={{py: 4, textAlign: "center"}}>No
                                        branches compiled.</Typography>
                                    : currentBranches.map(b => <BranchCard key={b.gateNodeId} branch={b} flash={flash}
                                                                           prevBranch={prevEventRef.current?.branchStates?.find(p => p.gateNodeId === b.gateNodeId)}/>)
                            )}

                            {tab === 2 && (
                                <Box>
                                    <Stack direction="row" justifyContent="space-between" alignItems="center"
                                           sx={{mb: 1}}>
                                        <FormControlLabel
                                            control={<Switch size="small" checked={showSkipped}
                                                             onChange={e => setShowSkipped(e.target.checked)}/>}
                                            label={<Typography sx={{fontSize: 12}}>Show SKIPPED / NOT_MET</Typography>}
                                        />
                                        <Button size="small" onClick={() => setLog([])}
                                                sx={{fontSize: 11, color: "text.secondary"}}>Clear</Button>
                                    </Stack>
                                    {filteredLog.length === 0
                                        ? <Typography color="text.disabled" fontSize={13}
                                                      sx={{py: 4, textAlign: "center"}}>
                                            {connStatus === "connected" ? "Waiting for events…" : "Connect to see live events"}
                                        </Typography>
                                        : filteredLog.map((e, i) => <LogRow key={i} event={e}/>)
                                    }
                                </Box>
                            )}

                            {tab === 3 && <ActionsTab actionLog={actionLog} onClear={() => setActionLog([])}
                                                      connStatus={connStatus}/>}

                            {tab === 4 && (!httpState?.coalition
                                    ? <Typography color="text.disabled" fontSize={13} sx={{py: 4, textAlign: "center"}}>No
                                        coalition — single-trigger mode.</Typography>
                                    : (
                                        <Card elevation={0} sx={{
                                            border: "0.5px solid",
                                            borderColor: "divider",
                                            borderRadius: 2,
                                            background: T.surface
                                        }}>
                                            <CardContent sx={{p: "12px 14px !important"}}>
                                                <Stack direction="row" justifyContent="space-between" sx={{mb: 1.5}}>
                                                    <Box>
                                                        <Typography sx={{
                                                            fontSize: 14,
                                                            fontWeight: 500,
                                                            mb: 0.25
                                                        }}>coalition</Typography>
                                                        <Typography sx={{
                                                            fontSize: 12,
                                                            color: "text.secondary"
                                                        }}>window: {httpState.coalition.windowSeconds}s</Typography>
                                                    </Box>
                                                    <Chip label={httpState.coalition.mode} size="small" color="info"
                                                          sx={{fontSize: 11}}/>
                                                </Stack>
                                                <Divider sx={{mb: 1.5}}/>
                                                <SLabel>members</SLabel>
                                                <Stack spacing={0.75}>
                                                    {(httpState.coalition.members || []).map((m, i) => {
                                                        const liveLastFired = liveEvent?.coalitionLastFired?.[m.deviceId];
                                                        const lastFired = liveLastFired || m.lastFiredEpochMs;
                                                        const agoS = lastFired > 0 ? (Date.now() - lastFired) / 1000 : -1;
                                                        const inWindow = lastFired > 0 && agoS < httpState.coalition.windowSeconds;
                                                        return (
                                                            <Box key={i} sx={{
                                                                p: "8px 10px",
                                                                borderRadius: 1.5,
                                                                background: "rgba(255,255,255,0.03)",
                                                                border: `0.5px solid ${T.border}`
                                                            }}>
                                                                <Stack direction="row" justifyContent="space-between"
                                                                       alignItems="center">
                                                                    <Box>
                                                                        <Mono
                                                                            sx={{color: "text.primary"}}>{m.deviceId}</Mono>
                                                                        <Typography sx={{
                                                                            fontSize: 11,
                                                                            color: "text.secondary"
                                                                        }}>
                                                                            {m.role}{httpState.coalition.mode === "SEQUENCE" ? ` · seq#${m.sequenceIndex}` : ""}
                                                                        </Typography>
                                                                    </Box>
                                                                    <Chip
                                                                        label={inWindow ? "within window" : fmtAgo(lastFired)}
                                                                        size="small"
                                                                        color={inWindow ? "success" : "default"}
                                                                        sx={{fontSize: 10, height: 20}}/>
                                                                </Stack>
                                                            </Box>
                                                        );
                                                    })}
                                                </Stack>
                                            </CardContent>
                                        </Card>
                                    )
                            )}

                            {tab === 5 &&
                                <OverrideTab automationId={automationId} hasCoalition={httpState?.hasCoalition}
                                             onSuccess={showToast}/>}
                        </>
                    )}

                    {!hasData && !httpLoading && !httpError && (
                        <Box sx={{py: 6, textAlign: "center"}}>
                            <AccountTreeIcon sx={{fontSize: 40, color: "text.disabled", mb: 1}}/>
                            <Typography color="text.disabled" fontSize={13}>Select an automation from the list, or enter
                                an ID above.</Typography>
                        </Box>
                    )}
                </Box>
            </Box>

            <Snackbar open={toast.open} autoHideDuration={2500} onClose={() => setToast(p => ({...p, open: false}))}
                      anchorOrigin={{vertical: "bottom", horizontal: "right"}}>
                <Alert severity={toast.severity} variant="filled" sx={{fontSize: 12}}>{toast.message}</Alert>
            </Snackbar>
        </Box>
    );
}

export default AutomationLiveInspector;