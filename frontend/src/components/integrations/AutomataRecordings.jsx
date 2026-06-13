import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    FormControlLabel,
    Grid,
    IconButton,
    InputLabel,
    LinearProgress,
    MenuItem,
    Paper,
    Select,
    Slider,
    Stack,
    Switch,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tabs,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import {
    AccessTime,
    Add,
    Bolt,
    Close,
    Delete,
    Download,
    FiberManualRecord,
    FilterAlt,
    HourglassEmpty,
    Memory,
    PlayArrow,
    Refresh,
    Replay,
    Stop,
    Storage,
    Visibility,
} from '@mui/icons-material';
import {useCachedDevices} from '../../services/AppCacheContext.jsx';
import {
    createSession,
    deleteSession,
    exportSessionCsv,
    getSessionBuckets,
    getSessions,
    startSession,
    stopSession,
} from '../../services/apis.jsx';
import GpsRoutePanel from "../../utils/GpsRoutePanel.jsx";

// ── helpers ───────────────────────────────────────────────────────────────────
const fmt = (n) => (n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n));

const elapsed = (start) => {
    if (!start) return '—';
    const s = Math.floor((Date.now() - new Date(start).getTime()) / 1000);
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
    return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
};

const STATUS_MAP = {
    ACTIVE: {color: 'success', label: 'ACTIVE'},
    STOPPED: {color: 'default', label: 'STOPPED'},
    PENDING: {color: 'warning', label: 'PENDING'},
    ERROR: {color: 'error', label: 'ERROR'},
};

export const TRIGGER_ICONS = {
    MANUAL: <FiberManualRecord sx={{fontSize: 15, color: '#1D9E75'}}/>,
    CONDITION: <FilterAlt sx={{fontSize: 15, color: '#534AB7'}}/>,
    AUTOMATION: <Bolt sx={{fontSize: 15, color: '#BA7517'}}/>,
};

// ── StatusChip ────────────────────────────────────────────────────────────────
const StatusChip = ({status}) => {
    const {color, label} = STATUS_MAP[status] || STATUS_MAP.STOPPED;
    return (
        <Chip
            size="small" color={color}
            label={
                <Stack direction="row" alignItems="center" spacing={0.5}>
                    {status === 'ACTIVE' && (
                        <Box sx={{
                            width: 6, height: 6, borderRadius: '50%', bgcolor: 'currentColor',
                            animation: 'pulse 1.4s infinite',
                            '@keyframes pulse': {'0%,100%': {opacity: 1}, '50%': {opacity: 0.3}},
                        }}/>
                    )}
                    <span>{label}</span>
                </Stack>
            }
            sx={{fontWeight: 600, fontSize: 11}}
        />
    );
};

// ── RawReadingsDialog ─────────────────────────────────────────────────────────
const RawReadingsDialog = ({bucket, deviceName, open, onClose}) => {
    if (!bucket) return null;
    const readings = bucket.readings ?? [];
    const allKeys = readings.length > 0
        ? Object.keys(readings[0]).filter((k) => k !== 'ts')
        : [];

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
            <DialogTitle sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                <Box>
                    <Typography variant="subtitle1" fontWeight={600}>
                        Raw readings — {deviceName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        Bucket {bucket.bucketStart} · {readings.length} readings
                    </Typography>
                </Box>
                <IconButton size="small" onClick={onClose}><Close fontSize="small"/></IconButton>
            </DialogTitle>
            <DialogContent sx={{p: 0}}>
                {readings.length === 0 ? (
                    <Box py={4} textAlign="center" color="text.secondary">
                        <Typography variant="body2">No readings in this bucket yet.</Typography>
                    </Box>
                ) : (
                    <TableContainer sx={{maxHeight: 480}}>
                        <Table size="small" stickyHeader>
                            <TableHead>
                                <TableRow>
                                    <TableCell sx={{fontWeight: 600, fontSize: 12}}>Timestamp</TableCell>
                                    {allKeys.map((k) => (
                                        <TableCell key={k} sx={{fontWeight: 600, fontSize: 12}}>
                                            {k}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {readings.map((r, i) => (
                                    <TableRow key={i} hover>
                                        <TableCell sx={{fontSize: 11, fontFamily: 'monospace', whiteSpace: 'nowrap'}}>
                                            {r.ts ? new Date(r.ts).toLocaleTimeString([], {
                                                hour: '2-digit', minute: '2-digit', second: '2-digit',
                                                fractionalSecondDigits: 1,
                                            }) : '—'}
                                        </TableCell>
                                        {allKeys.map((k) => (
                                            <TableCell key={k} sx={{fontSize: 11, fontFamily: 'monospace'}}>
                                                {r[k] != null ? String(r[k]) : '—'}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </DialogContent>
        </Dialog>
    );
};

// ── TriggerCard ───────────────────────────────────────────────────────────────
const TriggerCard = ({type, selected, onSelect}) => {
    const meta = {
        MANUAL: {
            icon: <FiberManualRecord sx={{fontSize: 22, color: '#1D9E75'}}/>,
            label: 'Manual',
            sub: 'Start/stop yourself'
        },
        CONDITION: {
            icon: <FilterAlt sx={{fontSize: 22, color: '#534AB7'}}/>,
            label: 'Condition',
            sub: 'Trigger on field value'
        },
        AUTOMATION: {
            icon: <Bolt sx={{fontSize: 22, color: '#BA7517'}}/>,
            label: 'Automation',
            sub: 'Started by a rule'
        },
    }[type];

    return (
        <Paper
            variant="outlined" onClick={() => onSelect(type)}
            sx={{
                p: 1.5, textAlign: 'center', cursor: 'pointer', userSelect: 'none',
                border: selected ? '1.5px solid #1D9E75' : '1px solid',
                borderColor: selected ? '#1D9E75' : 'divider',
                bgcolor: selected ? 'rgba(29,158,117,0.06)' : 'background.paper',
                transition: 'all 0.15s',
                '&:hover': {borderColor: '#1D9E75', bgcolor: 'rgba(29,158,117,0.04)'},
            }}
        >
            {meta.icon}
            <Typography variant="caption" display="block" fontWeight={600} mt={0.5}>{meta.label}</Typography>
            <Typography variant="caption" color="text.secondary" display="block">{meta.sub}</Typography>
        </Paper>
    );
};

// ── ConditionFields ───────────────────────────────────────────────────────────
const ConditionFields = ({label, value, onChange, devices}) => (
    <>
        <Typography variant="overline" color="text.secondary" display="block" mb={1}>{label}</Typography>
        <Grid container spacing={1} mb={1.5}>
            <Grid item xs={5}>
                <FormControl fullWidth size="small">
                    <InputLabel>Device</InputLabel>
                    <Select label="Device" value={value.deviceId}
                            onChange={(e) => onChange({...value, deviceId: e.target.value})}>
                        {devices.map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)}
                    </Select>
                </FormControl>
            </Grid>
            <Grid item xs={3}>
                <FormControl fullWidth size="small">
                    <InputLabel>Op</InputLabel>
                    <Select label="Op" value={value.operator}
                            onChange={(e) => onChange({...value, operator: e.target.value})}>
                        {[['GT', '>'], ['GTE', '≥'], ['LT', '<'], ['LTE', '≤'], ['EQ', '=']].map(([v, l]) => (
                            <MenuItem key={v} value={v}>{l}</MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </Grid>
            <Grid item xs={4}>
                <TextField fullWidth size="small" label="Value" value={value.value}
                           onChange={(e) => onChange({...value, value: e.target.value})}/>
            </Grid>
            <Grid item xs={12}>
                <TextField fullWidth size="small" label="Field name" placeholder="e.g. speed"
                           value={value.field}
                           onChange={(e) => onChange({...value, field: e.target.value})}/>
            </Grid>
        </Grid>
    </>
);

// ── NewSessionForm ────────────────────────────────────────────────────────────
const NewSessionForm = ({devices, onCancel, onCreate}) => {
    const [name, setName] = useState('');
    const [trigger, setTrigger] = useState('MANUAL');
    const [selectedDevices, setSelectedDevices] = useState([]);
    const [duration, setDuration] = useState(60);
    const [startCond, setStartCond] = useState({deviceId: '', field: '', operator: 'GT', value: ''});
    const [useStop, setUseStop] = useState(false);
    const [stopCond, setStopCond] = useState({deviceId: '', field: '', operator: 'LT', value: ''});
    const [loading, setLoading] = useState(false);

    const toggleDevice = (id) =>
        setSelectedDevices((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

    const handleCreate = async () => {
        setLoading(true);
        try {
            await onCreate({
                name: name.trim() || 'Untitled session',
                triggerType: trigger,
                deviceIds: selectedDevices,
                durationLimitSecs: duration * 60,
                ...(trigger === 'CONDITION' && {startCondition: startCond}),
                ...(trigger === 'CONDITION' && useStop && {stopCondition: stopCond}),
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card variant="outlined">
            <CardContent sx={{p: 2.5}}>
                <Typography variant="subtitle1" fontWeight={600} mb={2}>Create recording session</Typography>

                <TextField fullWidth label="Session name" size="small" value={name}
                           onChange={(e) => setName(e.target.value)}
                           placeholder="e.g. GPS drive test – June 12" sx={{mb: 2}}/>

                <Typography variant="caption" color="text.secondary" display="block" mb={0.75}>
                    Trigger type
                </Typography>
                <Grid container spacing={1} mb={2}>
                    {['MANUAL', 'CONDITION', 'AUTOMATION'].map((t) => (
                        <Grid item xs={4} key={t}>
                            <TriggerCard type={t} selected={trigger === t} onSelect={setTrigger}/>
                        </Grid>
                    ))}
                </Grid>

                {/* AUTOMATION info banner */}
                {trigger === 'AUTOMATION' && (
                    <Alert severity="info" sx={{mb: 2, fontSize: 12}}>
                        This session will be started automatically when an automation rule fires a
                        <strong> RECORDING_START</strong> action. Create the session first, then link it
                        inside the automation's action config using the session ID shown after saving.
                    </Alert>
                )}

                <Typography variant="caption" color="text.secondary" display="block" mb={0.75}>
                    Devices to record
                </Typography>
                <Stack direction="row" flexWrap="wrap" gap={0.75} mb={0.5}>
                    {devices.map((d) => (
                        <Chip
                            key={d.id} label={d.name} size="small" icon={<Memory sx={{fontSize: 14}}/>}
                            onClick={() => toggleDevice(d.id)}
                            variant={selectedDevices.includes(d.id) ? 'filled' : 'outlined'}
                            color={selectedDevices.includes(d.id) ? 'success' : 'default'}
                            sx={{cursor: 'pointer', fontSize: 12}}
                        />
                    ))}
                </Stack>
                <Typography variant="caption" color="text.secondary">
                    Select none to record all active devices
                </Typography>

                {trigger === 'CONDITION' && (
                    <>
                        <Divider sx={{my: 2}}/>
                        <ConditionFields
                            label="Start condition" value={startCond}
                            onChange={setStartCond} devices={devices}
                        />
                        <FormControlLabel
                            control={
                                <Switch checked={useStop} onChange={(e) => setUseStop(e.target.checked)} size="small"/>
                            }
                            label={
                                <Box>
                                    <Typography variant="body2">Stop condition</Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Auto-stop when field matches
                                    </Typography>
                                </Box>
                            }
                            sx={{mb: useStop ? 1.5 : 0}}
                        />
                        {useStop && (
                            <ConditionFields
                                label="Stop condition" value={stopCond}
                                onChange={setStopCond} devices={devices}
                            />
                        )}
                        <Divider sx={{mt: 1, mb: 2}}/>
                    </>
                )}

                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>
                    Duration limit
                </Typography>
                <Stack direction="row" alignItems="center" spacing={2} mb={0.5}>
                    <Slider value={duration} min={0} max={240} step={5}
                            onChange={(_, v) => setDuration(v)} sx={{flex: 1, color: '#1D9E75'}}/>
                    <Typography variant="body2" fontWeight={600} minWidth={64} textAlign="right">
                        {duration === 0 ? 'No limit' : `${duration} min`}
                    </Typography>
                </Stack>
                <Typography variant="caption" color="text.secondary">Set to 0 for no limit</Typography>

                <Stack direction="row" justifyContent="flex-end" spacing={1} mt={3}>
                    <Button variant="outlined" size="small" onClick={onCancel} disabled={loading}>
                        Cancel
                    </Button>
                    <Button
                        variant="contained" size="small" onClick={handleCreate} disabled={loading}
                        startIcon={loading ? <CircularProgress size={12} color="inherit"/> : null}
                        sx={{bgcolor: '#1D9E75', '&:hover': {bgcolor: '#17856A'}}}
                    >
                        {loading ? 'Creating…' : 'Create session'}
                    </Button>
                </Stack>
            </CardContent>
        </Card>
    );
};

// ── SessionCard ───────────────────────────────────────────────────────────────
const SessionCard = ({session, devices, onStop, onStart, onReplay, onDelete}) => {
    const devNames = session.deviceIds.map((id) => devices?.find((d) => d.id === id)?.name || id);
    const isClickable = session.status === 'STOPPED' || session.status === 'ACTIVE';
    const progressPct = session.durationLimitSecs && session.startTime
        ? Math.min(100, Math.round(
            (Date.now() - new Date(session.startTime).getTime()) / 1000 / session.durationLimitSecs * 100
        ))
        : null;

    return (
        <Card
            variant="outlined"
            onClick={() => isClickable && onReplay(session.id)}
            sx={{
                mb: 1.5, transition: 'box-shadow 0.15s',
                cursor: isClickable ? 'pointer' : 'default',
                '&:hover': isClickable ? {boxShadow: 2} : {},
            }}
        >
            <CardContent sx={{p: 2, '&:last-child': {pb: 2}}}>
                <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
                    <Box flex={1} minWidth={0}>
                        <Stack direction="row" alignItems="center" spacing={1} flexWrap="wrap" mb={0.5}>
                            <Typography variant="body2" fontWeight={600}>{session.name}</Typography>
                            <StatusChip status={session.status}/>
                            {isClickable && (
                                <Typography variant="caption" color="text.secondary" sx={{fontStyle: 'italic'}}>
                                    {session.status === 'STOPPED' ? 'Click to replay' : 'Click to view'}
                                </Typography>
                            )}
                        </Stack>
                        <Stack direction="row" spacing={2} flexWrap="wrap">
                            <Stack direction="row" alignItems="center" spacing={0.4}>
                                {TRIGGER_ICONS[session.triggerType]}
                                <Typography variant="caption" color="text.secondary">
                                    {session.triggerType.toLowerCase()}
                                </Typography>
                            </Stack>
                            <Stack direction="row" alignItems="center" spacing={0.4}>
                                <Memory sx={{fontSize: 13, color: 'text.secondary'}}/>
                                <Typography variant="caption" color="text.secondary">
                                    {devNames.join(', ') || 'all devices'}
                                </Typography>
                            </Stack>
                            <Stack direction="row" alignItems="center" spacing={0.4}>
                                <Storage sx={{fontSize: 13, color: 'text.secondary'}}/>
                                <Typography variant="caption">
                                    <strong>{fmt(session.recordCount)}</strong> readings
                                </Typography>
                            </Stack>
                            {session.status === 'ACTIVE' && (
                                <Stack direction="row" alignItems="center" spacing={0.4}>
                                    <AccessTime sx={{fontSize: 13, color: 'text.secondary'}}/>
                                    <Typography variant="caption">
                                        <strong>{elapsed(session.startTime)}</strong>
                                    </Typography>
                                </Stack>
                            )}
                            {!!session.durationLimitSecs && (
                                <Stack direction="row" alignItems="center" spacing={0.4}>
                                    <HourglassEmpty sx={{fontSize: 13, color: 'text.secondary'}}/>
                                    <Typography variant="caption">
                                        {session.durationLimitSecs / 60}m limit
                                    </Typography>
                                </Stack>
                            )}
                        </Stack>
                    </Box>

                    {/* Action buttons — stop propagation so card click doesn't fire */}
                    <Stack direction="row" spacing={0.5} onClick={(e) => e.stopPropagation()}>
                        {session.status === 'ACTIVE' && (
                            <Button size="small" variant="outlined" color="error"
                                    startIcon={<Stop sx={{fontSize: 14}}/>}
                                    onClick={() => onStop(session.id)} sx={{fontSize: 12}}>
                                Stop
                            </Button>
                        )}
                        {session.status === 'PENDING' && (
                            <Button size="small" variant="contained"
                                    startIcon={<PlayArrow sx={{fontSize: 14}}/>}
                                    onClick={() => onStart(session.id)}
                                    sx={{fontSize: 12, bgcolor: '#1D9E75', '&:hover': {bgcolor: '#17856A'}}}>
                                Start
                            </Button>
                        )}
                        {session.status === 'STOPPED' && (
                            <Button size="small" variant="outlined"
                                    startIcon={<Replay sx={{fontSize: 14}}/>}
                                    onClick={() => onReplay(session.id)} sx={{fontSize: 12}}>
                                Replay
                            </Button>
                        )}
                        <Tooltip title="Delete session">
                            <IconButton size="small" color="error"
                                        onClick={() => onDelete(session.id)}>
                                <Delete sx={{fontSize: 16}}/>
                            </IconButton>
                        </Tooltip>
                    </Stack>
                </Stack>

                {session.status === 'ACTIVE' && progressPct !== null && (
                    <Box mt={1.5}>
                        <Stack direction="row" justifyContent="space-between" mb={0.5}>
                            <Typography variant="caption" color="text.secondary">Duration</Typography>
                            <Typography variant="caption" color="text.secondary">{progressPct}%</Typography>
                        </Stack>
                        <LinearProgress variant="determinate" value={progressPct}
                                        sx={{
                                            height: 4, borderRadius: 2, bgcolor: 'action.hover',
                                            '& .MuiLinearProgress-bar': {bgcolor: '#1D9E75'},
                                        }}/>
                    </Box>
                )}
            </CardContent>
        </Card>
    );
};

// ── BucketRow ─────────────────────────────────────────────────────────────────
const BucketRow = ({bucket, maxCount, devices, onViewReadings}) => {
    const devName = devices.find((d) => d.id === bucket.deviceId)?.name || bucket.deviceId;
    const pct = Math.round((bucket.count / maxCount) * 100);

    return (
        <Stack
            direction="row" alignItems="center" spacing={1.5} py={1}
            sx={{borderBottom: '0.5px solid', borderColor: 'divider'}}
        >
            <Typography variant="caption" color="text.secondary" minWidth={48}>
                {bucket.bucketStart}
            </Typography>
            <Typography variant="caption" color="text.secondary" minWidth={90} noWrap>
                {devName}
            </Typography>
            <Box flex={1} height={6} bgcolor="action.hover" borderRadius={1} overflow="hidden">
                <Box height="100%" width={`${pct}%`} bgcolor="#5DCAA5" borderRadius={1}
                     sx={{transition: 'width 0.3s'}}/>
            </Box>
            <Typography variant="caption" color="text.secondary" minWidth={40} textAlign="right">
                {bucket.count}/60
            </Typography>
            <Tooltip title="View raw readings">
                <span>
                    <IconButton
                        size="small"
                        disabled={!bucket.readings?.length}
                        onClick={() => onViewReadings(bucket, devName)}
                    >
                        <Visibility sx={{fontSize: 14}}/>
                    </IconButton>
                </span>
            </Tooltip>
        </Stack>
    );
};

// ── SessionDetail ─────────────────────────────────────────────────────────────
const SessionDetail = ({session, devices, onStop, onSelectSession, sessions}) => {
    const [rawBucket, setRawBucket] = useState(null);
    const [rawDeviceName, setRawDeviceName] = useState('');
    const [exportLoading, setExportLoading] = useState(false);

    const handleViewReadings = (bucket, devName) => {
        setRawBucket(bucket);
        setRawDeviceName(devName);
    };

    const handleExport = async () => {
        if (!session) return;
        setExportLoading(true);
        try {
            await exportSessionCsv(session.id, session.name);
        } finally {
            setExportLoading(false);
        }
    };

    if (!session) {
        return (
            <Box>
                {sessions?.length > 0 && (
                    <Box mb={2}>
                        <Typography variant="caption" color="text.secondary" display="block" mb={0.75}>
                            Select a session to replay
                        </Typography>
                        <FormControl fullWidth size="small">
                            <InputLabel>Session</InputLabel>
                            <Select
                                label="Session"
                                value=""
                                onChange={(e) => onSelectSession(e.target.value)}
                            >
                                {sessions.map((s) => (
                                    <MenuItem key={s.id} value={s.id}>
                                        {s.name} — {s.status}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Box>
                )}
                <Box textAlign="center" py={5} color="text.secondary">
                    <Storage sx={{fontSize: 40, opacity: 0.3, display: 'block', mx: 'auto', mb: 1}}/>
                    <Typography variant="body2">No session selected.</Typography>
                </Box>
            </Box>
        );
    }

    const devNames = session.deviceIds?.map((id) => devices.find((d) => d.id === id)?.name || id) ?? [];
    const buckets = session.buckets ?? [];
    const maxCount = Math.max(...buckets.map((b) => b.count), 1);
    const uniqueDevices = new Set(buckets.map((b) => b.deviceId)).size;


    return (
        <Box>
            <RawReadingsDialog
                bucket={rawBucket} deviceName={rawDeviceName}
                open={!!rawBucket} onClose={() => setRawBucket(null)}
            />

            {/* Session picker when a session IS loaded — allow switching */}
            {sessions?.length > 0 && (
                <Box mb={2}>
                    <FormControl fullWidth size="small">
                        <InputLabel>Session</InputLabel>
                        <Select
                            variant="outlined"
                            label="Session"
                            value={session.id}
                            onChange={(e) => onSelectSession(e.target.value)}
                        >
                            {sessions.map((s) => (
                                <MenuItem key={s.id} value={s.id}>
                                    {s.name} — {s.status}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                </Box>
            )}

            {/* Header */}
            <Stack direction="row" alignItems="flex-start" justifyContent="space-between"
                   mb={2} flexWrap="wrap" gap={1}>
                <Box>
                    <Typography variant="subtitle1" fontWeight={600}>{session.name}</Typography>
                    <StatusChip status={session.status}/>
                </Box>
                <Stack direction="row" spacing={1}>
                    {session.status === 'ACTIVE' && (
                        <Button size="small" variant="outlined" color="error"
                                startIcon={<Stop sx={{fontSize: 14}}/>}
                                onClick={() => onStop(session.id)} sx={{fontSize: 12}}>
                            Stop
                        </Button>
                    )}
                    <Button size="small" variant="outlined"
                            startIcon={exportLoading
                                ? <CircularProgress size={12}/>
                                : <Download sx={{fontSize: 14}}/>}
                            onClick={handleExport} disabled={exportLoading || buckets.length === 0}
                            sx={{fontSize: 12}}>
                        {exportLoading ? 'Exporting…' : 'Export CSV'}
                    </Button>
                </Stack>
            </Stack>

            {/* Stats */}
            <Grid container spacing={1} mb={2}>
                {[
                    {val: fmt(session.recordCount), lbl: 'readings'},
                    {val: buckets.length, lbl: 'buckets'},
                    {val: uniqueDevices, lbl: 'devices'},
                ].map(({val, lbl}) => (
                    <Grid item xs={4} key={lbl}>
                        <Paper variant="outlined" sx={{p: 1.5, textAlign: 'center'}}>
                            <Typography variant="h5" fontWeight={500}>{val}</Typography>
                            <Typography variant="caption" color="text.secondary">{lbl}</Typography>
                        </Paper>
                    </Grid>
                ))}
            </Grid>

            {/* Meta */}
            <Paper variant="outlined" sx={{p: 1.5, mb: 2}}>
                <Grid container spacing={2}>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="caption" color="text.secondary" display="block">Started</Typography>
                        <Typography variant="body2">
                            {session.startTime ? new Date(session.startTime).toLocaleString() : 'Not started'}
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="caption" color="text.secondary" display="block">Ended</Typography>
                        <Typography variant="body2">
                            {session.endTime
                                ? new Date(session.endTime).toLocaleString()
                                : session.status === 'ACTIVE' ? 'Running…' : '—'}
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="caption" color="text.secondary" display="block">Trigger</Typography>
                        <Stack direction="row" alignItems="center" spacing={0.5}>
                            {TRIGGER_ICONS[session.triggerType]}
                            <Typography variant="body2">{session.triggerType}</Typography>
                        </Stack>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="caption" color="text.secondary" display="block">Devices</Typography>
                        <Typography variant="body2">{devNames.join(', ') || 'All devices'}</Typography>
                    </Grid>
                </Grid>
            </Paper>

            {/* Buckets */}
            <Typography variant="overline" color="text.secondary" display="block" mb={1}>
                Data buckets{' '}
                <Typography component="span" variant="caption" color="text.secondary"
                            sx={{textTransform: 'none', letterSpacing: 0}}>
                    — 1 bucket = 60 readings per device
                </Typography>
            </Typography>

            {buckets.length === 0 ? (
                <Box textAlign="center" py={3} color="text.secondary">
                    <Storage sx={{fontSize: 32, opacity: 0.3, display: 'block', mx: 'auto', mb: 1}}/>
                    <Typography variant="body2">
                        No data yet.
                        {session.status === 'PENDING' ? ' Start the session to begin recording.' : ''}
                    </Typography>
                </Box>
            ) : (
                buckets.map((b, i) => (
                    <BucketRow
                        key={`${b.deviceId}-${b.bucketStart}-${i}`}
                        bucket={b} maxCount={maxCount} devices={devices}
                        onViewReadings={handleViewReadings}
                    />
                ))
            )}

            <GpsRoutePanel session={session} devices={devices}/>
        </Box>
    );
};

// ── Main ──────────────────────────────────────────────────────────────────────
const Recordings = () => {
    const [tab, setTab] = useState(0);
    const [view, setView] = useState('list');
    const [sessions, setSessions] = useState([]);
    const [detailSession, setDetailSession] = useState(null);
    const [listLoading, setListLoading] = useState(false);
    const {devices} = useCachedDevices();

    // Polling ref — refresh active session recordCounts every 10s
    const pollRef = useRef(null);

    const fetchSessions = useCallback(async () => {
        setListLoading(true);
        try {
            const data = await getSessions();
            setSessions(data);
        } finally {
            setListLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchSessions();
        // Poll while any session is ACTIVE
        pollRef.current = setInterval(() => {
            setSessions((prev) => {
                const hasActive = prev.some((s) => s.status === 'ACTIVE');
                if (hasActive) fetchSessions();
                return prev;
            });
        }, 10_000);
        return () => clearInterval(pollRef.current);
    }, [fetchSessions]);

    const handleTabChange = (_, newVal) => {
        setTab(newVal);
        setView('list');
    };

    // ── Stop ────────────────────────────────────────────────────────────────
    const handleStopSession = useCallback(async (id) => {
        // stopSession returns the updated RecordingSession (not buckets)
        const updated = await stopSession(id);
        setSessions((prev) => prev.map((s) => (s.id === id ? updated : s)));

        // If the stopped session is open in the Replay tab, reload it with buckets
        if (detailSession?.id === id) {
            const withBuckets = await getSessionBuckets(id);
            setDetailSession(withBuckets);
        }
    }, [detailSession?.id]);

    // ── Start ───────────────────────────────────────────────────────────────
    const handleStartSession = useCallback(async (id) => {
        const updated = await startSession(id);
        setSessions((prev) => prev.map((s) => (s.id === id ? updated : s)));
    }, []);

    // ── Replay ──────────────────────────────────────────────────────────────
    const handleReplay = useCallback(async (id) => {
        const withBuckets = await getSessionBuckets(id);
        setDetailSession(withBuckets);
        setTab(1);
        setView('list');
    }, []);

    // Called from the Replay tab session picker dropdown
    const handleSelectSession = useCallback(async (id) => {
        const withBuckets = await getSessionBuckets(id);
        setDetailSession(withBuckets);
    }, []);

    // ── Delete ──────────────────────────────────────────────────────────────
    const handleDelete = useCallback(async (id) => {
        await deleteSession(id);
        setSessions((prev) => prev.filter((s) => s.id !== id));
        if (detailSession?.id === id) {
            setDetailSession(null);
            setTab(0);
        }
    }, [detailSession?.id]);

    // ── Create ──────────────────────────────────────────────────────────────
    const handleCreate = useCallback(async (payload) => {
        const created = await createSession(payload);
        setSessions((prev) => [created, ...prev]);
        setView('list');
    }, []);

    return (
        <Box sx={{p: {xs: 2, sm: 3}, maxWidth: 860, mx: 'auto'}}>
            <Stack direction="row" alignItems="flex-start" justifyContent="space-between" mb={2.5}>
                <Box>
                    <Typography variant="h6" fontWeight={600}>Recordings</Typography>
                    <Typography variant="body2" color="text.secondary">
                        Capture live device data over time
                    </Typography>
                </Box>
                {view === 'list' && (
                    <Stack direction="row" spacing={1}>
                        <Tooltip title="Refresh">
                            <IconButton size="small" onClick={fetchSessions} disabled={listLoading}>
                                <Refresh sx={{fontSize: 18}}/>
                            </IconButton>
                        </Tooltip>
                        <Button
                            variant="contained" size="small" startIcon={<Add/>}
                            onClick={() => setView('new')}
                            sx={{bgcolor: '#1D9E75', '&:hover': {bgcolor: '#17856A'}}}
                        >
                            New recording
                        </Button>
                    </Stack>
                )}
            </Stack>

            {view === 'list' && (
                <Tabs
                    value={tab} onChange={handleTabChange}
                    sx={{
                        mb: 2, minHeight: 36, borderBottom: 1, borderColor: 'divider',
                        '& .MuiTab-root': {minHeight: 36, textTransform: 'none', fontSize: 13, py: 1},
                        '& .Mui-selected': {color: 'text.primary', fontWeight: 600},
                        '& .MuiTabs-indicator': {bgcolor: 'text.primary'},
                    }}
                >
                    <Tab label="Sessions"/>
                    <Tab label="Replay"/>
                </Tabs>
            )}

            {view === 'new' ? (
                <NewSessionForm
                    devices={devices}
                    onCancel={() => setView('list')}
                    onCreate={handleCreate}
                />
            ) : tab === 0 ? (
                <>
                    {listLoading && <LinearProgress sx={{mb: 1, borderRadius: 1}}/>}
                    {!listLoading && sessions.length === 0 ? (
                        <Box textAlign="center" py={5} color="text.secondary">
                            <Storage sx={{fontSize: 40, opacity: 0.3, display: 'block', mx: 'auto', mb: 1}}/>
                            <Typography variant="body2">
                                No recordings yet.<br/>Create one to start capturing device data.
                            </Typography>
                        </Box>
                    ) : (
                        sessions.map((s) => (
                            <SessionCard
                                key={s.id} session={s} devices={devices}
                                onStop={handleStopSession}
                                onStart={handleStartSession}
                                onReplay={handleReplay}
                                onDelete={handleDelete}
                            />
                        ))
                    )}
                </>
            ) : (
                <SessionDetail
                    session={detailSession}
                    devices={devices}
                    sessions={sessions}
                    onStop={handleStopSession}
                    onSelectSession={handleSelectSession}
                />
            )}
        </Box>
    );
};

export default Recordings;