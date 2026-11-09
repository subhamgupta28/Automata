import React, {memo, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    InputAdornment,
    Skeleton,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from '@mui/material';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import RestartAltRoundedIcon from '@mui/icons-material/RestartAltRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import DeveloperBoardRoundedIcon from '@mui/icons-material/DeveloperBoardRounded';
import WifiRoundedIcon from '@mui/icons-material/WifiRounded';
import WifiOffRoundedIcon from '@mui/icons-material/WifiOffRounded';
import BoltRoundedIcon from '@mui/icons-material/BoltRounded';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import {useSnackbar} from 'notistack';
import {getLastData, rebootAllDevices, updateAttribute} from '../../services/apis.jsx';
import {useCachedDevices} from '../../services/AppCacheContext.jsx';
import {useDeviceLiveData} from '../../services/DeviceDataProvider.jsx';
import {useAuth} from '../auth/AuthContext.jsx';
import CreateDeviceDialog from '../CreateDeviceDialog.jsx';
import {CustomModal} from '../home/CustomModal.jsx';
import DeviceCard, {hasMap, OFFLINE_COLOR, ONLINE_COLOR} from './DeviceCard.jsx';

dayjs.extend(relativeTime);

const HAIRLINE = 'rgb(255 255 255 / 8%)';

const StatTile = ({icon, label, value, caption, accent, wideOnMobile}) => (
    <Box
        sx={{
            gridColumn: wideOnMobile ? {xs: 'span 3', md: 'auto'} : undefined,
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            px: {xs: 1.5, sm: 2},
            py: 1.5,
            borderRadius: '12px',
            backdropFilter: 'blur(3px)',
            backgroundColor: 'rgb(255 255 255 / 5%)',
            border: `1px solid ${HAIRLINE}`,
            minWidth: 0,
        }}
    >
        <Box
            sx={{
                display: {xs: 'none', sm: 'grid'},
                placeItems: 'center',
                width: 38,
                height: 38,
                flexShrink: 0,
                borderRadius: '10px',
                color: accent ?? 'primary.main',
                backgroundColor: 'rgb(255 255 255 / 6%)',
            }}
        >
            {icon}
        </Box>
        <Box sx={{minWidth: 0}}>
            <Typography
                variant="caption"
                sx={{
                    display: 'block',
                    color: 'text.secondary',
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase',
                }}
            >
                {label}
            </Typography>
            <Typography noWrap sx={{fontWeight: 700, fontSize: '1.35rem', lineHeight: 1.15}}>
                {value}
            </Typography>
            {caption && (
                <Typography noWrap variant="caption" sx={{display: 'block', color: 'text.secondary'}}>
                    {caption}
                </Typography>
            )}
        </Box>
    </Box>
);

const CardSkeleton = () => (
    <Box sx={{borderRadius: '12px', border: `1px solid ${HAIRLINE}`, p: 2}}>
        <Skeleton variant="text" width="55%" height={28}/>
        <Skeleton variant="text" width="30%"/>
        <Box sx={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, mt: 2}}>
            <Skeleton variant="rounded" height={64}/>
            <Skeleton variant="rounded" height={64}/>
            <Skeleton variant="rounded" height={64}/>
            <Skeleton variant="rounded" height={64}/>
        </Box>
    </Box>
);

function DevicesHubComponent() {
    const {devices, loading, error} = useCachedDevices();
    const {messages} = useDeviceLiveData();
    const {isGuest} = useAuth();
    const {enqueueSnackbar} = useSnackbar();

    const [deviceList, setDeviceList] = useState([]);
    const [liveMap, setLiveMap] = useState({});
    const [ackMap, setAckMap] = useState({});
    const [lastUpdate, setLastUpdate] = useState(null);
    const [, setTick] = useState(0);

    const [query, setQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState('all');
    const [typeFilter, setTypeFilter] = useState('all');

    const [createOpen, setCreateOpen] = useState(false);
    const [rebootOpen, setRebootOpen] = useState(false);
    const [settingsDevice, setSettingsDevice] = useState(null);

    // Devices that already received a websocket update, so a slower initial fetch can't overwrite them.
    const touched = useRef(new Set());

    // Local copy so the visibility switches update immediately after saving.
    useEffect(() => {
        if (devices) setDeviceList(devices);
    }, [devices]);

    // Seed live values from the cached last data, then refresh each device once from the API.
    useEffect(() => {
        if (!devices?.length) return undefined;
        let active = true;

        setLiveMap((prev) => {
            const next = {...prev};
            devices.forEach((d) => {
                if (!next[d.id]) next[d.id] = {...d.lastData};
            });
            return next;
        });

        Promise.allSettled(devices.map((d) => getLastData(d.id).then((res) => [d.id, res]))).then((results) => {
            if (!active) return;
            const fresh = {};
            results.forEach((r) => {
                if (r.status === 'fulfilled' && r.value[1] && !touched.current.has(r.value[0])) {
                    fresh[r.value[0]] = r.value[1];
                }
            });
            if (Object.keys(fresh).length) setLiveMap((prev) => ({...prev, ...fresh}));
        });

        return () => {
            active = false;
        };
    }, [devices]);

    // Live websocket stream, same message shape the old node cards consumed.
    useEffect(() => {
        const deviceId = messages?.deviceId;
        if (!deviceId) return;
        touched.current.add(deviceId);
        if (messages.data) setLiveMap((prev) => ({...prev, [deviceId]: messages.data}));
        setAckMap((prev) => ({...prev, [deviceId]: messages.ack ?? null}));
        setLastUpdate({deviceId, at: Date.now()});
    }, [messages]);

    // Keep relative times ("2 min ago") fresh.
    useEffect(() => {
        const timer = setInterval(() => setTick((t) => t + 1), 15000);
        return () => clearInterval(timer);
    }, []);

    const handleFlagChange = useCallback(async (device, attribute, checked) => {
        try {
            await updateAttribute(device.id, attribute, checked);
            setDeviceList((prev) => prev.map((d) => (d.id === device.id ? {...d, [attribute]: checked} : d)));
        } catch (err) {
            console.error('Failed to update device', err);
            enqueueSnackbar(`Could not update ${device.name}`, {variant: 'error'});
        }
    }, [enqueueSnackbar]);

    const handleReboot = async () => {
        setRebootOpen(false);
        try {
            await rebootAllDevices();
            enqueueSnackbar('Reboot sent to all devices', {variant: 'success'});
        } catch (err) {
            console.error('Reboot all failed', err);
            enqueueSnackbar('Could not reboot devices', {variant: 'error'});
        }
    };

    const stats = useMemo(() => {
        const online = deviceList.filter((d) => d.status === 'ONLINE').length;
        return {total: deviceList.length, online, offline: deviceList.length - online};
    }, [deviceList]);

    const types = useMemo(
        () => [...new Set(deviceList.map((d) => d.type).filter(Boolean))].sort(),
        [deviceList],
    );

    const visibleDevices = useMemo(() => {
        const q = query.trim().toLowerCase();
        return deviceList.filter((d) => {
            if (statusFilter === 'online' && d.status !== 'ONLINE') return false;
            if (statusFilter === 'offline' && d.status === 'ONLINE') return false;
            if (typeFilter !== 'all' && d.type !== typeFilter) return false;
            if (!q) return true;
            return [d.name, d.host, d.type, d.macAddr, d.originNode].some((v) =>
                String(v ?? '').toLowerCase().includes(q),
            );
        });
    }, [deviceList, query, statusFilter, typeFilter]);

    const lastUpdatedDevice = lastUpdate && deviceList.find((d) => d.id === lastUpdate.deviceId);
    // CustomModal reads live data from a websocket-style message; hand it the page's current values.
    const modalMessages = useMemo(
        () => (settingsDevice ? {deviceId: settingsDevice.id, data: liveMap[settingsDevice.id]} : messages),
        [settingsDevice, liveMap, messages],
    );
    const settingsMap = settingsDevice
        ? (settingsDevice.attributes ?? []).filter((a) => (a.type ?? '').startsWith('DATA|MAP'))
        : null;

    return (
        <Box sx={{minHeight: '100%', pb: 4}}>
            {/* Header + toolbar */}
            <Box
                sx={{
                    position: 'sticky',
                    top: 0,
                    zIndex: 5,
                    px: {xs: 2, md: 3},
                    pt: {xs: 2, md: 3},
                    pb: 1.5,
                    backdropFilter: 'blur(10px)',
                    backgroundColor: 'rgb(22 22 22 / 70%)',
                    borderBottom: `1px solid ${HAIRLINE}`,
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 1.5,
                    }}
                >
                    <Box>
                        <Typography sx={{fontWeight: 700, fontSize: '1.6rem', lineHeight: 1.2}}>Devices</Typography>
                        <Typography variant="body2" sx={{color: 'text.secondary'}}>
                            Live data and details for every device in your home
                        </Typography>
                    </Box>
                    {!isGuest && (
                        <Box sx={{display: 'flex', gap: 1}}>
                            <Button
                                variant="outlined"
                                size="small"
                                startIcon={<RestartAltRoundedIcon/>}
                                onClick={() => setRebootOpen(true)}
                            >
                                Reboot all
                            </Button>
                            <Button
                                variant="contained"
                                size="small"
                                startIcon={<AddRoundedIcon/>}
                                onClick={() => setCreateOpen(true)}
                            >
                                New device
                            </Button>
                        </Box>
                    )}
                </Box>

                <Box sx={{display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, mt: 1.5}}>
                    <TextField
                        size="small"
                        placeholder="Search name, host, MAC..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        sx={{width: {xs: '100%', sm: 280}}}
                        slotProps={{
                            input: {
                                startAdornment: (
                                    <InputAdornment position="start">
                                        <SearchRoundedIcon fontSize="small"/>
                                    </InputAdornment>
                                ),
                            },
                        }}
                    />
                    <ToggleButtonGroup
                        exclusive
                        size="small"
                        value={statusFilter}
                        onChange={(_, v) => v && setStatusFilter(v)}
                        aria-label="Filter by status"
                    >
                        <ToggleButton value="all" sx={{textTransform: 'none', px: 1.5}}>All</ToggleButton>
                        <ToggleButton value="online" sx={{textTransform: 'none', px: 1.5}}>Online</ToggleButton>
                        <ToggleButton value="offline" sx={{textTransform: 'none', px: 1.5}}>Offline</ToggleButton>
                    </ToggleButtonGroup>

                    {types.length > 1 && (
                        <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 0.75}}>
                            <Chip
                                size="small"
                                label="All types"
                                clickable
                                color={typeFilter === 'all' ? 'primary' : 'default'}
                                variant={typeFilter === 'all' ? 'filled' : 'outlined'}
                                onClick={() => setTypeFilter('all')}
                            />
                            {types.map((t) => (
                                <Chip
                                    key={t}
                                    size="small"
                                    label={t}
                                    clickable
                                    color={typeFilter === t ? 'primary' : 'default'}
                                    variant={typeFilter === t ? 'filled' : 'outlined'}
                                    onClick={() => setTypeFilter(typeFilter === t ? 'all' : t)}
                                    sx={{textTransform: 'capitalize'}}
                                />
                            ))}
                        </Box>
                    )}

                </Box>
            </Box>

            <Box sx={{px: {xs: 2, md: 3}, pt: 2.5}}>
                {/* Summary */}
                <Box
                    sx={{
                        display: 'grid',
                        gap: 1.5,
                        gridTemplateColumns: {xs: 'repeat(3, minmax(0, 1fr))', md: 'repeat(4, minmax(0, 1fr))'},
                        mb: 2.5,
                    }}
                >
                    <StatTile
                        icon={<DeveloperBoardRoundedIcon/>}
                        label="Devices"
                        value={loading ? '—' : stats.total}
                    />
                    <StatTile
                        icon={<WifiRoundedIcon/>}
                        label="Online"
                        value={loading ? '—' : stats.online}
                        accent={ONLINE_COLOR}
                    />
                    <StatTile
                        icon={<WifiOffRoundedIcon/>}
                        label="Offline"
                        value={loading ? '—' : stats.offline}
                        accent={stats.offline > 0 ? OFFLINE_COLOR : undefined}
                    />
                    <StatTile
                        wideOnMobile
                        icon={<BoltRoundedIcon/>}
                        label="Last update"
                        value={lastUpdate ? dayjs(lastUpdate.at).fromNow() : 'Waiting...'}
                        caption={lastUpdatedDevice?.name ?? 'No live message yet'}
                    />
                </Box>

                {error && <Alert severity="error" sx={{mb: 2}}>{error}</Alert>}

                {/* Device grid */}
                <Box
                    sx={{
                        display: 'grid',
                        gap: 2,
                        alignItems: 'start',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 320px), 1fr))',
                    }}
                >
                    {loading && Array.from({length: 6}, (_, i) => <CardSkeleton key={i}/>)}

                    {!loading && visibleDevices.map((device) => (
                        <Box key={device.id} sx={{gridColumn: hasMap(device) ? {md: 'span 2'} : undefined, minWidth: 0}}>
                            <DeviceCard
                                device={device}
                                live={liveMap[device.id]}
                                ack={ackMap[device.id]}
                                onOpenDetails={setSettingsDevice}
                                onFlagChange={handleFlagChange}
                                readOnly={isGuest}
                            />
                        </Box>
                    ))}
                </Box>

                {!loading && visibleDevices.length === 0 && (
                    <Box sx={{textAlign: 'center', py: 8, color: 'text.secondary'}}>
                        <DeveloperBoardRoundedIcon sx={{fontSize: 48, opacity: 0.4}}/>
                        <Typography sx={{mt: 1}}>
                            {deviceList.length === 0 ? 'No devices registered yet' : 'No devices match your filters'}
                        </Typography>
                        {deviceList.length > 0 && (
                            <Button
                                size="small"
                                sx={{mt: 1}}
                                onClick={() => {
                                    setQuery('');
                                    setStatusFilter('all');
                                    setTypeFilter('all');
                                }}
                            >
                                Clear filters
                            </Button>
                        )}
                    </Box>
                )}
            </Box>

            <CreateDeviceDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => {}}/>

            {settingsDevice && (
                <CustomModal
                    map={settingsMap}
                    isOpen
                    messages={modalMessages}
                    onClose={() => setSettingsDevice(null)}
                    devices={[settingsDevice]}
                />
            )}

            <Dialog open={rebootOpen} onClose={() => setRebootOpen(false)}>
                <DialogTitle>Reboot all devices?</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Every device will restart and briefly go offline.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button color="inherit" onClick={() => setRebootOpen(false)}>Cancel</Button>
                    <Button onClick={handleReboot}>Reboot all</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

const DevicesHub = memo(DevicesHubComponent);
export default DevicesHub;
