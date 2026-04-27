/**
 * AutomationFeatures.jsx
 *
 * Three dialogs plugging into the existing ActionBoard UI:
 *   - SceneManagerDialog     — create / trigger scenes
 *   - AbTestDialog           — start / view / end A/B tests
 *   - VersionHistoryDialog   — browse versions, diff, rollback
 *
 * Matches existing token system (T.*), component helpers, and dialog patterns.
 * Import and drop into ActionBoardDetailComponent alongside SnoozeDialog.
 */

import React, {useCallback, useEffect, useState,} from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    IconButton,
    InputLabel,
    LinearProgress,
    MenuItem,
    Select,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import HistoryIcon from '@mui/icons-material/History';
import ScienceIcon from '@mui/icons-material/Science';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RestoreIcon from '@mui/icons-material/Restore';
import {
    copyAutomation,
    createAbTest,
    deleteAutomation,
    deleteScene,
    endAbTest,
    getAbDivergences,
    getAbTests,
    getScenes,
    getVersions,
    pauseAbTest,
    resumeAbTest,
    rollback,
    saveScene,
    triggerScene
} from "../../services/apis.jsx";

// ─── Re-use token system from parent ─────────────────────────────────────────
const T = {
    yellow: '#ffd821',
    blue: '#74b9ff',
    green: '#00e5a0',
    red: '#ff4757',
    orange: '#ff6b35',
    purple: '#c084fc',
    surface: '#111318',
    border: 'rgba(255,213,33,0.12)',
    textDim: '#718096',
    textMid: '#b0b0b0',
    mono: '"JetBrains Mono", monospace',
};

const DIALOG_PAPER_SX = {
    backgroundColor: 'rgba(255, 255, 255, 0.0)',
    backdropFilter: 'blur(8px)',
};
const darkField = {
    fontSize: '12px',
    '& .MuiOutlinedInput-root': {

        fontSize: '12px',
        '&.Mui-focused fieldset': {borderColor: T.yellow}
    },
    '& .MuiInputLabel-root': {fontSize: '11px', color: T.textDim},
    '& .MuiInputLabel-root.Mui-focused': {color: T.yellow},
};
const darkSelect = {
    fontSize: '12px', color: '#e2e8f0',
    '& .MuiOutlinedInput-notchedOutline': {borderColor: 'rgba(255,255,255,0.1)'},
    '&.Mui-focused .MuiOutlinedInput-notchedOutline': {borderColor: T.yellow},
    '& .MuiSvgIcon-root': {color: T.textDim},
};

function Section({label, children, accent, sx}) {
    return (
        <Box sx={{
            background: 'rgba(255,255,255,0.025)', borderRadius: '8px',
            p: '10px 12px', border: `1px solid ${accent ? accent + '22' : 'rgba(255,255,255,0.06)'}`,
            ...sx,
        }}>
            {label && (
                <Typography sx={{
                    fontWeight: 700, textTransform: 'uppercase',
                    letterSpacing: '1.2px', mb: '8px',
                    color: accent || T.textDim,
                }}>
                    {label}
                </Typography>
            )}
            {children}
        </Box>
    );
}

function DialogHeader({icon, title, subtitle, accent, onClose, loading}) {
    return (
        <>
            <DialogTitle sx={{pb: 0.5, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'}}>
                <Box>
                    <Typography sx={{color: accent || T.yellow, fontWeight: 700, fontSize: '14px'}}>
                        {icon} {title}
                    </Typography>
                    {subtitle && (
                        <Typography sx={{fontSize: '11px', mt: '2px',}}>
                            {subtitle}
                        </Typography>
                    )}
                </Box>
                <IconButton size="small" onClick={onClose}
                            sx={{mt: '-4px', mr: '-8px'}}>✕</IconButton>
            </DialogTitle>
            {loading &&
                <LinearProgress sx={{height: '2px', '& .MuiLinearProgress-bar': {background: accent || T.yellow}}}/>}
            <Divider sx={{borderColor: T.border, mt: 1}}/>
        </>
    );
}

function EmptyNote({text, icon}) {
    return (
        <Typography sx={{fontSize: '11px', textAlign: 'center', py: 4,}}>
            {icon} {text}
        </Typography>
    );
}

function Tag({label, color}) {
    return (
        <Chip label={label} size="small" sx={{
            fontWeight: 700, height: '18px',
            background: `${color}18`, color, border: `1px solid ${color}35`,
        }}/>
    );
}

// ═════════════════════════════════════════════════════════════════════════════
// API helpers — replace with your actual api.jsx imports
// ═════════════════════════════════════════════════════════════════════════════

const baseUri = "http://localhost:8010/api/v1/automations"
// const api = {
//     // Scenes
//     getScenes: () => fetch(baseUri + '/scenes').then(r => r.json()),
//     saveScene: (s) => fetch(baseUri + '/api/scenes', {
//         method: 'POST',
//         headers: {'Content-Type': 'application/json'},
//         body: JSON.stringify(s)
//     }).then(r => r.json()),
//     deleteScene: (id) => fetch(`${baseUri}/scenes/${id}`, {method: 'DELETE'}),
//     triggerScene: (id, by) => fetch(`${baseUri}/scenes/${id}/trigger`, {
//         method: 'POST',
//         headers: {'Content-Type': 'application/json'},
//         body: JSON.stringify({triggeredBy: by})
//     }).then(r => r.json()),
//     toggleScene: (id, en) => fetch(`${baseUri}/scenes/${id}/toggle?enabled=${en}`, {method: 'PATCH'}).then(r => r.json()),
//
//     // A/B Tests
//     getAbTests: () => fetch(baseUri + '/ab-tests').then(r => r.json()),
//     createAbTest: (t) => fetch(baseUri + '/ab-tests', {
//         method: 'POST',
//         headers: {'Content-Type': 'application/json'},
//         body: JSON.stringify(t)
//     }).then(r => r.json()),
//     pauseAbTest: (id) => fetch(`${baseUri}/ab-tests/${id}/pause`, {method: 'POST'}).then(r => r.json()),
//     resumeAbTest: (id) => fetch(`${baseUri}/ab-tests/${id}/resume`, {method: 'POST'}).then(r => r.json()),
//     endAbTest: (id, winner, conclusion) => fetch(`${baseUri}/ab-tests/${id}/end`, {
//         method: 'POST',
//         headers: {'Content-Type': 'application/json'},
//         body: JSON.stringify({winner, conclusion})
//     }).then(r => r.json()),
//     getAbDivergences: (id) => fetch(`${baseUri}/ab-tests/${id}/divergences`).then(r => r.json()),
//     getAbLogs: (id) => fetch(`${baseUri}/ab-tests/${id}/logs?limit=30`).then(r => r.json()),
//
//     // Versions
//     getVersions: (aid) => fetch(`${baseUri}/${aid}/versions`).then(r => r.json()),
//     rollback: (aid, v, user) => fetch(`${baseUri}/${aid}/versions/${v}/rollback`, {
//         method: 'POST',
//         headers: {'Content-Type': 'application/json'},
//         body: JSON.stringify({user})
//     }).then(r => r.json()),
//     // Management
//     copyAutomation: (id) => fetch(`/api/v1/action/${id}/copy`, {method: 'POST'}).then(r => r.json()),
//     deleteAutomation: (id) => fetch(`/api/v1/${id}`, {method: 'DELETE'}).then(r => r.json()),
// };

// ═════════════════════════════════════════════════════════════════════════════
// 3.7  SCENE MANAGER DIALOG
// ═════════════════════════════════════════════════════════════════════════════

export function SceneManagerDialog({open, automation, automations, onClose}) {
    const [scenes, setScenes] = useState([]);
    const [loading, setLoading] = useState(false);
    const [triggering, setTriggering] = useState(null); // sceneId being triggered
    const [creating, setCreating] = useState(false);
    const [newScene, setNewScene] = useState({name: '', description: '', members: []});

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setScenes(await getScenes());
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (open) load();
    }, [open, load]);

    const handleTrigger = async (sceneId) => {
        setTriggering(sceneId);
        try {
            await triggerScene(sceneId, 'ui');
        } catch (e) {
            console.error(e);
        } finally {
            setTriggering(null);
        }
    };

    const handleDelete = async (sceneId) => {
        await deleteScene(sceneId);
        load();
    };

    const handleSave = async () => {
        if (!newScene.name.trim() || newScene.members.length === 0) return;
        setLoading(true);
        try {
            await saveScene(newScene);
            setNewScene({name: '', description: '', members: []});
            setCreating(false);
            load();
        } finally {
            setLoading(false);
        }
    };

    const addMember = () => {
        if (!automation?.id) return;
        setNewScene(s => ({
            ...s,
            members: [...s.members, {
                automationId: automation.id,
                automationName: automation.name,
                order: s.members.length + 1,
                delayAfterSeconds: 0
            }]
        }));
    };

    const updateMember = (i, field, val) => {
        setNewScene(s => {
            const m = [...s.members];
            m[i] = {...m[i], [field]: val};
            if (field === 'automationId') {
                const found = automations?.find(a => a.id === val);
                if (found) m[i].automationName = found.name;
            }
            return {...s, members: m};
        });
    };

    const removeMember = (i) => setNewScene(s => ({...s, members: s.members.filter((_, idx) => idx !== i)}));

    return (
        <Dialog open={open} onClose={onClose}
                PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
            <DialogHeader icon="🎬" title="Scene Manager" accent={T.purple}
                          subtitle="Group automations and trigger them together"
                          loading={loading} onClose={onClose}/>

            <DialogContent sx={{pt: 2, overflowY: 'auto', '&::-webkit-scrollbar': {width: '4px'}}}>
                <Stack spacing={2}>

                    {/* Existing scenes */}
                    {scenes.length === 0 && !creating && <EmptyNote icon="🎬" text="No scenes yet — create one below"/>}

                    {scenes.map(scene => (
                        <Section key={scene.id} accent={T.purple} sx={{position: 'relative'}}>
                            <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                <Box>
                                    <Typography
                                        sx={{fontWeight: 700, fontSize: '12px',}}>
                                        {scene.name}
                                    </Typography>
                                    {scene.description && (
                                        <Typography
                                            sx={{fontSize: '10px', mt: '2px'}}>
                                            {scene.description}
                                        </Typography>
                                    )}
                                    <Stack direction="row" spacing={0.5} sx={{mt: '6px', flexWrap: 'wrap', gap: '4px'}}>
                                        {(scene.members || []).map((m, i) => (
                                            <Tag key={i} label={`${m.order}. ${m.automationName || m.automationId}`}
                                            />
                                        ))}
                                    </Stack>
                                    {scene.lastTriggeredAt && (
                                        <Typography
                                            sx={{mt: '4px'}}>
                                            Last triggered: {new Date(scene.lastTriggeredAt).toLocaleString()}
                                        </Typography>
                                    )}
                                </Box>

                                <Stack direction="row" spacing={0.5} alignItems="center">
                                    <Tooltip title="Trigger scene now">
                                        <span>
                                            <IconButton size="small" disabled={triggering === scene.id}
                                                        onClick={() => handleTrigger(scene.id)}
                                                        sx={{
                                                            color: T.green,
                                                            border: `1px solid ${T.green}30`,
                                                            borderRadius: '6px',
                                                            p: '4px'
                                                        }}>
                                                {triggering === scene.id
                                                    ? <CircularProgress size={14} sx={{color: T.green}}/>
                                                    : <PlayArrowIcon sx={{fontSize: 16}}/>}
                                            </IconButton>
                                        </span>
                                    </Tooltip>
                                    <IconButton size="small" onClick={() => handleDelete(scene.id)}
                                                sx={{
                                                    color: T.red,
                                                    border: `1px solid ${T.red}30`,
                                                    borderRadius: '6px',
                                                    p: '4px'
                                                }}>
                                        <DeleteIcon sx={{fontSize: 14}}/>
                                    </IconButton>
                                </Stack>
                            </Stack>
                        </Section>
                    ))}

                    {/* Create new scene */}
                    {!creating ? (
                        <Button variant="outlined" fullWidth onClick={() => setCreating(true)}
                                startIcon={<AddIcon/>}
                                sx={{
                                    fontSize: '11px', fontWeight: 700,
                                    color: T.purple, borderColor: `${T.purple}50`,
                                    '&:hover': {borderColor: T.purple, background: `${T.purple}0d`}
                                }}>
                            New Scene
                        </Button>
                    ) : (
                        <Section accent={T.purple} label="New Scene">
                            <Stack spacing={1.5}>
                                <TextField size="small" label="Scene Name" value={newScene.name}
                                           onChange={e => setNewScene(s => ({...s, name: e.target.value}))}
                                           sx={darkField}/>
                                <TextField size="small" label="Description (optional)" value={newScene.description}
                                           onChange={e => setNewScene(s => ({...s, description: e.target.value}))}
                                           sx={darkField}/>

                                {/* Members */}
                                <Typography sx={{

                                    fontWeight: 700,
                                    textTransform: 'uppercase',
                                    letterSpacing: '1px',

                                }}>
                                    Members
                                </Typography>

                                {newScene.members.map((m, i) => (
                                    <Stack key={i} direction="row" spacing={1} alignItems="center">
                                        <FormControl size="small" sx={{flex: 2}}>
                                            <InputLabel sx={{

                                                fontSize: '11px',
                                            }}>Automation</InputLabel>
                                            <Select variant="outlined" value={m.automationId} label="Automation"
                                                    onChange={e => updateMember(i, 'automationId', e.target.value)}
                                                    sx={darkSelect}>
                                                {(automations || []).map(a => (
                                                    <MenuItem key={a.id} value={a.id} sx={{

                                                        fontSize: '12px'
                                                    }}>{a.name}</MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                        <TextField size="small" label="Order" type="number" value={m.order}
                                                   onChange={e => updateMember(i, 'order', parseInt(e.target.value) || 1)}
                                                   sx={{width: '70px', ...darkField}} inputProps={{min: 1}}/>
                                        <TextField size="small" label="Delay(s)" type="number"
                                                   value={m.delayAfterSeconds}
                                                   onChange={e => updateMember(i, 'delayAfterSeconds', parseInt(e.target.value) || 0)}
                                                   sx={{width: '80px', ...darkField}} inputProps={{min: 0}}/>
                                        <IconButton size="small" onClick={() => removeMember(i)}
                                                    sx={{color: T.red}}>
                                            <DeleteIcon sx={{fontSize: 14}}/>
                                        </IconButton>
                                    </Stack>
                                ))}

                                <Button size="small" variant="outlined" onClick={addMember} startIcon={<AddIcon/>}
                                        sx={{

                                            fontSize: '10px',
                                            color: T.purple,
                                            borderColor: `${T.purple}40`,
                                            '&:hover': {borderColor: T.purple, background: `${T.purple}0d`}
                                        }}>
                                    Add Member
                                </Button>

                                <Stack direction="row" spacing={1} justifyContent="flex-end">
                                    <Button size="small" onClick={() => {
                                        setCreating(false);
                                        setNewScene({name: '', description: '', members: []});
                                    }}
                                    >Cancel</Button>
                                    <Button size="small" variant="outlined" onClick={handleSave}
                                            disabled={!newScene.name.trim() || newScene.members.length === 0}
                                            sx={{
                                                color: T.purple, borderColor: `${T.purple}50`,
                                                '&:hover': {borderColor: T.purple, background: `${T.purple}0d`}
                                            }}>
                                        Save Scene
                                    </Button>
                                </Stack>
                            </Stack>
                        </Section>
                    )}
                </Stack>
            </DialogContent>
        </Dialog>
    );
}

// ═════════════════════════════════════════════════════════════════════════════
// 3.8  A/B TEST DIALOG
// ═════════════════════════════════════════════════════════════════════════════

const STATUS_META = {
    RUNNING: {color: T.green, label: 'RUNNING', icon: '🧪'},
    PAUSED: {color: T.yellow, label: 'PAUSED', icon: '⏸'},
    ENDED: {label: 'ENDED', icon: '✓'},
};

export function AbTestDialog({open, automation, automations, onClose}) {
    const [tests, setTests] = useState([]);
    const [loading, setLoading] = useState(false);
    const [creating, setCreating] = useState(false);
    const [selected, setSelected] = useState(null); // test being inspected
    const [divergences, setDivergences] = useState([]);
    const [divLoading, setDivLoading] = useState(false);
    const [endModal, setEndModal] = useState(null); // testId to end
    const [winner, setWinner] = useState('A');
    const [conclusion, setConclusion] = useState('');
    const [newTest, setNewTest] = useState({name: '', description: '', variantBId: ''});

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setTests(await getAbTests());
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (open) {
            load();
            setCreating(false);
            setSelected(null);
        }
    }, [open, load]);

    const loadDivergences = async (testId) => {
        setDivLoading(true);
        try {
            setDivergences(await getAbDivergences(testId));
        } catch (e) {
            console.error(e);
        } finally {
            setDivLoading(false);
        }
    };

    const selectTest = (t) => {
        setSelected(t);
        loadDivergences(t.id);
    };

    const handleCreate = async () => {
        if (!newTest.name.trim() || !newTest.variantBId || !automation?.id) return;
        setLoading(true);
        try {
            await createAbTest({...newTest, variantAId: automation.id});
            setCreating(false);
            setNewTest({name: '', description: '', variantBId: ''});
            load();
        } finally {
            setLoading(false);
        }
    };

    const handleEnd = async () => {
        if (!endModal) return;
        setLoading(true);
        try {
            await endAbTest(endModal, winner, conclusion);
            setEndModal(null);
            setConclusion('');
            setSelected(null);
            load();
        } finally {
            setLoading(false);
        }
    };

    const togglePause = async (t) => {
        setLoading(true);
        try {
            t.status === 'RUNNING' ? await pauseAbTest(t.id) : await resumeAbTest(t.id);
            load();
        } finally {
            setLoading(false);
        }
    };

    const agreementPct = (t) => t.stats?.totalEvaluations > 0
        ? Math.round(t.stats.agreementRate * 100) : null;

    // ── Test list ─────────────────────────────────────────────────────────────
    const listView = (
        <Stack spacing={2}>
            {tests.length === 0 && !creating &&
                <EmptyNote icon="🧪" text="No A/B tests — create one to shadow-evaluate a candidate automation"/>}

            {tests.map(t => {
                const sm = STATUS_META[t.status] || STATUS_META.ENDED;
                const pct = agreementPct(t);
                const isRunning = t.status === 'RUNNING';
                return (
                    <Section key={t.id} accent={sm.color}>
                        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                            <Box sx={{flex: 1}}>
                                <Stack direction="row" spacing={1} alignItems="center" sx={{mb: '4px'}}>
                                    <Tag label={sm.icon + ' ' + sm.label} color={sm.color}/>
                                    <Typography
                                        sx={{fontWeight: 700, fontSize: '12px',}}>
                                        {t.name}
                                    </Typography>
                                </Stack>
                                {t.description && (
                                    <Typography
                                        sx={{fontSize: '10px', mb: '4px'}}>
                                        {t.description}
                                    </Typography>
                                )}
                                <Stack direction="row" spacing={1} sx={{mt: '4px', flexWrap: 'wrap', gap: '4px'}}>
                                    <Tag label={'A: ' + (t.variantAId?.slice(-6) || '?')} color={T.blue}/>
                                    <Tag label={'B: ' + (t.variantBId?.slice(-6) || '?')} color={T.orange}/>
                                    {pct !== null && (
                                        <Tag label={`${pct}% agree · ${t.stats.totalEvaluations} evals`}
                                             color={pct > 80 ? T.green : pct > 50 ? T.yellow : T.red}/>
                                    )}
                                </Stack>
                            </Box>

                            <Stack direction="row" spacing={0.5}>
                                <Tooltip title="View divergences">
                                    <IconButton size="small" onClick={() => selectTest(t)}
                                                sx={{
                                                    color: T.blue,
                                                    border: `1px solid ${T.blue}30`,
                                                    borderRadius: '6px',
                                                    p: '4px'
                                                }}>
                                        <ScienceIcon sx={{fontSize: 14}}/>
                                    </IconButton>
                                </Tooltip>
                                {t.status !== 'ENDED' && (
                                    <>
                                        <Tooltip title={isRunning ? 'Pause' : 'Resume'}>
                                            <IconButton size="small" onClick={() => togglePause(t)}
                                                        sx={{
                                                            color: T.yellow,
                                                            border: `1px solid ${T.yellow}30`,
                                                            borderRadius: '6px',
                                                            p: '4px'
                                                        }}>
                                                <Typography sx={{fontSize: '10px',}}>
                                                    {isRunning ? '⏸' : '▶'}
                                                </Typography>
                                            </IconButton>
                                        </Tooltip>
                                        <Tooltip title="End test">
                                            <IconButton size="small" onClick={() => {
                                                setEndModal(t.id);
                                                setWinner('A');
                                                setConclusion('');
                                            }}
                                                        sx={{
                                                            color: T.green,
                                                            border: `1px solid ${T.green}30`,
                                                            borderRadius: '6px',
                                                            p: '4px'
                                                        }}>
                                                <CheckCircleIcon sx={{fontSize: 14}}/>
                                            </IconButton>
                                        </Tooltip>
                                    </>
                                )}
                            </Stack>
                        </Stack>
                    </Section>
                );
            })}

            {/* Create button */}
            {!creating && (
                <Button variant="outlined" fullWidth onClick={() => setCreating(true)} startIcon={<AddIcon/>}
                        sx={{
                            fontSize: '11px', fontWeight: 700,
                            color: T.orange, borderColor: `${T.orange}50`,
                            '&:hover': {borderColor: T.orange, background: `${T.orange}0d`}
                        }}>
                    New A/B Test for {automation?.name}
                </Button>
            )}

            {creating && (
                <Section accent={T.orange} label="New A/B Test">
                    <Stack spacing={1.5}>
                        <TextField size="small" label="Test Name" value={newTest.name}
                                   onChange={e => setNewTest(s => ({...s, name: e.target.value}))}
                                   sx={darkField}/>
                        <TextField size="small" label="Description (optional)" value={newTest.description}
                                   onChange={e => setNewTest(s => ({...s, description: e.target.value}))}
                                   sx={darkField}/>

                        <Box sx={{
                            p: '8px 10px',
                            borderRadius: '6px',
                            background: `${T.blue}0d`,
                            border: `1px solid ${T.blue}25`
                        }}>
                            <Typography sx={{fontSize: '10px', fontWeight: 700}}>
                                Variant A (live)
                            </Typography>
                            <Typography sx={{fontSize: '11px', mt: '2px'}}>
                                {automation?.name} — will keep firing normally
                            </Typography>
                        </Box>

                        <FormControl size="small" fullWidth>
                            <InputLabel sx={{fontSize: '11px'}}>
                                Variant B (candidate — must be disabled)
                            </InputLabel>
                            <Select variant="outlined" value={newTest.variantBId}
                                    label="Variant B (candidate — must be disabled)"
                                    onChange={e => setNewTest(s => ({...s, variantBId: e.target.value}))}
                                    sx={darkSelect}>
                                {(automations || [])
                                    .filter(a => a.id !== automation?.id)
                                    .map(a => (
                                        <MenuItem key={a.id} value={a.id} sx={{fontSize: '12px'}}>
                                            {a.name} {a.isEnabled ? '⚠ (enabled)' : ''}
                                        </MenuItem>
                                    ))}
                            </Select>
                        </FormControl>

                        <Alert severity="info" sx={{
                            fontSize: '10px', py: '4px',
                            background: `${T.blue}0d`, border: `1px solid ${T.blue}25`, color: T.blue,
                            '& .MuiAlert-icon': {color: T.blue}
                        }}>
                            Variant B will be evaluated in shadow mode — its actions are never executed.
                            Create variant B in the editor first with the candidate logic.
                        </Alert>

                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" onClick={() => setCreating(false)}
                            >Cancel</Button>
                            <Button size="small" variant="outlined" onClick={handleCreate}
                                    disabled={!newTest.name.trim() || !newTest.variantBId}
                                    sx={{
                                        color: T.orange, borderColor: `${T.orange}50`,
                                        '&:hover': {borderColor: T.orange, background: `${T.orange}0d`}
                                    }}>
                                Start Test
                            </Button>
                        </Stack>
                    </Stack>
                </Section>
            )}
        </Stack>
    );

    // ── Divergence detail view ────────────────────────────────────────────────
    const detailView = selected && (
        <Stack spacing={2}>
            <Stack direction="row" alignItems="center" spacing={1}>
                <IconButton size="small" onClick={() => setSelected(null)}
                            sx={{

                                border: '1px solid rgba(255,255,255,0.08)',
                                borderRadius: '6px',
                                p: '4px'
                            }}>
                    ←
                </IconButton>
                <Typography sx={{fontWeight: 700, fontSize: '13px',}}>
                    {selected.name} — Divergences
                </Typography>
            </Stack>

            {/* Stats */}
            <Stack direction="row" spacing={1}>
                {[
                    {label: 'Total evals', val: selected.stats?.totalEvaluations ?? 0,},
                    {
                        label: 'Agreement',
                        val: agreementPct(selected) !== null ? `${agreementPct(selected)}%` : '—',
                        color: agreementPct(selected) > 80 ? T.green : T.yellow
                    },
                    {label: 'A triggers', val: selected.stats?.variantATriggerCount ?? 0, color: T.blue},
                    {label: 'B triggers', val: selected.stats?.variantBTriggerCount ?? 0, color: T.orange},
                ].map(s => (
                    <Box key={s.label} sx={{
                        flex: 1,
                        p: '8px',
                        borderRadius: '6px',
                        background: 'rgba(255,255,255,0.03)',
                        border: '1px solid rgba(255,255,255,0.06)',
                        textAlign: 'center'
                    }}>
                        <Typography sx={{
                            fontWeight: 700,
                            fontSize: '16px',

                        }}>{s.val}</Typography>
                        <Typography sx={{


                            textTransform: 'uppercase',
                            letterSpacing: '0.8px'
                        }}>{s.label}</Typography>
                    </Box>
                ))}
            </Stack>

            {/* Divergence list */}
            {divLoading ? <CircularProgress size={20} sx={{color: T.yellow, mx: 'auto'}}/>
                : divergences.length === 0
                    ? <EmptyNote icon="✓" text="No divergences — A and B agree on all evaluations"/>
                    : divergences.map((d, i) => (
                        <Box key={i} sx={{
                            p: '8px 10px',
                            borderRadius: '6px',
                            border: '1px solid rgba(255,71,87,0.2)',
                            background: 'rgba(255,71,87,0.04)'
                        }}>
                            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{mb: '4px'}}>
                                <Typography sx={{}}>
                                    {new Date(d.timestamp).toLocaleTimeString()}
                                </Typography>
                                <Stack direction="row" spacing={0.5}>
                                    <Tag label={`A: ${d.variantA?.rootTrue ? 'TRUE' : 'FALSE'}`} color={T.blue}/>
                                    <SwapHorizIcon sx={{fontSize: 14, color: T.textDim}}/>
                                    <Tag label={`B: ${d.variantB?.rootTrue ? 'TRUE' : 'FALSE'}`} color={T.orange}/>
                                </Stack>
                            </Stack>
                            <Typography sx={{fontSize: '10px',}}>
                                {Object.entries(d.payload || {}).slice(0, 4).map(([k, v]) => `${k}=${v}`).join(' · ')}
                            </Typography>
                            {(d.variantA?.winningBranchDescription || d.variantB?.winningBranchDescription) && (
                                <Stack direction="row" spacing={0.5} sx={{mt: '4px'}}>
                                    {d.variantA?.winningBranchDescription &&
                                        <Tag label={`A→ ${d.variantA.winningBranchDescription}`} color={T.blue}/>}
                                    {d.variantB?.winningBranchDescription &&
                                        <Tag label={`B→ ${d.variantB.winningBranchDescription}`} color={T.orange}/>}
                                </Stack>
                            )}
                        </Box>
                    ))
            }
        </Stack>
    );

    // ── End test modal ────────────────────────────────────────────────────────
    const endTestModal = endModal && (
        <Dialog open PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
            <DialogHeader icon="✓" title="End A/B Test" accent={T.green}
                          loading={loading} onClose={() => setEndModal(null)}/>
            <DialogContent sx={{pt: 2}}>
                <Stack spacing={2}>
                    <Typography sx={{color: T.textMid, fontSize: '12px',}}>
                        Which variant wins?
                    </Typography>
                    <Stack direction="row" spacing={1}>
                        {['A', 'B'].map(v => (
                            <Button key={v} variant="outlined" fullWidth onClick={() => setWinner(v)}
                                    sx={{
                                        fontWeight: 700,
                                        color: winner === v ? (v === 'A' ? T.blue : T.orange) : T.textDim,
                                        borderColor: winner === v ? (v === 'A' ? T.blue : T.orange) + '80' : 'rgba(255,255,255,0.1)',
                                        background: winner === v ? (v === 'A' ? T.blue : T.orange) + '12' : 'transparent',
                                    }}>
                                Variant {v} {v === 'A' ? '(live)' : '(candidate)'}
                            </Button>
                        ))}
                    </Stack>
                    {winner === 'B' && (
                        <Alert severity="warning" sx={{
                            fontSize: '10px', py: '4px',
                            background: `${T.orange}0d`, border: `1px solid ${T.orange}25`, color: T.orange,
                            '& .MuiAlert-icon': {color: T.orange}
                        }}>
                            Variant B will be enabled and Variant A will be disabled.
                        </Alert>
                    )}
                    <TextField size="small" label="Conclusion note" multiline rows={2} value={conclusion}
                               onChange={e => setConclusion(e.target.value)} sx={darkField}/>
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => setEndModal(null)}
                                sx={{color: T.textDim}}>Cancel</Button>
                        <Button size="small" variant="outlined" onClick={handleEnd}
                                sx={{
                                    color: T.green, borderColor: `${T.green}50`,
                                    '&:hover': {borderColor: T.green, background: `${T.green}0d`}
                                }}>
                            Confirm & End
                        </Button>
                    </Stack>
                </Stack>
            </DialogContent>
        </Dialog>
    );

    return (
        <>
            <Dialog open={open} onClose={onClose}
                    PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
                <DialogHeader icon="🧪" title="A/B Testing" accent={T.orange}
                              subtitle={automation ? `Variant A: ${automation.name}` : undefined}
                              loading={loading} onClose={onClose}/>
                <DialogContent sx={{pt: 2, overflowY: 'auto', '&::-webkit-scrollbar': {width: '4px'}}}>
                    {selected ? detailView : listView}
                </DialogContent>
            </Dialog>
            {endTestModal}
        </>
    );
}

// ═════════════════════════════════════════════════════════════════════════════
// 3.9  VERSION HISTORY DIALOG
// ═════════════════════════════════════════════════════════════════════════════

export function VersionHistoryDialog({open, automation, onClose, onRollback}) {
    const [versions, setVersions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [rolling, setRolling] = useState(null); // version number being rolled back
    const [preview, setPreview] = useState(null); // version being previewed
    const [confirmRollback, setConfirmRollback] = useState(null);

    const load = useCallback(async () => {
        if (!automation?.id) return;
        setLoading(true);
        try {
            setVersions(await getVersions(automation.id));
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }, [automation?.id]);

    useEffect(() => {
        if (open) {
            load();
            setPreview(null);
            setConfirmRollback(null);
        }
    }, [open, load]);

    const handleRollback = async () => {
        if (!confirmRollback) return;
        setRolling(confirmRollback.version);
        try {
            await rollback(automation.id, confirmRollback.version, 'ui');
            setConfirmRollback(null);
            onRollback?.();
            onClose();
        } catch (e) {
            console.error(e);
        } finally {
            setRolling(null);
        }
    };

    const fmtDate = (d) => {
        if (!d) return '—';
        const dt = new Date(d);
        return `${dt.toLocaleDateString()} ${dt.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}`;
    };

    const diffSummary = (diff) => {
        if (!diff) return null;
        const parts = [];
        if (diff.addedNodeIds?.length) parts.push(`+${diff.addedNodeIds.length} added`);
        if (diff.removedNodeIds?.length) parts.push(`−${diff.removedNodeIds.length} removed`);
        if (diff.changedNodes?.length) parts.push(`~ ${diff.changedNodes.length} changed`);
        if (diff.changedFields?.length && !parts.length) parts.push(diff.changedFields.join(', '));
        return parts.length ? parts.join(' · ') : 'initial version';
    };

    // ── Version list ──────────────────────────────────────────────────────────
    const listView = (
        <Stack spacing={1.5}>
            {versions.length === 0 &&
                <EmptyNote icon="📋" text="No version history yet — save the automation to create the first version"/>}

            {versions.map((v, idx) => {
                const isLatest = idx === 0;
                const isRolling = rolling === v.version;
                const summary = diffSummary(v.diff);
                const isRollback = v.changeNote?.startsWith('Rolled back');

                return (
                    <Box key={v.id} sx={{
                        p: '10px 12px', borderRadius: '8px',
                        border: `1px solid ${isLatest ? T.green + '35' : 'rgba(255,255,255,0.07)'}`,
                        background: isLatest ? `${T.green}06` : 'rgba(255,255,255,0.02)',
                        cursor: 'pointer', transition: 'border-color 0.15s',
                        '&:hover': {borderColor: isLatest ? T.green + '60' : 'rgba(255,255,255,0.15)'},
                    }}
                         onClick={() => setPreview(preview?.id === v.id ? null : v)}>

                        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Typography sx={{
                                    color: isLatest ? T.green : T.textDim,
                                    fontWeight: 700, fontSize: '13px', minWidth: '30px'
                                }}>
                                    v{v.version}
                                </Typography>
                                {isLatest && <Tag label="CURRENT" color={T.green}/>}
                                {isRollback && <Tag label="ROLLBACK" color={T.yellow}/>}
                            </Stack>

                            <Stack direction="row" spacing={0.5} alignItems="center">
                                <Typography sx={{fontSize: '10px',}}>
                                    {fmtDate(v.savedAt)}
                                </Typography>
                                {!isLatest && (
                                    <Tooltip title={`Roll back to v${v.version}`}>
                                        <span>
                                            <IconButton size="small" disabled={!!rolling}
                                                        onClick={e => {
                                                            e.stopPropagation();
                                                            setConfirmRollback(v);
                                                        }}
                                                        sx={{
                                                            color: T.yellow,
                                                            border: `1px solid ${T.yellow}30`,
                                                            borderRadius: '6px',
                                                            p: '4px',
                                                            ml: '4px'
                                                        }}>
                                                {isRolling
                                                    ? <CircularProgress size={12} sx={{color: T.yellow}}/>
                                                    : <RestoreIcon sx={{fontSize: 14}}/>}
                                            </IconButton>
                                        </span>
                                    </Tooltip>
                                )}
                            </Stack>
                        </Stack>

                        {/* Diff summary */}
                        {summary && (
                            <Typography sx={{fontSize: '10px', mt: '4px'}}>
                                {summary}
                            </Typography>
                        )}
                        {v.changeNote && (
                            <Typography sx={{
                                color: T.textMid,
                                fontSize: '10px',

                                mt: '2px',
                                fontStyle: 'italic'
                            }}>
                                "{v.changeNote}"
                            </Typography>
                        )}
                        {v.savedBy && (
                            <Typography sx={{color: '#333', mt: '2px'}}>
                                by {v.savedBy}
                            </Typography>
                        )}

                        {/* Expanded diff detail */}
                        {preview?.id === v.id && v.diff && (
                            <Box sx={{mt: '8px', pt: '8px', borderTop: '1px solid rgba(255,255,255,0.06)'}}>
                                {v.diff.addedNodeIds?.length > 0 && (
                                    <Typography sx={{color: T.green, fontSize: '10px',}}>
                                        + {v.diff.addedNodeIds.join(', ')}
                                    </Typography>
                                )}
                                {v.diff.removedNodeIds?.length > 0 && (
                                    <Typography sx={{color: T.red, fontSize: '10px',}}>
                                        − {v.diff.removedNodeIds.join(', ')}
                                    </Typography>
                                )}
                                {v.diff.changedNodes?.map((cn, i) => (
                                    <Box key={i} sx={{mt: '4px'}}>
                                        <Typography sx={{color: T.yellow, fontSize: '10px',}}>
                                            ~ {cn.nodeId} ({cn.nodeType})
                                        </Typography>
                                        <Stack direction="row" spacing={1} sx={{mt: '2px'}}>
                                            <Typography sx={{
                                                color: T.red,


                                                flex: 1,
                                                wordBreak: 'break-all'
                                            }}>
                                                {cn.before}
                                            </Typography>
                                            <Typography sx={{color: '#333',}}>→</Typography>
                                            <Typography sx={{
                                                color: T.green,


                                                flex: 1,
                                                wordBreak: 'break-all'
                                            }}>
                                                {cn.after}
                                            </Typography>
                                        </Stack>
                                    </Box>
                                ))}
                                {v.diff.changedFields?.length > 0 && (
                                    <Typography
                                        sx={{fontSize: '10px', mt: '2px'}}>
                                        Fields: {v.diff.changedFields.join(', ')}
                                    </Typography>
                                )}
                            </Box>
                        )}
                    </Box>
                );
            })}
        </Stack>
    );

    // ── Confirm rollback modal ────────────────────────────────────────────────
    const rollbackModal = confirmRollback && (
        <Dialog open PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
            <DialogHeader icon="⏪" title={`Roll back to v${confirmRollback.version}`}
                          loading={!!rolling} onClose={() => setConfirmRollback(null)}/>
            <DialogContent sx={{pt: 2}}>
                <Stack spacing={2}>
                    <Typography sx={{color: T.textMid, fontSize: '12px',}}>
                        This will restore the automation to v{confirmRollback.version}
                        {confirmRollback.savedAt ? ` (saved ${fmtDate(confirmRollback.savedAt)})` : ''}.
                        A new version entry will be created with a rollback note.
                    </Typography>
                    <Alert severity="warning" sx={{
                        fontSize: '10px', py: '4px',

                    }}>
                        The editor will reload with the restored graph. Unsaved changes will be lost.
                    </Alert>
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => setConfirmRollback(null)}
                        >Cancel</Button>
                        <Button size="small" variant="outlined" onClick={handleRollback} disabled={!!rolling}>
                            {rolling ? 'Rolling back…' : 'Confirm Rollback'}
                        </Button>
                    </Stack>
                </Stack>
            </DialogContent>
        </Dialog>
    );

    return (
        <>
            <Dialog open={open} onClose={onClose}
                    PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
                <DialogHeader icon="📋" title="Version History" accent={T.textMid}
                              subtitle={automation?.name}
                              loading={loading} onClose={onClose}/>
                <DialogContent sx={{pt: 2, overflowY: 'auto', '&::-webkit-scrollbar': {width: '4px'}}}>
                    {listView}
                </DialogContent>
            </Dialog>
            {rollbackModal}
        </>
    );
}

// ═════════════════════════════════════════════════════════════════════════════
// COPY & DELETE  — inline in AutomationListItem, no dialog needed
// Exported as hooks + the updated list item component
// ═════════════════════════════════════════════════════════════════════════════

/**
 * useCopyDelete — handles copy and delete for a single automation.
 *
 * Usage:
 *   const { copying, deleting, confirmDelete, handleCopy, handleDelete, setConfirmDelete }
 *       = useCopyDelete({ automation, onCopied, onDeleted });
 */
export function useCopyDelete({automation, onCopied, onDeleted}) {
    const [copying, setCopying] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);

    const handleCopy = useCallback(async () => {
        if (!automation?.id) return;
        setCopying(true);
        try {
            const result = await copyAutomation(automation.id);
            onCopied?.(result);
        } catch (e) {
            console.error('Copy failed:', e);
        } finally {
            setCopying(false);
        }
    }, [automation?.id, onCopied]);

    const handleDelete = useCallback(async () => {
        if (!automation?.id) return;
        setDeleting(true);
        try {
            await deleteAutomation(automation.id);
            setConfirmDelete(false);
            onDeleted?.(automation.id);
        } catch (e) {
            console.error('Delete failed:', e);
        } finally {
            setDeleting(false);
        }
    }, [automation?.id, onDeleted]);

    return {copying, deleting, confirmDelete, setConfirmDelete, handleCopy, handleDelete};
}

/**
 * DeleteConfirmDialog — small inline confirmation dialog.
 * Shown when the user clicks delete in the list.
 */
export function DeleteConfirmDialog({open, automation, deleting, onConfirm, onCancel}) {
    if (!open) return null;
    return (
        <Dialog open PaperProps={{sx: {...DIALOG_PAPER_SX}}}>
            <DialogHeader icon="🗑️" title="Delete Automation" accent={T.red}
                          loading={deleting} onClose={onCancel}/>
            <DialogContent sx={{pt: 2}}>
                <Stack spacing={2}>
                    <Typography sx={{color: T.textMid, fontSize: '12px',}}>
                        Permanently delete
                        <Box component="span" sx={{color: '#e2e8f0', fontWeight: 700}}> {automation?.name}</Box>?
                    </Typography>
                    <Alert severity="error" sx={{
                        fontSize: '10px', py: '4px',
                        background: `${T.red}0d`, border: `1px solid ${T.red}25`, color: T.red,
                        '& .MuiAlert-icon': {color: T.red},
                    }}>
                        This removes the automation, its visual graph, all logs, and full version history.
                        This cannot be undone.
                    </Alert>
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={onCancel}
                                sx={{color: T.textDim}}>
                            Cancel
                        </Button>
                        <Button size="small" variant="outlined" onClick={onConfirm} disabled={deleting}
                                sx={{
                                    fontWeight: 700,
                                    color: T.red, borderColor: `${T.red}55`,
                                    '&:hover': {borderColor: T.red, background: `${T.red}0d`},
                                    '&.Mui-disabled': {opacity: 0.4},
                                }}>
                            {deleting ? 'Deleting…' : 'Delete'}
                        </Button>
                    </Stack>
                </Stack>
            </DialogContent>
        </Dialog>
    );
}

/**
 * AutomationListItemWithActions — drop-in replacement for AutomationListItem.
 *
 * Adds copy (📋) and delete (🗑) icon buttons to each list item.
 * Copy creates a duplicate and refreshes the list.
 * Delete shows a confirmation dialog then removes the automation.
 *
 * Props:
 *   a          — automation object
 *   onOpen     — called when user clicks Open
 *   onRefresh  — called after copy or delete to refresh the list
 *   onDeleted  — called after deletion with the deleted id (use to close editor if open)
 */
export function AutomationListItemWithActions({a, onOpen, onRefresh, onDeleted}) {
    const {
        copying, deleting, confirmDelete, setConfirmDelete,
        handleCopy, handleDelete,
    } = useCopyDelete({
        automation: a,
        onCopied: () => onRefresh?.(),
        onDeleted: (id) => {
            onRefresh?.();
            onDeleted?.(id);
        },
    });

    return (
        <>
            <Box sx={{
                display: 'flex', alignItems: 'center', gap: '6px',
                p: '6px 8px', mt: '6px', borderRadius: '8px',
                border: '1px solid rgba(255,255,255,0.1)',
                transition: 'border-color 0.15s',
                '&:hover': {borderColor: 'rgba(255,255,255,0.2)'},
            }}>
                {/* Name + status */}
                <Box sx={{flex: 1, minWidth: 0}}>
                    <Typography sx={{
                        color: '#e2e8f0', fontSize: '12px',
                        fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    }}>
                        {a.name}
                    </Typography>
                    <Tag
                        label={a.isEnabled ? 'enabled' : 'disabled'}
                        color={a.isEnabled ? T.green : T.red}
                    />
                </Box>

                {/* Action buttons */}
                <Stack direction="row" spacing={0.5} alignItems="center" sx={{flexShrink: 0}}>
                    {/* Open */}
                    <Tooltip title="Open in editor" arrow>
                        <Button size="small" variant="outlined" onClick={() => onOpen(a)}
                                sx={{
                                    fontSize: '10px', fontWeight: 700,
                                    color: T.blue, borderColor: `${T.blue}40`, py: '3px', px: '8px', minWidth: 0,
                                    '&:hover': {borderColor: T.blue, background: `${T.blue}0d`},
                                }}>
                            Open
                        </Button>
                    </Tooltip>

                    {/* Copy */}
                    <Tooltip title="Duplicate automation (starts disabled)" arrow>
                        <span>
                            <IconButton size="small" disabled={copying} onClick={handleCopy}
                                        sx={{
                                            color: T.yellow, border: `1px solid ${T.yellow}30`,
                                            borderRadius: '6px', p: '4px',
                                            '&:hover': {background: `${T.yellow}0d`, borderColor: `${T.yellow}80`},
                                            '&.Mui-disabled': {opacity: 0.4},
                                        }}>
                                {copying
                                    ? <CircularProgress size={12} sx={{color: T.yellow}}/>
                                    : <Typography sx={{fontSize: '12px', lineHeight: 1}}>📋</Typography>
                                }
                            </IconButton>
                        </span>
                    </Tooltip>

                    {/* Delete */}
                    <Tooltip title="Delete automation" arrow>
                        <span>
                            <IconButton size="small" disabled={deleting} onClick={() => setConfirmDelete(true)}
                                        sx={{
                                            color: T.red, border: `1px solid ${T.red}30`,
                                            borderRadius: '6px', p: '4px',
                                            '&:hover': {background: `${T.red}0d`, borderColor: `${T.red}80`},
                                            '&.Mui-disabled': {opacity: 0.4},
                                        }}>
                                <DeleteIcon sx={{fontSize: 14}}/>
                            </IconButton>
                        </span>
                    </Tooltip>
                </Stack>
            </Box>

            <DeleteConfirmDialog
                open={confirmDelete}
                automation={a}
                deleting={deleting}
                onConfirm={handleDelete}
                onCancel={() => setConfirmDelete(false)}
            />
        </>
    );
}

// ═════════════════════════════════════════════════════════════════════════════
// PANEL BUTTONS — drop these into the existing bottom-left Panel
// Replace the existing Panel content in ActionBoardDetailComponent
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Usage in ActionBoardDetailComponent:
 *
 * 1. Add state:
 *    const [sceneOpen, setSceneOpen] = useState(false);
 *    const [abTestOpen, setAbTestOpen] = useState(false);
 *    const [versionOpen, setVersionOpen] = useState(false);
 *
 * 2. Replace the Panel content with <AutomationPanelButtons .../> below.
 *
 * 3. Add dialogs alongside SnoozeDialog and TestHarnessDialog:
 *    <SceneManagerDialog   open={sceneOpen}   automation={selectedAutomation} automations={automations} onClose={() => setSceneOpen(false)}/>
 *    <AbTestDialog         open={abTestOpen}  automation={selectedAutomation} automations={automations} onClose={() => setAbTestOpen(false)}/>
 *    <VersionHistoryDialog open={versionOpen} automation={selectedAutomation} onClose={() => setVersionOpen(false)} onRollback={() => { openAutomation(selectedAutomation); }}/>
 */
export function AutomationPanelButtons({
                                           hasSelected,
                                           selectedAutomation,
                                           onSnooze, onTest, onScene, onAbTest, onVersion
                                       }) {
    return (
        <>
            {/* Bottom-left: automation controls */}
            {hasSelected && (
                <div
                    style={{display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap'}}>
                    <Tooltip title="Manage scenes" arrow>
                        <Button size="small" variant="outlined" onClick={onScene}
                                sx={{
                                    color: T.purple, borderColor: `${T.purple}40`,
                                    '&:hover': {borderColor: T.purple, background: `${T.purple}0d`}
                                }}>
                            🎬 Scene
                        </Button>
                    </Tooltip>
                    <Tooltip title="A/B test this automation" arrow>
                        <Button size="small" variant="outlined" onClick={onAbTest}
                                sx={{
                                    color: T.orange, borderColor: `${T.orange}40`,
                                    '&:hover': {borderColor: T.orange, background: `${T.orange}0d`}
                                }}>
                            🧪 A/B Test
                        </Button>
                    </Tooltip>
                    <Tooltip title="Version history & rollback" arrow>
                        <Button size="small" variant="outlined" onClick={onVersion}
                                sx={{
                                    color: T.textMid,
                                    borderColor: 'rgba(255,255,255,0.15)',
                                    '&:hover': {
                                        borderColor: 'rgba(255,255,255,0.4)',
                                        background: 'rgba(255,255,255,0.04)'
                                    },
                                    display: 'flex',
                                    gap: '4px',
                                    alignItems: 'center'
                                }}>
                            <HistoryIcon sx={{fontSize: 13}}/> History
                        </Button>
                    </Tooltip>
                </div>
            )}
        </>
    );
}