import React, {useCallback, useEffect, useState} from 'react';
import {
    Alert,
    Avatar,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Divider,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    List,
    ListItem,
    ListItemText,
    MenuItem,
    Modal,
    Select,
    Stack,
    Tab,
    Tabs,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material';
import {
    AlertTriangle,
    Check,
    Copy,
    Home,
    Key,
    Mail,
    Plus,
    Settings,
    Trash2,
    UserPlus,
    Users,
    Wifi,
    WifiOff,
} from 'lucide-react';
import {
    changeMemberRole,
    claimDevice,
    createHome,
    createInvite,
    deleteHome,
    getHomeInvites,
    getHomeMembers,
    getMyHomes,
    getUnclaimedDevices,
    joinHome,
    revokeAccess,
} from '../../services/apis';
import LoadingScreen from "../../utils/LoadingScreen.jsx";

const modalStyle = {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: {xs: '92vw', sm: 520},
    bgcolor: 'background.paper',
    border: '2px solid #000',
    boxShadow: 24,
    p: 4,
    maxHeight: '90vh',
    overflowY: 'auto',
};

// ─── Role badge ──────────────────────────────────────────────────────────────
function RoleBadge({role}) {
    const colorMap = {
        OWNER: 'secondary',
        ADMIN: 'primary',
        MEMBER: 'default',
        GUEST: 'default',
    };
    return <Chip label={role} size="small" color={colorMap[role] || 'default'}/>;
}

// ─── Unclaimed Devices Tab ───────────────────────────────────────────────────
function UnclaimedDevicesTab({homeId, myRole}) {
    const [devices, setDevices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [claiming, setClaiming] = useState(null); // deviceId currently being claimed

    const canClaim = ['OWNER', 'ADMIN'].includes(myRole);

    const fetchDevices = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getUnclaimedDevices(homeId);
            setDevices(data);
        } catch {
            setError('Could not load unclaimed devices.');
        } finally {
            setLoading(false);
        }
    }, [homeId]);

    useEffect(() => {
        fetchDevices();
    }, [fetchDevices]);

    const handleClaim = async (deviceId) => {
        setClaiming(deviceId);
        try {
            await claimDevice(homeId, deviceId);
            fetchDevices(); // remove it from the list once claimed
        } catch (err) {
            setError(err?.response?.data?.message || 'Failed to claim device.');
        } finally {
            setClaiming(null);
        }
    };

    if (loading) return (
        <Box sx={{display: 'flex', justifyContent: 'center', py: 4}}>
            <LoadingScreen/>
        </Box>
    );

    if (error) return (
        <Alert severity="error" sx={{mt: 2}} onClose={() => setError(null)}>{error}</Alert>
    );

    if (devices.length === 0) return (
        <Box sx={{textAlign: 'center', py: 5, color: 'text.secondary'}}>
            <WifiOff size={36} strokeWidth={1.2} style={{marginBottom: 8, opacity: 0.4}}/>
            <Typography variant="body2">No unclaimed devices found</Typography>
            <Typography variant="caption">
                Devices that connect to the network but haven't been assigned to any home appear here.
            </Typography>
        </Box>
    );

    return (
        <List disablePadding sx={{mt: 1}}>
            {devices.map((device) => (
                <ListItem key={device.id} divider>
                    <Box sx={{mr: 2, color: 'warning.main'}}>
                        <Wifi size={18}/>
                    </Box>
                    <ListItemText
                        primary={device.name || device.macAddress}
                        secondary={`${device.macAddress} · Last seen ${device.lastSeen ? new Date(device.lastSeen).toLocaleString() : 'unknown'}`}
                    />
                    {canClaim ? (
                        <Button
                            size="small"
                            variant="outlined"
                            color="warning"
                            disabled={claiming === device.id}
                            onClick={() => handleClaim(device.id)}
                            sx={{ml: 1, whiteSpace: 'nowrap'}}
                        >
                            {claiming === device.id ? <CircularProgress size={14} sx={{mr: 0.5}}/> : null}
                            {claiming === device.id ? 'Claiming…' : 'Claim'}
                        </Button>
                    ) : (
                        <Chip label="Unclaimed" size="small" color="warning" variant="outlined"/>
                    )}
                </ListItem>
            ))}
        </List>
    );
}

// ─── Invites Tab ─────────────────────────────────────────────────────────────
function InvitesTab({homeId, myRole}) {
    const [invites, setInvites] = useState([]);
    const [loading, setLoading] = useState(true);
    const [email, setEmail] = useState('');
    const [roleToGrant, setRoleToGrant] = useState('MEMBER');
    const [sending, setSending] = useState(false);
    const [copied, setCopied] = useState(null);
    const [feedback, setFeedback] = useState(null);

    const canInvite = ['OWNER', 'ADMIN'].includes(myRole);

    const fetchInvites = useCallback(async () => {
        setLoading(true);
        try {
            const data = await getHomeInvites(homeId);
            setInvites(data);
        } catch {
            /* silent */
        } finally {
            setLoading(false);
        }
    }, [homeId]);

    useEffect(() => {
        fetchInvites();
    }, [fetchInvites]);

    const handleSendInvite = async () => {
        if (!email.trim()) return;
        setSending(true);
        setFeedback(null);
        try {
            const invite = await createInvite({homeId, email: email.trim(), roleToGrant});
            setEmail('');
            setFeedback({type: 'success', msg: `Invite sent${invite.token ? ' — copy the token below' : '.'}`});
            fetchInvites();
        } catch (err) {
            setFeedback({type: 'error', msg: err?.response?.data?.message || 'Failed to send invite.'});
        } finally {
            setSending(false);
        }
    };

    const copyToken = (token) => {
        navigator.clipboard.writeText(token);
        setCopied(token);
        setTimeout(() => setCopied(null), 2000);
    };

    return (
        <Box>
            {canInvite && (
                <Box sx={{p: 2, mb: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1}}>
                    <Typography variant="body2" sx={{mb: 2, fontWeight: 600}}>
                        Send an invite
                    </Typography>
                    <Stack direction={{xs: 'column', sm: 'row'}} spacing={1.5} alignItems="flex-start">
                        <TextField
                            size="small"
                            label="Email address"
                            value={email}
                            onChange={e => setEmail(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleSendInvite()}
                            sx={{flex: 1}}
                            InputProps={{
                                startAdornment: <Mail size={14} style={{marginRight: 6, opacity: 0.5}}/>,
                            }}
                        />
                        <FormControl size="small" sx={{minWidth: 120}}>
                            <InputLabel>Role</InputLabel>
                            <Select
                                value={roleToGrant}
                                label="Role"
                                onChange={e => setRoleToGrant(e.target.value)}
                            >
                                <MenuItem value="ADMIN">Admin</MenuItem>
                                <MenuItem value="MEMBER">Member</MenuItem>
                                <MenuItem value="GUEST">Guest</MenuItem>
                            </Select>
                        </FormControl>
                        <Button
                            variant="contained"
                            onClick={handleSendInvite}
                            disabled={sending || !email.trim()}
                            startIcon={<UserPlus size={15}/>}
                            sx={{whiteSpace: 'nowrap', height: 40}}
                        >
                            {sending ? 'Sending…' : 'Send'}
                        </Button>
                    </Stack>
                    {feedback && (
                        <Alert severity={feedback.type} sx={{mt: 1.5, py: 0.5}}>
                            {feedback.msg}
                        </Alert>
                    )}
                </Box>
            )}

            <Typography variant="body2" sx={{mb: 1, fontWeight: 600, color: 'text.secondary'}}>
                Pending invites
            </Typography>

            {loading ? (
                <CircularProgress size={24}/>
            ) : invites.length === 0 ? (
                <Typography variant="caption" color="text.secondary">No pending invites.</Typography>
            ) : (
                <List disablePadding>
                    {invites.map(invite => (
                        <ListItem key={invite.id || invite.token} divider>
                            <ListItemText
                                primary={invite.invitedEmail || 'Open invite'}
                                secondary={
                                    <>
                                        Expires {invite.expiresAt ? new Date(invite.expiresAt).toLocaleDateString() : '—'}
                                        {' · '}
                                        <RoleBadge role={invite.roleToGrant}/>
                                    </>
                                }
                            />
                            {invite.token && (
                                <Tooltip title={copied === invite.token ? 'Copied!' : 'Copy token'}>
                                    <IconButton
                                        size="small"
                                        onClick={() => copyToken(invite.token)}
                                        color={copied === invite.token ? 'success' : 'default'}
                                    >
                                        {copied === invite.token ? <Check size={15}/> : <Copy size={15}/>}
                                    </IconButton>
                                </Tooltip>
                            )}
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    );
}

// ─── Members Tab ─────────────────────────────────────────────────────────────
function MembersTab({homeId, myRole, onRefreshHomes}) {
    const [members, setMembers] = useState([]);
    const [loading, setLoading] = useState(true);

    const fetchMembers = useCallback(async () => {
        setLoading(true);
        try {
            const data = await getHomeMembers(homeId);
            setMembers(data);
        } catch {
            /* silent */
        } finally {
            setLoading(false);
        }
    }, [homeId]);

    useEffect(() => {
        fetchMembers();
    }, [fetchMembers]);

    const canManage = ['OWNER', 'ADMIN'].includes(myRole);

    const handleRoleChange = async (userId, newRole) => {
        try {
            await changeMemberRole(homeId, userId, newRole);
            fetchMembers();
        } catch (err) {
            console.error('Role change failed', err);
        }
    };

    const handleRevoke = async (userId) => {
        try {
            await revokeAccess(homeId, userId);
            fetchMembers();
            onRefreshHomes?.();
        } catch (err) {
            console.error('Revoke failed', err);
        }
    };

    if (loading) return (
        <Box sx={{display: 'flex', justifyContent: 'center', py: 4}}>
            <CircularProgress size={28}/>
        </Box>
    );

    if (members.length === 0) return (
        <Typography variant="body2" color="text.secondary" sx={{py: 3, textAlign: 'center'}}>
            No members found.
        </Typography>
    );

    return (
        <List disablePadding sx={{mt: 1}}>
            {members.map((member) => {
                const isOwner = member.role === 'OWNER';
                const initials = (member.name || member.email || '?').slice(0, 2).toUpperCase();
                return (
                    <ListItem key={member.userId || member.id} divider disableGutters sx={{py: 1.5, gap: 1.5}}>
                        <Avatar sx={{width: 34, height: 34, fontSize: '0.8rem'}}>
                            {initials}
                        </Avatar>
                        <Box sx={{flex: 1, minWidth: 0}}>
                            <Typography variant="body2" fontWeight={600} noWrap>
                                {member.name || '—'}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" noWrap>
                                {member.email || member.userId}
                            </Typography>
                        </Box>

                        {canManage && !isOwner && myRole === 'OWNER' ? (
                            <FormControl size="small" sx={{minWidth: 110}}>
                                <Select
                                    value={member.role}
                                    onChange={e => handleRoleChange(member.userId || member.id, e.target.value)}
                                >
                                    <MenuItem value="ADMIN">Admin</MenuItem>
                                    <MenuItem value="MEMBER">Member</MenuItem>
                                    <MenuItem value="GUEST">Guest</MenuItem>
                                </Select>
                            </FormControl>
                        ) : (
                            <RoleBadge role={member.role}/>
                        )}

                        {canManage && !isOwner && (
                            <Tooltip title="Remove member">
                                <IconButton
                                    size="small"
                                    color="error"
                                    onClick={() => handleRevoke(member.userId || member.id)}
                                >
                                    <Trash2 size={15}/>
                                </IconButton>
                            </Tooltip>
                        )}
                    </ListItem>
                );
            })}
        </List>
    );
}

// ─── Manage Home Modal ────────────────────────────────────────────────────────
function ManageHomeModal({home, open, onClose, onHomesChange}) {
    const [tab, setTab] = useState(0);
    const [deleting, setDeleting] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);

    const myRole = home?.myRole;

    const handleDeleteHome = async () => {
        if (!confirmDelete) {
            setConfirmDelete(true);
            return;
        }
        setDeleting(true);
        try {
            await deleteHome(home.id);
            onHomesChange();
            onClose();
        } catch (err) {
            console.error('Delete failed', err);
        } finally {
            setDeleting(false);
            setConfirmDelete(false);
        }
    };

    const tabs = [
        {label: 'Members', icon: <Users size={14}/>},
        {label: 'Invites', icon: <Mail size={14}/>},
        {label: 'Devices', icon: <Wifi size={14}/>},
        ...(myRole === 'OWNER' ? [{label: 'Danger Zone', icon: <AlertTriangle size={14}/>}] : []),
    ];

    return (
        <Modal open={open} onClose={onClose}>
            <Box sx={modalStyle}>
                {/* Header */}
                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{mb: 2.5}}>
                    <Box>
                        <Typography variant="h6" component="h2">{home?.name}</Typography>
                        <Box sx={{mt: 0.5}}><RoleBadge role={myRole}/></Box>
                    </Box>
                    <IconButton onClick={onClose} size="small">
                        <span style={{fontSize: 18, lineHeight: 1}}>✕</span>
                    </IconButton>
                </Stack>

                {/* Tabs */}
                <Tabs
                    value={tab}
                    onChange={(_, v) => {
                        setTab(v);
                        setConfirmDelete(false);
                    }}
                    sx={{mb: 2, borderBottom: 1, borderColor: 'divider'}}
                >
                    {tabs.map((t, i) => (
                        <Tab
                            key={i}
                            label={
                                <Stack direction="row" spacing={0.75} alignItems="center">
                                    {t.icon}<span>{t.label}</span>
                                </Stack>
                            }
                            sx={{textTransform: 'none', minHeight: 40}}
                        />
                    ))}
                </Tabs>

                {/* Tab panels */}
                {tab === 0 && (
                    <MembersTab homeId={home?.id} myRole={myRole} onRefreshHomes={onHomesChange}/>
                )}
                {tab === 1 && (
                    <InvitesTab homeId={home?.id} myRole={myRole}/>
                )}
                {tab === 2 && (
                    <UnclaimedDevicesTab homeId={home?.id} myRole={myRole}/>
                )}
                {tab === 3 && myRole === 'OWNER' && (
                    <Box sx={{mt: 2}}>
                        <Alert severity="warning" sx={{mb: 2}}>
                            Deleting this home is permanent. All devices, automations, and member access will be
                            removed.
                        </Alert>
                        {confirmDelete && (
                            <Typography variant="body2" color="error" sx={{mb: 1.5}}>
                                Are you sure? This cannot be undone. Click again to confirm.
                            </Typography>
                        )}
                        <Button
                            variant="contained"
                            color="error"
                            onClick={handleDeleteHome}
                            disabled={deleting}
                            startIcon={<Trash2 size={15}/>}
                        >
                            {deleting ? 'Deleting…' : confirmDelete ? 'Confirm Delete' : 'Delete Home'}
                        </Button>
                    </Box>
                )}
            </Box>
        </Modal>
    );
}

// ─── Main Component ───────────────────────────────────────────────────────────
function HomeManagement() {
    const [homes, setHomes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [openCreate, setOpenCreate] = useState(false);
    const [openJoin, setOpenJoin] = useState(false);
    const [openManage, setOpenManage] = useState(false);
    const [selectedHome, setSelectedHome] = useState(null);
    const [newHomeName, setNewHomeName] = useState('');
    const [newTimezone, setNewTimezone] = useState('Asia/Kolkata');
    const [joinToken, setJoinToken] = useState('');
    const [creating, setCreating] = useState(false);
    const [joining, setJoining] = useState(false);

    const fetchHomes = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const myHomes = await getMyHomes();
            setHomes(myHomes);
        } catch {
            setError('Failed to fetch homes. Check your connection.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchHomes();
    }, [fetchHomes]);

    const handleCreateHome = async () => {
        if (!newHomeName.trim()) return;
        setCreating(true);
        try {
            await createHome({name: newHomeName.trim(), timezone: newTimezone});
            setOpenCreate(false);
            setNewHomeName('');
            fetchHomes();
        } catch (err) {
            console.error('Create home failed', err);
        } finally {
            setCreating(false);
        }
    };

    const handleJoinHome = async () => {
        if (!joinToken.trim()) return;
        setJoining(true);
        try {
            await joinHome(joinToken.trim());
            setOpenJoin(false);
            setJoinToken('');
            fetchHomes();
        } catch (err) {
            console.error('Join home failed', err);
        } finally {
            setJoining(false);
        }
    };

    const handleOpenManage = (home) => {
        setSelectedHome(home);
        setOpenManage(true);
    };
    const handleCloseManage = () => {
        setOpenManage(false);
        setSelectedHome(null);
    };

    if (loading) return (
        <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}>
            <CircularProgress/>
        </Box>
    );

    return (
        <Box sx={{p: 3}}>
            {/* Header */}
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{mb: 3}}>
                <Typography variant="h4">Home Management</Typography>
                <Stack direction="row" spacing={1}>
                    <Button variant="outlined" startIcon={<Key size={14}/>} onClick={() => setOpenJoin(true)}>
                        Join Home
                    </Button>
                    <Button variant="contained" startIcon={<Plus size={14}/>} onClick={() => setOpenCreate(true)}>
                        Create Home
                    </Button>
                </Stack>
            </Stack>

            {error && (
                <Alert severity="error" sx={{mb: 2}}
                       action={<Button size="small" onClick={fetchHomes}>Retry</Button>}>
                    {error}
                </Alert>
            )}

            {!error && homes.length === 0 && (
                <Box sx={{textAlign: 'center', py: 10, border: '1px dashed', borderColor: 'divider', borderRadius: 2}}>
                    <Home size={40} strokeWidth={1} style={{marginBottom: 12, opacity: 0.3}}/>
                    <Typography variant="body1" sx={{mb: 0.5}}>No homes yet</Typography>
                    <Typography variant="caption" color="text.secondary">
                        Create a home to start managing your devices.
                    </Typography>
                </Box>
            )}

            {/* Home cards */}
            <Grid container spacing={3}>
                {homes.map((home) => (
                    <Grid item xs={12} sm={6} md={4} key={home.id}>
                        <Card>
                            <CardContent>
                                <Stack direction="row" alignItems="flex-start" justifyContent="space-between"
                                       sx={{mb: 1.5}}>
                                    <Typography variant="h6">{home.name}</Typography>
                                    <RoleBadge role={home.myRole}/>
                                </Stack>
                                <Typography variant="caption" color="text.secondary">
                                    {home.timezone || 'UTC'}
                                </Typography>
                                <Divider sx={{my: 1.5}}/>
                                <Button
                                    fullWidth
                                    size="small"
                                    variant="outlined"
                                    startIcon={<Settings size={13}/>}
                                    onClick={() => handleOpenManage(home)}
                                >
                                    Manage
                                </Button>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            {/* Create Home Modal */}
            <Modal open={openCreate} onClose={() => setOpenCreate(false)}>
                <Box sx={modalStyle}>
                    <Typography variant="h6" component="h2" sx={{mb: 2}}>
                        Create a new home
                    </Typography>
                    <Stack spacing={2}>
                        <TextField
                            autoFocus
                            label="Home name"
                            fullWidth
                            variant="standard"
                            value={newHomeName}
                            onChange={e => setNewHomeName(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleCreateHome()}
                        />
                        <TextField
                            label="Timezone"
                            fullWidth
                            variant="standard"
                            value={newTimezone}
                            onChange={e => setNewTimezone(e.target.value)}
                            helperText="e.g. Asia/Kolkata, UTC, America/New_York"
                        />
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button onClick={() => setOpenCreate(false)}>Cancel</Button>
                            <Button
                                variant="contained"
                                onClick={handleCreateHome}
                                disabled={creating || !newHomeName.trim()}
                            >
                                {creating ? 'Creating…' : 'Create'}
                            </Button>
                        </Stack>
                    </Stack>
                </Box>
            </Modal>

            {/* Join Home Modal */}
            <Modal open={openJoin} onClose={() => setOpenJoin(false)}>
                <Box sx={modalStyle}>
                    <Typography variant="h6" component="h2" sx={{mb: 0.5}}>
                        Join a home
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{mb: 2}}>
                        Enter the invite token shared by your home admin.
                    </Typography>
                    <Stack spacing={2}>
                        <TextField
                            autoFocus
                            label="Invite token"
                            fullWidth
                            variant="standard"
                            value={joinToken}
                            onChange={e => setJoinToken(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleJoinHome()}
                        />
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button onClick={() => setOpenJoin(false)}>Cancel</Button>
                            <Button
                                variant="contained"
                                onClick={handleJoinHome}
                                disabled={joining || !joinToken.trim()}
                            >
                                {joining ? 'Joining…' : 'Join'}
                            </Button>
                        </Stack>
                    </Stack>
                </Box>
            </Modal>

            {/* Manage Modal */}
            {selectedHome && (
                <ManageHomeModal
                    home={selectedHome}
                    open={openManage}
                    onClose={handleCloseManage}
                    onHomesChange={fetchHomes}
                />
            )}
        </Box>
    );
}

export default HomeManagement;