import React, {memo, useEffect, useMemo, useRef, useState} from 'react';
import {
    Box,
    Button,
    Chip,
    Divider,
    IconButton,
    LinearProgress,
    Link,
    Popover,
    Switch,
    Tooltip,
    Typography,
} from '@mui/material';
import {keyframes} from '@mui/material/styles';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import OpenInFullRoundedIcon from '@mui/icons-material/OpenInFullRounded';
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import {sendAction} from '../../services/apis.jsx';
import {GaugeChart} from '../charts/GaugeChart.jsx';
import {CustomSlider} from '../charts/CustomSlider.jsx';
import {MapView} from '../charts/MapView.jsx';
import {SwitchButton} from '../charts/SwitchButton.jsx';
import {Presets} from '../charts/Presets.jsx';
import PersonTracker from '../charts/PersonTracker.jsx';
import '../../App.css';

dayjs.extend(relativeTime);

export const ONLINE_COLOR = '#84fd49';
export const OFFLINE_COLOR = '#ff0000';

const GLASS_HEADER = 'rgb(255 255 255 / 10%)';
const GLASS_BODY = 'rgb(0 0 0 / 5%)';
export const HAIRLINE = 'rgb(255 255 255 / 8%)';

const pulse = keyframes`
    0% { box-shadow: 0 0 0 0 rgb(132 253 73 / 55%); }
    70% { box-shadow: 0 0 0 7px rgb(132 253 73 / 0%); }
    100% { box-shadow: 0 0 0 0 rgb(132 253 73 / 0%); }
`;

export const formatValue = (value) => {
    if (value === undefined || value === null || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'On' : 'Off';
    if (typeof value === 'number') return Number.isInteger(value) ? String(value) : String(Math.round(value * 100) / 100);
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
};

export const formatTime = (ts) => (ts ? dayjs(ts).format('MMM D, YYYY h:mm A') : '—');
export const fromNow = (ts) => (ts ? dayjs(ts).fromNow() : 'never');

// Same hover glow the old node cards had.
const useCardGlowEffect = (cardRef) => {
    useEffect(() => {
        const card = cardRef.current;
        if (!card) return undefined;

        const handleMouseMove = (e) => {
            const rect = card.getBoundingClientRect();
            const mouseX = e.clientX - rect.left - rect.width / 2;
            const mouseY = e.clientY - rect.top - rect.height / 2;
            const angle = (Math.atan2(mouseY, mouseX) * (180 / Math.PI) + 360) % 360;
            card.style.setProperty('--start', angle + 60);
        };

        card.addEventListener('mousemove', handleMouseMove);
        return () => card.removeEventListener('mousemove', handleMouseMove);
    }, [cardRef]);
};

// Briefly highlights a value when it changes so live updates are noticeable.
export const LiveValue = ({value, children, sx}) => {
    const previous = useRef(value);
    const [flash, setFlash] = useState(false);

    useEffect(() => {
        if (previous.current === value) return undefined;
        previous.current = value;
        setFlash(true);
        const timer = setTimeout(() => setFlash(false), 700);
        return () => clearTimeout(timer);
    }, [value]);

    return (
        <Box
            component="span"
            sx={{
                transition: 'text-shadow 0.4s ease, opacity 0.4s ease',
                textShadow: flash ? '0 0 12px rgb(248 230 151 / 90%)' : 'none',
                ...sx,
            }}
        >
            {children}
        </Box>
    );
};

export const SectionLabel = ({children, sx}) => (
    <Typography
        variant="caption"
        sx={{
            display: 'block',
            color: 'text.secondary',
            fontWeight: 700,
            fontSize: '0.65rem',
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            ...sx,
        }}
    >
        {children}
    </Typography>
);

export const InfoRow = ({label, children}) => (
    <Box sx={{display: 'flex', flexDirection: 'column', minWidth: 0, py: 0.75}}>
        <Typography variant="caption" sx={{color: 'text.secondary', fontSize: '0.7rem'}}>
            {label}
        </Typography>
        <Typography variant="body2" component="div" sx={{wordBreak: 'break-word', color: 'text.primary'}}>
            {children}
        </Typography>
    </Box>
);

export const ExternalLink = ({href, children}) =>
    href ? (
        <Link
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            underline="hover"
            sx={{color: 'primary.main', display: 'inline-flex', alignItems: 'center', gap: 0.5}}
        >
            {children}
            <OpenInNewRoundedIcon sx={{fontSize: 13}}/>
        </Link>
    ) : (
        '—'
    );

function groupAttributes(attributes = []) {
    const grouped = {
        gaugeData: [],
        sliderData: [],
        mapData: [],
        switchButtons: [],
        presetButtons: [],
        mainData: [],
        actionOutButtons: [],
        radarData: [],
    };

    for (const attr of attributes) {
        const type = attr.type ?? '';
        if (type === 'DATA|GAUGE') grouped.gaugeData.push(attr);
        else if (type === 'ACTION|SLIDER') grouped.sliderData.push(attr);
        else if (type.startsWith('DATA|MAP')) grouped.mapData.push(attr);
        else if (type.startsWith('ACTION|SWITCH')) grouped.switchButtons.push(attr);
        else if (type.startsWith('ACTION|PRESET')) grouped.presetButtons.push(attr);
        else if (type === 'DATA|MAIN') grouped.mainData.push(attr);
        else if (type === 'ACTION|OUT') grouped.actionOutButtons.push(attr);
        else if (type === 'DATA|RADAR') grouped.radarData.push(attr);
    }
    return grouped;
}

export const hasMap = (device) => (device.attributes ?? []).some((a) => (a.type ?? '').startsWith('DATA|MAP'));

function DeviceCardComponent({device, live, ack, onOpenDetails, onFlagChange, readOnly}) {
    const [optionsAnchor, setOptionsAnchor] = useState(null);
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef);

    const attributes = device.attributes ?? [];
    const grouped = useMemo(() => groupAttributes(attributes), [attributes]);
    const {gaugeData, sliderData, mapData, switchButtons, presetButtons, mainData, actionOutButtons, radarData} = grouped;

    const online = device.status === 'ONLINE';
    const statusColor = online ? ONLINE_COLOR : OFFLINE_COLOR;
    const hasLiveWidgets =
        mapData.length + gaugeData.length + radarData.length + sliderData.length +
        presetButtons.length + mainData.length + switchButtons.length + actionOutButtons.length > 0;

    const handleAction = async (attribute) => {
        try {
            const act = attribute.key;
            await sendAction(device.id, {key: act, [act]: 200, device_id: device.id}, device.type);
        } catch (err) {
            console.error('Action send failed', err);
        }
    };

    return (
        <Box className="card-glow-container" ref={cardRef} sx={{minWidth: 0}}>
            <div className="card-glow"/>
            <Box
                sx={{
                    position: 'relative',
                    borderRadius: '12px',
                    overflow: 'hidden',
                    backdropFilter: 'blur(3px)',
                    backgroundColor: GLASS_BODY,
                    border: `1px solid ${HAIRLINE}`,
                }}
            >
                {/* Header */}
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.25,
                        pl: 2,
                        pr: 0.75,
                        py: 0.75,
                        backgroundColor: GLASS_HEADER,
                    }}
                >
                    <Tooltip title={online ? 'Online' : 'Offline'}>
                        <Box
                            sx={{
                                width: 9,
                                height: 9,
                                flexShrink: 0,
                                borderRadius: '50%',
                                backgroundColor: statusColor,
                                animation: online ? `${pulse} 2.4s ease-out infinite` : 'none',
                            }}
                        />
                    </Tooltip>
                    <Box sx={{minWidth: 0, flexGrow: 1}}>
                        <Typography noWrap sx={{fontWeight: 700, fontSize: '1.05rem', lineHeight: 1.25}}>
                            {device.name}
                        </Typography>
                        <Typography noWrap variant="caption" sx={{color: 'text.secondary'}}>
                            {online ? 'Live now' : `Last seen ${fromNow(device.lastOnline)}`}
                        </Typography>
                    </Box>
                    {device.type && (
                        <Chip
                            size="small"
                            variant="outlined"
                            label={device.type}
                            sx={{height: 22, fontSize: '0.7rem', textTransform: 'capitalize', color: 'text.secondary'}}
                        />
                    )}
                    {!readOnly && (
                        <>
                            <Tooltip title="Card options">
                                <IconButton
                                    size="small"
                                    onClick={(e) => setOptionsAnchor(e.currentTarget)}
                                    aria-label="Card options"
                                >
                                    <SettingsRoundedIcon fontSize="small"/>
                                </IconButton>
                            </Tooltip>
                            <Popover
                                open={Boolean(optionsAnchor)}
                                anchorEl={optionsAnchor}
                                onClose={() => setOptionsAnchor(null)}
                                anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}
                                transformOrigin={{vertical: 'top', horizontal: 'right'}}
                                slotProps={{paper: {sx: {p: 1.5, minWidth: 220, backdropFilter: 'blur(10px)', backgroundColor: 'rgb(22 22 22 / 88%)', backgroundImage: 'none', border: `1px solid ${HAIRLINE}`}}}}
                            >
                                <SectionLabel sx={{mb: 0.5}}>Visibility</SectionLabel>
                                {[
                                    ['showInDashboard', 'Show in dashboard'],
                                    ['showCharts', 'Show charts'],
                                    ['analytics', 'Analytics'],
                                ].map(([flag, label]) => (
                                    <Box key={flag} sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                                        <Typography variant="body2">{label}</Typography>
                                        <Switch
                                            size="small"
                                            checked={Boolean(device[flag])}
                                            onChange={(e) => onFlagChange(device, flag, e.target.checked)}
                                        />
                                    </Box>
                                ))}
                            </Popover>
                        </>
                    )}
                </Box>

                {/* Live data points */}
                <Box sx={{p: 1.5, opacity: online ? 1 : 0.6, transition: 'opacity 0.3s ease'}}>
                    {ack?.command === 'reboot' && (
                        <Box sx={{mb: 1, p: 1, borderRadius: '8px', border: `1px solid ${HAIRLINE}`}}>
                            <Typography variant="body2">Rebooting...</Typography>
                            <LinearProgress sx={{mt: 0.5}}/>
                        </Box>
                    )}

                    {!hasLiveWidgets && (
                        <Typography variant="body2" sx={{color: 'text.secondary', py: 1, textAlign: 'center'}}>
                            No live data points configured
                        </Typography>
                    )}

                    {mapData.length > 0 && live && (
                        <Box sx={{mb: 1, borderRadius: '10px', overflow: 'hidden'}}>
                            <MapView lat={live.LAT} lng={live.LONG} h="280px" w="100%"/>
                        </Box>
                    )}

                    {gaugeData.length > 0 && (
                        <Box
                            sx={{
                                display: 'grid',
                                gap: 1,
                                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                                mb: 1,
                            }}
                        >
                            {gaugeData.map((g) => (
                                <GaugeChart
                                    key={g.key}
                                    value={live?.[g.key]}
                                    maxValue={g.extras?.max}
                                    displayName={g.displayName}
                                />
                            ))}
                        </Box>
                    )}

                    {radarData.length > 0 && (
                        <Box sx={{display: 'flex', justifyContent: 'center', mb: 1}}>
                            <PersonTracker liveData={live} radarData={radarData}/>
                        </Box>
                    )}

                    {sliderData.map((s) => (
                        <Box key={s.key} sx={{mb: 0.5}}>
                            <SectionLabel sx={{mb: 0.25}}>{s.displayName}</SectionLabel>
                            <CustomSlider
                                value={live?.[s.key]}
                                deviceId={device.id}
                                type={device.type}
                                data={s}
                                displayName={s.displayName}
                            />
                        </Box>
                    ))}

                    {presetButtons.map((p) => (
                        <Presets
                            key={p.key}
                            value={live}
                            deviceId={device.id}
                            type={device.type}
                            data={p}
                            displayName={p.displayName}
                        />
                    ))}

                    {(mainData.length > 0 || switchButtons.length > 0 || actionOutButtons.length > 0) && (
                        <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 1, mt: 1}}>
                            {mainData.map((m, index) => (
                                <Box
                                    key={m.id ?? m.key}
                                    sx={{
                                        // a lone last tile fills the row instead of leaving a gap
                                        gridColumn:
                                            index === mainData.length - 1 &&
                                            mainData.length % 2 === 1 &&
                                            switchButtons.length === 0 &&
                                            actionOutButtons.length === 0
                                                ? 'span 2'
                                                : undefined,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        px: 1,
                                        py: 1.25,
                                        borderRadius: '10px',
                                        backgroundColor: 'rgb(255 255 255 / 4%)',
                                        border: `1px solid ${HAIRLINE}`,
                                        minWidth: 0,
                                    }}
                                >
                                    <LiveValue
                                        value={live?.[m.key]}
                                        sx={{color: 'primary.main', fontWeight: 700, fontSize: '1.2rem', lineHeight: 1.2}}
                                    >
                                        {formatValue(live?.[m.key])}
                                        {m.units && live?.[m.key] !== undefined && live?.[m.key] !== null ? ` ${m.units}` : ''}
                                    </LiveValue>
                                    <Typography variant="caption" noWrap sx={{color: 'text.secondary', maxWidth: '100%'}}>
                                        {m.displayName}
                                    </Typography>
                                </Box>
                            ))}

                            {switchButtons.length > 0 && (
                                <Box
                                    sx={{
                                        gridColumn: 'span 2',
                                        display: 'flex',
                                        flexWrap: 'wrap',
                                        justifyContent: 'space-around',
                                        alignItems: 'center',
                                        borderRadius: '10px',
                                        border: `1px solid ${HAIRLINE}`,
                                        py: 0.5,
                                    }}
                                >
                                    {switchButtons.map((s) => (
                                        <SwitchButton
                                            key={s.key}
                                            value={live?.[s.key]}
                                            deviceId={device.id}
                                            data={s}
                                            type={device.type}
                                            displayName={s.displayName}
                                        />
                                    ))}
                                </Box>
                            )}

                            {actionOutButtons.length > 0 && (
                                <Box
                                    sx={{
                                        gridColumn: 'span 2',
                                        display: 'flex',
                                        flexWrap: 'wrap',
                                        justifyContent: 'space-around',
                                        alignItems: 'center',
                                        borderRadius: '10px',
                                        border: `1px solid ${HAIRLINE}`,
                                        py: 0.5,
                                    }}
                                >
                                    {actionOutButtons.map((a) => (
                                        <Button key={a.id ?? a.key} onClick={() => handleAction(a)}>
                                            {a.displayName}
                                        </Button>
                                    ))}
                                </Box>
                            )}
                        </Box>
                    )}
                </Box>

                {/* Details popup trigger */}
                <Divider sx={{borderColor: HAIRLINE}}/>
                <Button
                    fullWidth
                    size="small"
                    color="inherit"
                    onClick={() => onOpenDetails(device)}
                    aria-haspopup="dialog"
                    endIcon={<OpenInFullRoundedIcon sx={{fontSize: '15px !important'}}/>}
                    sx={{
                        borderRadius: 0,
                        py: 0.75,
                        color: 'text.secondary',
                        textTransform: 'none',
                        fontSize: '0.8rem',
                    }}
                >
                    {`Details · ${attributes.length} data point${attributes.length === 1 ? '' : 's'}`}
                </Button>
            </Box>
        </Box>
    );
}

const DeviceCard = memo(DeviceCardComponent);
export default DeviceCard;
