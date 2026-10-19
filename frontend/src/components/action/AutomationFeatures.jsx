/**
 * AutomationFeatures.jsx
 *
 * Dialogs and list-item actions plugging into the ActionBoard UI:
 *   - VersionHistoryDialog          — browse versions, diff, rollback
 *   - AutomationListItemWithActions — open / copy / delete rows in the sidebar list
 *   - AutomationPanelButtons        — canvas toolbar buttons
 *
 * Matches existing token system (T.*), component helpers, and dialog patterns.
 */

import React, {useCallback, useEffect, useState} from 'react';
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
    IconButton,
    LinearProgress,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material';

import DeleteIcon from '@mui/icons-material/Delete';
import HistoryIcon from '@mui/icons-material/History';
import RestoreIcon from '@mui/icons-material/Restore';
import {
    copyAutomation,
    deleteAutomation,
    getVersions,
    rollback,
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
                p: '6px 8px', borderRadius: '8px',
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
// PANEL BUTTONS — canvas toolbar (bottom-left Panel in ActionBoardDetailComponent)
// ═════════════════════════════════════════════════════════════════════════════

export function AutomationPanelButtons({onVersion}) {
    return (
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
    );
}