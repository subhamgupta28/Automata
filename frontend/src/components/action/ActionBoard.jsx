import {
    addEdge,
    applyEdgeChanges,
    applyNodeChanges,
    Controls,
    Panel,
    ReactFlow,
    useEdgesState,
    useNodesState,
    useReactFlow
} from "@xyflow/react";
import React, {createContext, memo, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {
    disableAutomation,
    getActions,
    getAutomationDetail,
    getSnoozeStatus,
    resumeAutomation,
    saveAutomationDetail,
    snoozeAutomation,
    timedDisableAuto,
} from "../../services/apis.jsx";

import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    Grid,
    IconButton,
    InputAdornment,
    LinearProgress,
    Slider,
    Stack,
    Switch,
    Tab,
    Tabs,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip,
    Typography,
} from "@mui/material";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import CustomEdge from "./CustomEdge.jsx";
import '@xyflow/react/dist/style.css';
import {TriggerNode} from "./TriggerNode.jsx";
import {ActionNode} from "./ActionNode.jsx";
import {ConditionNode} from "./ConditionNode.jsx";
import {ValueReaderNode} from "./ValueReaderNode.jsx";
import {And, Or} from "./Conditions.jsx";

// ─── Node palette styles ──────────────────────────────────────────────────────
const triggerStyle = {padding: '10px', borderRadius: '5px', width: '100%', border: '2px solid #6DBF6D', cursor: 'grab'};
const actionStyle = {padding: '10px', borderRadius: '5px', width: '100%', border: '2px solid #0288D1', cursor: 'grab'};
const conditionStyle = {
    padding: '10px',
    borderRadius: '5px',
    width: '100%',
    border: '2px solid #FFEB3B',
    cursor: 'grab'
};

let id = 0;
const getId = (type) => `node_${type}_${id++}`;

// ─── Theme tokens ─────────────────────────────────────────────────────────────
const T = {
    yellow: '#ffd821',
    blue: '#74b9ff',
    green: '#00e5a0',
    red: '#ff4757',
    orange: '#ff6b35',
    surface: '#111318',
    border: 'rgba(255,213,33,0.12)',
    textDim: '#718096',
    textMid: '#b0b0b0',
    mono: '"JetBrains Mono", monospace',
};

const DIALOG_PAPER_SX = {
    fontFamily: T.mono,
};

// ─── Shared MUI sx helpers ────────────────────────────────────────────────────
const monoTypo = {fontFamily: T.mono};

// TextField / OutlinedInput dark override
const darkField = {
    fontFamily: T.mono,
    fontSize: '12px',
    '& .MuiOutlinedInput-root': {
        fontFamily: T.mono,
        fontSize: '12px',
        '&.Mui-focused fieldset': {borderColor: T.yellow},
    },
    '& .MuiInputLabel-root': {fontFamily: T.mono, fontSize: '11px', color: T.textDim},
    '& .MuiInputLabel-root.Mui-focused': {color: T.yellow},
    '& input[type=number]::-webkit-inner-spin-button': {WebkitAppearance: 'none'},
};

// ToggleButtonGroup for mode tabs
const tabGroupSx = {
    width: '100%',
    '& .MuiToggleButton-root': {
        flex: 1,
        fontFamily: T.mono,
        fontSize: '11px',
        fontWeight: 700,
        textTransform: 'uppercase',
        letterSpacing: '0.5px',
        border: '1px solid rgba(255,255,255,0.07)',
        padding: '7px',
        transition: 'all 0.15s',
    },
    '& .MuiToggleButton-root.Mui-selected': {
        color: T.yellow,
        borderColor: `${T.yellow}55`,
        background: `${T.yellow}10`
    },
    '& .MuiToggleButton-root:hover': {background: 'rgba(255,255,255,0.04)'},
};

// Preset chip button
function PresetChip({label, onClick, disabled, hoverColor}) {
    return (
        <Button
            variant="outlined"
            size="small"
            disabled={disabled}
            onClick={onClick}
            sx={{
                fontFamily: T.mono,
                fontSize: '11px',
                fontWeight: 600,
                color: T.textMid,
                borderColor: 'rgba(255,255,255,0.1)',
                padding: '5px 4px',
                minWidth: 0,
                '&:hover': {borderColor: hoverColor, color: hoverColor, background: `${hoverColor}0d`},
                '&.Mui-disabled': {opacity: 0.4},
            }}
        >
            {label}
        </Button>
    );
}

// Section wrapper using MUI Box
function Section({label, children, sx}) {
    return (
        <Box sx={{
            background: 'rgba(255,255,255,0.025)',
            borderRadius: '8px',
            p: '10px 12px',
            border: '1px solid rgba(255,255,255,0.06)',
            ...sx,
        }}>
            {label && (
                <Typography sx={{
                    fontSize: '9px', fontWeight: 700,
                    textTransform: 'uppercase', letterSpacing: '1.2px',
                    mb: '8px', fontFamily: T.mono,
                }}>
                    {label}
                </Typography>
            )}
            {children}
        </Box>
    );
}

// Status Alert using MUI Alert
function StatusAlert({isSnoozed, isDisabled, snoozeRemaining, disableRemaining, loading, onResume}) {
    const fmtRem = (s) => {
        if (!s) return '';
        const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
        return h > 0 ? ` — ${h}h ${m}m remaining` : ` — ${m}m remaining`;
    };

    const severity = isSnoozed ? 'warning' : isDisabled ? 'error' : 'success';
    const msg = isSnoozed
        ? `⏸ Snoozed${fmtRem(snoozeRemaining)}`
        : isDisabled
            ? `🚫 Disabled${fmtRem(disableRemaining)}`
            : '▶ Active';

    return (
        <Alert
            severity={severity}
            sx={{
                fontFamily: T.mono,
                fontSize: '12px',
                fontWeight: 700,
                py: '6px',
                background: isSnoozed ? `${T.yellow}0d` : isDisabled ? `${T.red}0d` : `${T.green}0a`,
                border: `1px solid ${isSnoozed ? `${T.yellow}30` : isDisabled ? `${T.red}30` : `${T.green}25`}`,
                color: isSnoozed ? T.yellow : isDisabled ? T.red : T.green,
                '& .MuiAlert-icon': {color: 'inherit'},
            }}
            action={(isSnoozed || isDisabled) && (
                <Button size="small" variant="outlined" disabled={loading} onClick={onResume}
                        sx={{
                            color: T.green, borderColor: `${T.green}50`, fontSize: '10px',
                            fontFamily: T.mono, py: '2px',
                            '&:hover': {borderColor: T.green, background: `${T.green}0d`},
                        }}>
                    ▶ Resume
                </Button>
            )}
        >
            {msg}
        </Alert>
    );
}

// ─── Snooze Dialog ────────────────────────────────────────────────────────────
const SNOOZE_PRESETS = [
    {label: '15 min', minutes: 15}, {label: '30 min', minutes: 30},
    {label: '1 hr', minutes: 60}, {label: '2 hr', minutes: 120},
    {label: '4 hr', minutes: 240}, {label: 'Tonight', minutes: 480},
];

function SnoozeDialog({open, automation, onClose, onStatusChange}) {
    const [status, setStatus] = useState(null);
    const [customMin, setCustomMin] = useState('');
    const [loading, setLoading] = useState(false);
    const [mode, setMode] = useState('snooze'); // 'snooze' | 'disable'

    const refresh = useCallback(async () => {
        if (!automation?.id) return;
        try {
            setStatus(await getSnoozeStatus(automation.id));
        } catch (e) {
            console.error(e);
        }
    }, [automation?.id]);

    useEffect(() => {
        if (!open) return;
        refresh();
        const t = setInterval(refresh, 10000);
        return () => clearInterval(t);
    }, [open, refresh]);

    const act = async (fn) => {
        setLoading(true);
        try {
            await fn();
            await refresh();
            onStatusChange?.();
        } finally {
            setLoading(false);
        }
    };

    const firePreset = (min) => act(() => mode === 'snooze' ? snoozeAutomation(automation.id, min) : timedDisableAuto(automation.id, min));
    const fireCustom = () => {
        const m = parseInt(customMin);
        if (m > 0) {
            firePreset(m);
            setCustomMin('');
        }
    };
    const fireResume = () => act(() => resumeAutomation(automation.id));

    const accentColor = mode === 'snooze' ? T.yellow : T.red;

    return (
        <Dialog open={open} onClose={onClose} PaperProps={{sx: {...DIALOG_PAPER_SX, minWidth: 420, maxWidth: 480}}}>
            <DialogTitle sx={{pb: 1, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                <Box>
                    <Typography sx={{color: T.yellow, fontWeight: 700, fontSize: '14px', ...monoTypo}}>
                        ⏸ Snooze / Disable
                    </Typography>
                    <Typography sx={{color: T.textDim, fontSize: '11px', mt: '2px', ...monoTypo}}>
                        {automation?.name}
                    </Typography>
                </Box>
                <IconButton size="small" onClick={onClose} sx={{color: T.textDim, mt: '-4px', mr: '-8px'}}>
                    ✕
                </IconButton>
            </DialogTitle>

            {loading && <LinearProgress sx={{height: '2px', '& .MuiLinearProgress-bar': {background: T.yellow}}}/>}
            <Divider sx={{borderColor: T.border}}/>

            <DialogContent sx={{pt: 2}}>
                <Stack spacing={2}>

                    {/* Status */}
                    <StatusAlert
                        isSnoozed={status?.snoozed}
                        isDisabled={status?.timedDisabled}
                        snoozeRemaining={status?.snoozeRemainingSeconds}
                        disableRemaining={status?.disableRemainingSeconds}
                        loading={loading}
                        onResume={fireResume}
                    />

                    {/* Mode toggle */}
                    <ToggleButtonGroup
                        exclusive value={mode}
                        onChange={(_, v) => v && setMode(v)}
                        sx={tabGroupSx}
                    >
                        <ToggleButton value="snooze">⏸ Snooze</ToggleButton>
                        <ToggleButton value="disable" sx={{
                            '&.Mui-selected': {
                                color: T.red,
                                borderColor: `${T.red}55`,
                                background: `${T.red}10`
                            }
                        }}>
                            🚫 Disable
                        </ToggleButton>
                    </ToggleButtonGroup>

                    {/* Presets */}
                    <Section label={mode === 'snooze' ? 'Pause automation for...' : 'Disable automation for...'}>
                        <Grid container spacing={1}>
                            {SNOOZE_PRESETS.map(p => (
                                <Grid item xs={4} key={p.minutes}>
                                    <PresetChip
                                        label={p.label}
                                        disabled={loading}
                                        onClick={() => firePreset(p.minutes)}
                                        hoverColor={accentColor}
                                    />
                                </Grid>
                            ))}
                        </Grid>
                    </Section>

                    <Divider sx={{borderColor: 'rgba(255,255,255,0.06)'}}/>

                    {/* Custom duration */}
                    <Stack direction="row" spacing={1} alignItems="center">
                        <TextField
                            type="number"
                            size="small"
                            placeholder="Custom minutes"
                            value={customMin}
                            onChange={e => setCustomMin(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && fireCustom()}
                            inputProps={{min: 1, max: 1440}}
                            InputProps={{
                                endAdornment: <InputAdornment position="end">
                                    <Typography sx={{color: T.textDim, fontSize: '10px', ...monoTypo}}>min</Typography>
                                </InputAdornment>
                            }}
                            sx={{flex: 1, ...darkField}}
                        />
                        <Button
                            variant="outlined"
                            disabled={loading || !customMin}
                            onClick={fireCustom}
                            sx={{
                                fontFamily: T.mono,
                                fontWeight: 700,
                                fontSize: '12px',
                                color: accentColor,
                                borderColor: `${accentColor}60`,
                                background: `${accentColor}0d`,
                                whiteSpace: 'nowrap',
                                '&:hover': {borderColor: accentColor, background: `${accentColor}18`},
                                '&.Mui-disabled': {opacity: 0.4},
                            }}
                        >
                            Set
                        </Button>
                    </Stack>
                </Stack>
            </DialogContent>
        </Dialog>
    );
}

// ─── Evaluation logic (pure JS — no DOM) ─────────────────────────────────────
function parseTimeStr(s) {
    if (!s) return null;
    s = s.trim();
    const ampm = /^(\d+):(\d+):(\d+)\s*(AM|PM)$/i.exec(s);
    if (ampm) {
        let h = parseInt(ampm[1]), m = parseInt(ampm[2]);
        if (ampm[4].toUpperCase() === 'PM' && h !== 12) h += 12;
        if (ampm[4].toUpperCase() === 'AM' && h === 12) h = 0;
        return h * 60 + m;
    }
    const plain = /^(\d+):(\d+)(?::(\d+))?$/.exec(s);
    if (plain) return parseInt(plain[1]) * 60 + parseInt(plain[2]);
    return null;
}

function isInTimeRange(cur, fromStr, toStr) {
    const from = parseTimeStr(fromStr), to = parseTimeStr(toStr);
    if (from === null || to === null) return false;
    return from <= to ? cur >= from && cur <= to : cur >= from || cur <= to;
}

function evalCondition(cond, payload, wasActive, simMin, simDay) {
    if (cond.condition === 'scheduled') {
        if (cond.days?.length > 0 && !cond.days.includes('Everyday') && !cond.days.includes(simDay))
            return {result: false, detail: `Day ${simDay} not in schedule`};
        if (cond.scheduleType === 'range') {
            const inRange = isInTimeRange(simMin, cond.fromTime, cond.toTime);
            const hhmm = `${String(Math.floor(simMin / 60)).padStart(2, '0')}:${String(simMin % 60).padStart(2, '0')}`;
            return {
                result: inRange,
                detail: `${hhmm} ${inRange ? '✓ in' : '✗ outside'} ${cond.fromTime} – ${cond.toTime}`
            };
        }
        if (cond.scheduleType === 'interval') return {
            result: true,
            detail: `interval every ${cond.intervalMinutes}min (sim)`
        };
        if (cond.scheduleType === 'solar') return {
            result: true,
            detail: `solar ${cond.solarType} ±${cond.offsetMinutes}min (sim)`
        };
        return {result: true, detail: `schedule: ${cond.scheduleType}`};
    }
    const key = cond.triggerKey;
    if (!key || !(key in payload)) return {result: false, detail: `Key '${key}' missing`};
    const raw = payload[key], v = parseFloat(raw), buf = 5.0;
    if (isNaN(v)) {
        const pass = String(raw) === cond.value;
        return {result: pass, detail: `"${raw}" == "${cond.value}" → ${pass ? 'PASS' : 'FAIL'}`};
    }
    if (cond.condition === 'range') {
        const a = parseFloat(cond.above), b = parseFloat(cond.below);
        const pass = wasActive ? v > (a - buf) && v < (b + buf) : v > a && v < b;
        return {result: pass, detail: `${v} in (${a}, ${b})${wasActive ? ' +buf' : ''} → ${pass ? 'PASS' : 'FAIL'}`};
    }
    if (cond.condition === 'above') {
        const t = parseFloat(cond.value), pass = wasActive ? v > (t - buf) : v > t;
        return {result: pass, detail: `${v} > ${wasActive ? (t - buf) + '(buf)' : t} → ${pass ? 'PASS' : 'FAIL'}`};
    }
    if (cond.condition === 'below') {
        const t = parseFloat(cond.value), pass = wasActive ? v < (t + buf) : v < t;
        return {result: pass, detail: `${v} < ${wasActive ? (t + buf) + '(buf)' : t} → ${pass ? 'PASS' : 'FAIL'}`};
    }
    const pass = String(raw) === cond.value;
    return {result: pass, detail: `${raw} == ${cond.value} → ${pass ? 'PASS' : 'FAIL'}`};
}

function runEvaluate(automation, payload, wasActive, simMin, simDay) {
    const ctx = {};
    const operatorIds = new Set((automation.operators || []).map(o => o.nodeId));
    const triggerConds = [], gateConds = [];

    (automation.conditions || []).forEach(c => {
        if (!c.enabled) return;
        const isGate = c.previousNodeRef?.some(r => operatorIds.has(r.nodeId));
        isGate ? gateConds.push(c) : triggerConds.push(c);
    });

    triggerConds.forEach(c => {
        const {result, detail} = evalCondition(c, payload, wasActive, simMin, simDay);
        ctx[c.nodeId] = {id: c.nodeId, result, detail, contributors: new Set([c.nodeId]), type: 'condition', cond: c};
    });

    (automation.operators || []).forEach(op => {
        const inputs = (op.previousNodeRef || []).map(r => ctx[r.nodeId]).filter(Boolean);
        let result, contributors = new Set();
        if (op.logicType === 'OR') {
            result = inputs.some(n => n.result);
            inputs.filter(n => n.result).forEach(n => n.contributors.forEach(c => contributors.add(c)));
        } else {
            result = inputs.every(n => n.result);
            inputs.forEach(n => n.contributors.forEach(c => contributors.add(c)));
        }
        const sum = inputs.map(n => `${n.id.split('_').pop()}=${n.result}`).join(', ');
        ctx[op.nodeId] = {
            id: op.nodeId,
            result,
            contributors,
            type: 'operator',
            op,
            detail: `${op.logicType}([${sum}]) → ${result ? 'TRUE' : 'FALSE'}`
        };
    });

    gateConds.forEach(c => {
        const parentTrue = (c.previousNodeRef || []).some(r => ctx[r.nodeId]?.result);
        const {result: cr, detail} = evalCondition(c, payload, wasActive, simMin, simDay);
        const result = parentTrue && cr;
        ctx[c.nodeId] = {
            id: c.nodeId, result,
            detail: parentTrue ? detail : `⛔ gate blocked — ${detail}`,
            contributors: new Set([c.nodeId]), type: 'gate', cond: c, durationMinutes: c.durationMinutes || 0,
        };
    });

    const ops = automation.operators || [];
    let root = null;
    if (ops.length > 0) {
        const refIds = new Set(ops.flatMap(o => (o.previousNodeRef || []).map(r => r.nodeId)).filter(i => operatorIds.has(i)));
        root = ctx[(ops.find(o => !refIds.has(o.nodeId)) || ops[ops.length - 1]).nodeId];
    } else if (triggerConds.length > 0) {
        root = ctx[triggerConds[0].nodeId];
    }

    const trueNodes = new Set(Object.values(ctx).filter(n => n.result).map(n => n.id));
    const falseNodes = new Set(Object.values(ctx).filter(n => !n.result).map(n => n.id));

    const resolveGroup = g => (automation.actions || [])
        .filter(a => a.isEnabled && (a.conditionGroup || 'positive').toLowerCase() === g)
        .filter(a => {
            if (!a.previousNodeRef?.length) return true;
            return a.previousNodeRef.some(ref =>
                ref.handle?.includes('cond-negative') ? falseNodes.has(ref.nodeId) : trueNodes.has(ref.nodeId)
            );
        })
        .sort((a, b) => (a.order || 9999) - (b.order || 9999));

    const activeDurationConds = (automation.conditions || [])
        .filter(c => c.enabled && c.durationMinutes > 0 && ctx[c.nodeId]?.result);

    return {
        ctx,
        root,
        positiveActions: resolveGroup('positive'),
        negativeActions: resolveGroup('negative'),
        activeDurationConds
    };
}

// ─── Test Harness Dialog ──────────────────────────────────────────────────────
const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const VERDICT_META = {
    TRIGGERED: {icon: '🚀', color: T.yellow, label: 'TRIGGERED', stateMsg: 'IDLE → ACTIVE'},
    RESTORED: {icon: '⏹', color: T.red, label: 'RESTORED', stateMsg: 'ACTIVE/HOLDING → IDLE'},
    SKIPPED: {icon: '⏭', color: '#718096', label: 'SKIPPED', stateMsg: 'state unchanged'},
    NOT_MET: {icon: '💤', color: '#444', label: 'NOT MET', stateMsg: 'state unchanged'},
};

const NODE_TYPE_COLOR = {condition: T.blue, operator: T.orange, gate: T.yellow};

// Node trace row
function NodeRow({node}) {
    const tc = NODE_TYPE_COLOR[node.type] || T.blue;
    const typeLabel = node.type === 'gate'
        ? `GATE${node.durationMinutes > 0 ? ` (${node.durationMinutes}m)` : ''}`
        : node.type.toUpperCase();

    return (
        <Box sx={{
            p: '8px 10px', borderRadius: '6px',
            background: 'rgba(255,255,255,0.02)',
            border: '1px solid rgba(255,255,255,0.05)',
            borderLeft: `3px solid ${tc}`,
        }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: '3px'}}>
                <Stack direction="row" alignItems="center" spacing={0.75}>
                    <Typography sx={{
                        color: tc,
                        fontSize: '9px',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        fontFamily: T.mono
                    }}>
                        {typeLabel}
                    </Typography>
                    <Typography sx={{color: T.textMid, fontSize: '10px', fontFamily: T.mono}}>
                        {node.id}
                    </Typography>
                </Stack>
                <Chip
                    label={node.result ? 'TRUE' : 'FALSE'}
                    size="small"
                    sx={{
                        fontFamily: T.mono, fontSize: '10px', fontWeight: 700, height: '20px',
                        background: node.result ? 'rgba(0,229,160,0.1)' : 'rgba(255,71,87,0.1)',
                        color: node.result ? T.green : T.red,
                        border: `1px solid ${node.result ? T.green + '30' : T.red + '30'}`,
                    }}
                />
            </Stack>
            <Typography sx={{color: T.textDim, fontSize: '10px', lineHeight: 1.5, fontFamily: T.mono}}>
                {node.detail}
            </Typography>
        </Box>
    );
}

// Action row
function ActionRow({action, index, isActive, groupColor}) {
    return (
        <Box sx={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            p: '6px 10px', mb: '4px', borderRadius: '6px',
            background: isActive ? `${groupColor}08` : 'rgba(255,255,255,0.02)',
            border: `1px solid ${isActive ? groupColor + '28' : 'rgba(255,255,255,0.05)'}`,
        }}>
            <Stack direction="row" alignItems="center" spacing={1}>
                <Typography sx={{color: '#333', fontFamily: T.mono, fontSize: '10px'}}>
                    {String(index + 1).padStart(2, '0')}
                </Typography>
                <Box>
                    <Typography sx={{color: '#e2e8f0', fontSize: '11px', fontFamily: T.mono}}>
                        {action.name}
                    </Typography>
                    <Typography sx={{color: T.textDim, fontSize: '10px', fontFamily: T.mono}}>
                        {action.key} = <Box component="span" sx={{color: groupColor}}>{action.data}</Box>
                    </Typography>
                </Box>
            </Stack>
            <Box sx={{textAlign: 'right'}}>
                <Typography sx={{color: '#444', fontSize: '9px', fontFamily: T.mono}}>#{action.order}</Typography>
                {action.delaySeconds > 0 && (
                    <Typography sx={{color: T.yellow, fontSize: '9px', fontFamily: T.mono}}>
                        +{action.delaySeconds}s
                    </Typography>
                )}
            </Box>
        </Box>
    );
}

function EmptyNote({text}) {
    return (
        <Typography sx={{color: '#333', fontSize: '11px', textAlign: 'center', py: 4, fontFamily: T.mono}}>
            {text}
        </Typography>
    );
}

function TestHarnessDialog({open, automation, onClose}) {
    const now = new Date();
    const [rangeVal, setRangeVal] = useState(100);
    const [customKey, setCustomKey] = useState('');
    const [customVal, setCustomVal] = useState('');
    const [timeVal, setTimeVal] = useState(`${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`);
    const [dayVal, setDayVal] = useState(DAYS[now.getDay() === 0 ? 6 : now.getDay() - 1]);
    const [stateVal, setStateVal] = useState('IDLE');
    const [result, setResult] = useState(null);
    const [runLog, setRunLog] = useState([]);
    const [extraPayload, setExtraPayload] = useState({});
    const [tab, setTab] = useState(0); // MUI Tabs uses numeric index

    const primaryKey = automation?.trigger?.keys?.[0] || automation?.trigger?.key || 'range';

    useEffect(() => {
        if (open) {
            setResult(null);
            setTab(0);
        }
    }, [open]);

    const run = () => {
        const [hh, mm] = timeVal.split(':').map(Number);
        const simMin = hh * 60 + mm;
        const wasActive = stateVal === 'ACTIVE' || stateVal === 'HOLDING';
        const payload = {[primaryKey]: rangeVal, ...extraPayload};

        const ev = runEvaluate(automation, payload, wasActive, simMin, dayVal);
        const condNow = ev.root?.result ?? false;

        let verdict;
        if (stateVal === 'IDLE' && condNow) verdict = 'TRIGGERED';
        else if ((stateVal === 'ACTIVE' || stateVal === 'HOLDING') && !condNow) verdict = 'RESTORED';
        else if ((stateVal === 'ACTIVE' || stateVal === 'HOLDING') && condNow) verdict = 'SKIPPED';
        else verdict = 'NOT_MET';

        setRunLog(prev => [{
            time: new Date().toLocaleTimeString(), verdict,
            payload: {...payload}, stateVal, simTime: timeVal, day: dayVal,
        }, ...prev].slice(0, 30));

        setResult({...ev, verdict, payload});
        setTab(1); // jump to Trace
    };

    const addExtra = () => {
        if (customKey && customVal) {
            setExtraPayload(p => ({...p, [customKey]: isNaN(customVal) ? customVal : parseFloat(customVal)}));
            setCustomKey('');
            setCustomVal('');
        }
    };

    const vm = result ? VERDICT_META[result.verdict] : null;

    // ── Inputs tab ────────────────────────────────────────────────────────────
    const inputsPane = (
        <Stack spacing={2} sx={{pt: 1}}>
            {/* Payload */}
            <Section label="Payload">
                <Typography sx={{
                    color: T.textDim,
                    fontSize: '10px',
                    textTransform: 'uppercase',
                    mb: '4px',
                    fontFamily: T.mono
                }}>
                    {primaryKey}
                </Typography>

                <Stack direction="row" alignItems="center" spacing={2} sx={{mb: 1.5}}>
                    <Slider
                        value={rangeVal}
                        min={0} max={1000}
                        onChange={(_, v) => setRangeVal(v)}
                        sx={{
                            color: T.yellow,
                            '& .MuiSlider-thumb': {width: 14, height: 14},
                            '& .MuiSlider-track': {height: 3},
                            '& .MuiSlider-rail': {height: 3, opacity: 0.2},
                        }}
                    />
                    <Typography sx={{
                        color: T.yellow,
                        fontWeight: 700,
                        minWidth: '44px',
                        textAlign: 'right',
                        fontFamily: T.mono,
                        fontSize: '13px'
                    }}>
                        {rangeVal}
                    </Typography>
                </Stack>

                {/* Extra payload entries */}
                {Object.entries(extraPayload).map(([k, v]) => (
                    <Stack key={k} direction="row" justifyContent="space-between" alignItems="center"
                           sx={{p: '4px 8px', background: 'rgba(255,255,255,0.04)', borderRadius: '5px', mb: '4px'}}>
                        <Typography sx={{fontFamily: T.mono, color: T.textMid, fontSize: '11px'}}>
                            {k}: <Box component="span" sx={{color: T.blue}}>{String(v)}</Box>
                        </Typography>
                        <IconButton size="small" onClick={() => setExtraPayload(p => {
                            const n = {...p};
                            delete n[k];
                            return n;
                        })}
                                    sx={{color: T.textDim, p: '2px', fontSize: '12px'}}>✕</IconButton>
                    </Stack>
                ))}

                {/* Add extra key/value */}
                <Stack direction="row" spacing={1} sx={{mt: '6px'}}>
                    <TextField size="small" placeholder="key" value={customKey}
                               onChange={e => setCustomKey(e.target.value)}
                               sx={{flex: 1, ...darkField}}/>
                    <TextField size="small" placeholder="value" value={customVal}
                               onChange={e => setCustomVal(e.target.value)}
                               onKeyDown={e => e.key === 'Enter' && addExtra()}
                               sx={{flex: 1, ...darkField}}/>
                    <Button variant="outlined" onClick={addExtra}
                            sx={{
                                fontFamily: T.mono,
                                fontWeight: 700,
                                color: T.blue,
                                borderColor: `${T.blue}50`,
                                minWidth: '36px',
                                px: '10px',
                                '&:hover': {borderColor: T.blue, background: `${T.blue}0d`}
                            }}>
                        +
                    </Button>
                </Stack>
            </Section>

            {/* Time + Day */}
            <Grid container spacing={1}>
                <Grid item xs={6}>
                    <Section label="Sim Time">
                        <TextField type="time" size="small" value={timeVal}
                                   onChange={e => setTimeVal(e.target.value)}
                                   inputProps={{step: 60}}
                                   sx={{width: '100%', ...darkField}}/>
                    </Section>
                </Grid>
                <Grid item xs={6}>
                    <Section label="Day">
                        <ToggleButtonGroup
                            exclusive value={dayVal}
                            onChange={(_, v) => v && setDayVal(v)}
                            sx={{
                                flexWrap: 'wrap', gap: '4px',
                                '& .MuiToggleButton-root': {
                                    fontFamily: T.mono, fontSize: '9px', fontWeight: 700,
                                    color: '#555', border: '1px solid rgba(255,255,255,0.08)',
                                    borderRadius: '4px !important', p: '4px 6px', lineHeight: 1,
                                },
                                '& .MuiToggleButton-root.Mui-selected': {
                                    color: T.yellow, borderColor: `${T.yellow}50`, background: `${T.yellow}10`,
                                },
                            }}
                        >
                            {DAYS.map(d => <ToggleButton key={d} value={d}>{d}</ToggleButton>)}
                        </ToggleButtonGroup>
                    </Section>
                </Grid>
            </Grid>

            {/* Automation State */}
            <Section label="Automation State">
                <ToggleButtonGroup
                    exclusive value={stateVal}
                    onChange={(_, v) => v && setStateVal(v)}
                    fullWidth
                    sx={{
                        ...tabGroupSx,
                        '& .MuiToggleButton-root.Mui-selected': {
                            color: '#fff', borderColor: 'rgba(255,255,255,0.3)', background: 'rgba(255,255,255,0.08)',
                        },
                    }}
                >
                    {['IDLE', 'ACTIVE', 'HOLDING'].map(s => (
                        <ToggleButton key={s} value={s}>{s}</ToggleButton>
                    ))}
                </ToggleButtonGroup>
            </Section>

            <Button
                variant="outlined" fullWidth onClick={run}
                sx={{
                    fontFamily: T.mono, fontWeight: 700, fontSize: '12px', letterSpacing: '0.5px',
                    color: T.yellow, borderColor: `${T.yellow}55`, background: `${T.yellow}08`,
                    py: '10px',
                    '&:hover': {borderColor: T.yellow, background: `${T.yellow}14`},
                }}
            >
                ▶ Run Evaluation
            </Button>
        </Stack>
    );

    // ── Trace tab ─────────────────────────────────────────────────────────────
    const tracePane = result ? (
        <Stack spacing={1} sx={{pt: 1}}>
            {/* Verdict */}
            <Alert
                icon={<span style={{fontSize: '20px'}}>{vm.icon}</span>}
                sx={{
                    fontFamily: T.mono, background: `${vm.color}0d`,
                    border: `1px solid ${vm.color}35`, color: vm.color,
                    '& .MuiAlert-icon': {color: 'inherit', pt: '6px'},
                    '& .MuiAlert-message': {width: '100%'},
                }}
            >
                <Typography sx={{fontWeight: 700, fontSize: '13px', fontFamily: T.mono, color: vm.color}}>
                    {vm.label}
                </Typography>
                <Typography sx={{fontSize: '10px', color: T.textDim, fontFamily: T.mono, mt: '2px'}}>
                    {vm.stateMsg}
                    {result.activeDurationConds?.length > 0 && result.verdict === 'TRIGGERED' &&
                        ` · HOLDING (${result.activeDurationConds.map(c => `${c.nodeId.split('_').pop()} ${c.durationMinutes}m`).join(', ')})`}
                </Typography>
            </Alert>

            {/* Node rows */}
            {Object.values(result.ctx).map(node => <NodeRow key={node.id} node={node}/>)}
        </Stack>
    ) : <EmptyNote text="Run evaluation first"/>;

    // ── Actions tab ───────────────────────────────────────────────────────────
    const actionsPane = result ? (
        <Stack spacing={2} sx={{pt: 1}}>
            {['positive', 'negative'].map(group => {
                const actions = group === 'positive' ? result.positiveActions : result.negativeActions;
                const isActive = (group === 'positive' && result.verdict === 'TRIGGERED') ||
                    (group === 'negative' && result.verdict === 'RESTORED');
                const gc = group === 'positive' ? T.green : T.red;

                return (
                    <Section key={group}
                             label={`${group} (${actions.length})${isActive ? ' ← executing' : ''}`}
                             sx={{opacity: isActive ? 1 : 0.4}}
                    >
                        {actions.length === 0
                            ? <Typography sx={{color: '#333', fontSize: '10px', fontFamily: T.mono}}>no actions
                                resolved</Typography>
                            : actions.map((a, i) => (
                                <ActionRow key={a.nodeId} action={a} index={i} isActive={isActive} groupColor={gc}/>
                            ))
                        }
                    </Section>
                );
            })}
        </Stack>
    ) : <EmptyNote text="Run evaluation first"/>;

    // ── Log tab ───────────────────────────────────────────────────────────────
    const logPane = runLog.length === 0 ? <EmptyNote text="No runs yet"/> : (
        <Stack sx={{pt: 1}}>
            {runLog.map((e, i) => {
                const m = VERDICT_META[e.verdict];
                return (
                    <Box key={i} sx={{
                        display: 'flex', gap: '8px', alignItems: 'baseline',
                        py: '5px', borderBottom: '1px solid rgba(255,255,255,0.04)',
                    }}>
                        <Typography sx={{
                            color: '#333',
                            fontSize: '10px',
                            minWidth: '70px',
                            fontFamily: T.mono
                        }}>{e.time}</Typography>
                        <Typography sx={{
                            color: m.color,
                            fontSize: '10px',
                            fontWeight: 700,
                            minWidth: '82px',
                            fontFamily: T.mono
                        }}>{e.verdict}</Typography>
                        <Typography sx={{color: '#4a5568', fontSize: '10px', fontFamily: T.mono}}>
                            {e.stateVal} · {e.simTime} {e.day} · {Object.entries(e.payload).map(([k, v]) => `${k}=${v}`).join(' ')}
                        </Typography>
                    </Box>
                );
            })}
        </Stack>
    );

    const TAB_LABELS = [
        '⚙ Inputs',
        '🔬 Trace',
        '⚡ Actions',
        `📋 Log${runLog.length > 0 ? ` (${runLog.length})` : ''}`,
    ];

    return (
        <Dialog open={open} onClose={onClose}
                PaperProps={{sx: {...DIALOG_PAPER_SX, minWidth: 500, maxWidth: 560, maxHeight: '88vh'}}}
        >
            <DialogTitle sx={{pb: 0, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                <Box>
                    <Typography sx={{color: T.yellow, fontWeight: 700, fontSize: '14px', ...monoTypo}}>⚡ Test
                        Harness</Typography>
                    <Typography sx={{
                        color: T.textDim,
                        fontSize: '11px',
                        mt: '2px', ...monoTypo
                    }}>{automation?.name}</Typography>
                </Box>
                <IconButton size="small" onClick={onClose}
                            sx={{color: T.textDim, mt: '-4px', mr: '-8px'}}>✕</IconButton>
            </DialogTitle>

            <Divider sx={{borderColor: T.border, mt: 1}}/>

            {/* Tab bar */}
            <Tabs
                value={tab}
                onChange={(_, v) => setTab(v)}
                variant="fullWidth"
                sx={{
                    minHeight: '36px',
                    px: '12px',
                    pt: '8px',
                    '& .MuiTabs-indicator': {height: '2px'},
                    '& .MuiTab-root': {
                        fontFamily: T.mono, fontSize: '10px', fontWeight: 700,
                        textTransform: 'uppercase', letterSpacing: '0.4px',
                        color: '#555', minHeight: '36px', py: 0,
                        transition: 'color 0.15s',
                    },
                    '& .MuiTab-root.Mui-selected': {color: T.yellow},
                }}
            >
                {TAB_LABELS.map((label, i) => <Tab key={i} label={label}/>)}
            </Tabs>

            <DialogContent sx={{
                pt: '12px', overflowY: 'auto',
                '&::-webkit-scrollbar': {width: '4px'},
                '&::-webkit-scrollbar-thumb': {borderRadius: '2px'},
            }}>
                {tab === 0 && inputsPane}
                {tab === 1 && tracePane}
                {tab === 2 && actionsPane}
                {tab === 3 && logPane}
            </DialogContent>
        </Dialog>
    );
}

// ─── Automation List Item ─────────────────────────────────────────────────────
function AutomationListItem({a, onOpen}) {
    return (
        <ListItem variant="outlined" component={Card} key={a.id}
                  style={{
                      padding: '6px', marginTop: '8px', background: 'transparent',
                      backdropFilter: 'blur(6px)', borderColor: '#ffffff', backgroundColor: 'rgb(255 255 255 / 8%)'
                  }}>
            <ListItemText style={{flex: 1}}>{a.name}</ListItemText>
            <Chip size="small" variant="outlined" color={a.isEnabled ? 'primary' : 'error'}
                  label={a.isEnabled ? 'Enabled' : 'Disabled'} style={{marginRight: '4px'}}/>
            <Button size="small" onClick={() => onOpen(a)}>Open</Button>
        </ListItem>
    );
}

// ─── Main Component ───────────────────────────────────────────────────────────
function ActionBoardDetailComponent() {
    const [nodes, setNodes] = useNodesState([]);
    const [edges, setEdges] = useEdgesState([]);
    const [automations, setAutomations] = useState([]);
    const [selectedAutomation, setSelectedAutomation] = useState({});
    const [automationDetail, setAutomationDetail] = useState({});
    const reactFlowWrapper = useRef(null);
    const {screenToFlowPosition} = useReactFlow();
    const [type, setType] = useDnD();
    const [rfInstance, setRfInstance] = useState(null);
    const [snoozeOpen, setSnoozeOpen] = useState(false);
    const [testOpen, setTestOpen] = useState(false);

    const fetchData = async () => {
        try {
            setAutomations(await getActions());
        } catch (e) {
            console.error(e);
        }
    };
    useEffect(() => {
        fetchData();
    }, []);

    const onSave = useCallback(() => {
        if (!rfInstance) return;
        const flow = rfInstance.toObject();
        const seenN = new Set();
        const uniqueNodes = flow.nodes.filter(n => {
            if (seenN.has(n.id)) return false;
            seenN.add(n.id);
            return true;
        });
        const nodeIds = new Set(uniqueNodes.map(n => n.id));
        const cleanEdges = flow.edges.filter(e => nodeIds.has(e.source) && nodeIds.has(e.target) && (!e.sourceHandle || e.sourceHandle.includes(':')));
        const seenE = new Set();
        const uniqueEdges = cleanEdges.filter(e => {
            if (seenE.has(e.id)) return false;
            seenE.add(e.id);
            return true;
        });
        const cleanFlow = {...flow, nodes: uniqueNodes, edges: uniqueEdges, id: automationDetail.id || ''};
        saveAutomationDetail(cleanFlow).then(fetchData);
        localStorage.setItem('flow', JSON.stringify(cleanFlow));
    }, [rfInstance, automationDetail]);

    const handleDisableAutomation = async (e) => await disableAutomation(selectedAutomation.id, e.target.checked);
    const onDragOver = useCallback(e => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    }, []);
    const onDrop = useCallback(e => {
        e.preventDefault();
        if (!type) return;
        const position = screenToFlowPosition({x: e.clientX, y: e.clientY});
        setNodes(nds => nds.concat({id: getId(type), type, position, data: {value: {isNewNode: true, name: type}}}));
    }, [screenToFlowPosition, type]);

    const openAutomation = async (a) => {
        setSelectedAutomation(a);
        const detail = await getAutomationDetail(a.id);
        setAutomationDetail(detail);
        const nodeIds = new Set((detail.nodes || []).map(n => n.id));
        const seenE = new Set();
        const cleanEdges = (detail.edges || []).filter(edge => {
            if (seenE.has(edge.id)) return false;
            seenE.add(edge.id);
            const legSrc = edge.sourceHandle && !edge.sourceHandle.includes(':');
            return nodeIds.has(edge.source) && nodeIds.has(edge.target) && !legSrc;
        });
        const maxId = detail.nodes.reduce((max, n) => {
            const m = n.id.match(/_(\d+)$/);
            return m ? Math.max(max, parseInt(m[1])) : max;
        }, 0);
        id = maxId + 1;
        const deduped = new Map();
        for (const edge of cleanEdges) {
            const k = `${edge.source}→${edge.target}`;
            edge.animated = true;
            if (!deduped.has(k) || edge.targetHandle?.includes('action:in:')) deduped.set(k, edge);
        }
        setNodes(detail.nodes || []);
        setEdges([...deduped.values()]);
        setSnoozeOpen(false);
        setTestOpen(false);
    };

    const clearBoard = () => {
        setSelectedAutomation({});
        setAutomationDetail({});
        setNodes([]);
        setEdges([]);
        setSnoozeOpen(false);
        setTestOpen(false);
        fetchData();
    };

    const onNodesChange = useCallback(c => setNodes(nds => applyNodeChanges(c, nds)), [setNodes]);
    const onEdgesChange = useCallback(c => setEdges(eds => applyEdgeChanges(c, eds)), [setEdges]);
    const onConnect = useCallback(conn => {
        setEdges(eds => eds.filter(e => e.target !== conn.target));
        const isNeg = conn.sourceHandle?.includes('cond-negative');
        setEdges(eds => addEdge({
            ...conn,
            type: 'custom-edge',
            animated: true,
            data: {color: isNeg ? '#f44336' : '#4caf50'}
        }, eds));
    }, [setEdges]);

    const defaultViewport = useMemo(() => ({x: 0, y: 50, zoom: 0.75}), []);
    const onDragStart = (e, nodeType) => {
        setType(nodeType);
        e.dataTransfer.effectAllowed = 'move';
    };
    const hasSelected = Boolean(selectedAutomation?.id);

    return (
        <Box sx={{position: 'relative', zIndex: 0}}>
            <Stack direction="row">
                {/* Canvas */}
                <Box sx={{width: '80%', height: '100dvh', borderRadius: '10px', p: '10px 10px 10px 0px'}}
                     className="reactflow-wrapper" ref={reactFlowWrapper}>
                    <ReactFlow
                        style={{
                            borderRadius: '10px', backgroundColor: 'transparent',
                            borderColor: 'rgb(255 255 255 / 18%)', borderWidth: '2px', borderStyle: 'dashed',
                            position: 'relative', zIndex: 0
                        }}
                        colorMode="dark" nodes={nodes} edges={edges}
                        onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
                        onConnect={onConnect} onInit={setRfInstance}
                        edgeTypes={{'custom-edge': CustomEdge}}
                        defaultViewport={defaultViewport}
                        fitView
                        onDrop={onDrop} onDragOver={onDragOver}
                        className="validationflow"
                        nodeTypes={{
                            trigger: TriggerNode,
                            action: ActionNode,
                            condition: ConditionNode,
                            valueReader: ValueReaderNode,
                            and: And,
                            or: Or
                        }}
                    >
                        {/* Bottom-left controls — visible only when automation is open */}

                        {hasSelected && (
                            <Panel position="bottom-left" style={{
                                marginBottom: '20px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                flexWrap: 'wrap'
                            }}>
                                <Box sx={{
                                    display: 'flex', alignItems: 'center', gap: '6px', px: '10px', py: '5px',
                                    background: 'transparent', backdropFilter: 'blur(8px)',
                                    borderRadius: '8px', border: '1px solid rgba(255,255,255,0.1)'
                                }}>
                                    <Typography variant="body2"
                                                sx={{fontSize: '11px', fontFamily: T.mono, color: 'text.secondary'}}>
                                        Enabled
                                    </Typography>
                                    <Switch size="small" checked={selectedAutomation.isEnabled}
                                            onChange={handleDisableAutomation}

                                    />
                                </Box>

                                <Tooltip title="Simulate and trace automation evaluation" arrow>
                                    <Button size="small" variant="outlined" onClick={() => setTestOpen(true)}
                                            sx={{
                                                color: T.blue,
                                            }}>
                                        ⚡ Test
                                    </Button>
                                </Tooltip>

                                <Tooltip title="Snooze or temporarily disable this automation" arrow>
                                    <Button size="small" variant="outlined" onClick={() => setSnoozeOpen(true)}
                                            sx={{
                                                color: T.yellow,
                                            }}>
                                        ⏸ Snooze
                                    </Button>
                                </Tooltip>
                            </Panel>
                        )}
                        <Controls orientation="horizontal" position="top-left"/>
                        <Panel position="bottom-right" style={{marginBottom: '20px'}}>
                            <Button size="small" variant="outlined" onClick={onSave}
                                    style={{marginLeft: '10px'}}>Save</Button>
                            <Button size="small" variant="outlined" onClick={clearBoard}
                                    style={{marginLeft: '10px'}}>Clear</Button>
                        </Panel>
                    </ReactFlow>
                </Box>

                {/* Sidebar */}
                <Box sx={{width: '20%', height: '100dvh'}}>
                    <Card style={{
                        height: '97dvh',
                        display: 'flex',
                        flexDirection: 'column',
                        margin: '10px 10px 10px 0px',
                        borderRadius: '10px',
                        background: 'transparent',
                        backdropFilter: 'blur(6px)',
                        borderColor: 'rgb(255 255 255 / 18%)',
                        borderWidth: '2px',
                        borderStyle: 'dashed'
                    }}>
                        <CardContent style={{
                            flex: 1,
                            padding: '16px',
                            display: 'flex',
                            flexDirection: 'column',
                            overflow: 'hidden'
                        }}>
                            <Typography variant="h6">Automation Playground</Typography>
                            <Typography gutterBottom sx={{color: 'text.secondary', fontSize: 14}}>
                                Drag and drop nodes to create automations.
                            </Typography>
                            <Box sx={{p: '10px', display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
                                {[
                                    {style: triggerStyle, type: 'trigger', label: 'Add Trigger'},
                                    {style: conditionStyle, type: 'condition', label: 'Add Condition'},
                                    {style: actionStyle, type: 'action', label: 'Add Action'},
                                    {style: conditionStyle, type: 'and', label: 'Add AND'},
                                    {style: conditionStyle, type: 'or', label: 'Add OR'},
                                ].map(({style: s, type: t, label}) => (
                                    <div key={t} style={{...s, marginTop: '10px'}} draggable
                                         onDragStart={e => onDragStart(e, t)}>{label}</div>
                                ))}
                            </Box>
                            <Box sx={{flex: 1, overflow: 'auto', scrollbarWidth: 'none', mt: '16px', p: '10px'}}>
                                <Typography>Saved Automations</Typography>
                                <List>
                                    {automations.map(a => <AutomationListItem key={a.id} a={a}
                                                                              onOpen={openAutomation}/>)}
                                </List>
                            </Box>
                        </CardContent>
                    </Card>
                </Box>
            </Stack>

            {/* Dialogs at root — centered in full viewport */}
            {hasSelected && (
                <>
                    <SnoozeDialog
                        open={snoozeOpen}
                        automation={selectedAutomation}
                        onClose={() => setSnoozeOpen(false)}
                        onStatusChange={fetchData}
                    />
                    <TestHarnessDialog
                        open={testOpen}
                        automation={selectedAutomation}
                        onClose={() => setTestOpen(false)}
                    />
                </>
            )}
        </Box>
    );
}

// ─── DnD context ──────────────────────────────────────────────────────────────
const DnDContext = createContext([null, (_) => {
}]);
const DnDProvider = ({children}) => {
    const [type, setType] = useState(null);
    return <DnDContext.Provider value={[type, setType]}>{children}</DnDContext.Provider>;
};
const useDnD = () => useContext(DnDContext);

const ActionBoardDetail = memo(ActionBoardDetailComponent);
const ActionBoard = () => <DnDProvider><ActionBoardDetail/></DnDProvider>;
export default React.memo(ActionBoard);