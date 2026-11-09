import React from 'react';
import {
    Box,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import {
    ExternalLink,
    formatTime,
    formatValue,
    fromNow,
    HAIRLINE,
    InfoRow,
    LiveValue,
    OFFLINE_COLOR,
    ONLINE_COLOR,
    SectionLabel,
} from './DeviceCard.jsx';

export default function DeviceDetailsDialog({device, live, open, onClose, onFlagChange, readOnly}) {
    if (!device) return null;
    const attributes = device.attributes ?? [];
    const online = device.status === 'ONLINE';

    return (
        <Dialog
            open={open}
            onClose={onClose}
            fullWidth
            maxWidth="md"
            scroll="paper"
            slotProps={{
                paper: {
                    sx: {
                        borderRadius: '14px',
                        backgroundColor: 'rgb(22 22 22 / 88%)',
                        backgroundImage: 'none',
                        backdropFilter: 'blur(10px)',
                        border: `1px solid ${HAIRLINE}`,
                    },
                },
            }}
        >
            <DialogTitle sx={{display: 'flex', alignItems: 'center', gap: 1.25, pr: 1}}>
                <Box
                    sx={{
                        width: 9,
                        height: 9,
                        flexShrink: 0,
                        borderRadius: '50%',
                        backgroundColor: online ? ONLINE_COLOR : OFFLINE_COLOR,
                    }}
                />
                <Box sx={{flexGrow: 1, minWidth: 0}}>
                    <Typography noWrap sx={{fontWeight: 700, fontSize: '1.15rem', lineHeight: 1.25}}>
                        {device.name}
                    </Typography>
                    <Typography noWrap variant="caption" sx={{color: 'text.secondary'}}>
                        {online ? 'Online' : `Offline · last seen ${fromNow(device.lastOnline)}`}
                    </Typography>
                </Box>
                {device.type && (
                    <Chip
                        size="small"
                        variant="outlined"
                        label={device.type}
                        sx={{textTransform: 'capitalize', color: 'text.secondary'}}
                    />
                )}
                <IconButton onClick={onClose} aria-label="Close details">
                    <CloseRoundedIcon/>
                </IconButton>
            </DialogTitle>
            <Divider sx={{borderColor: HAIRLINE}}/>
            <DialogContent sx={{p: 0}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {xs: 'minmax(0, 1fr)', md: 'minmax(0, 1fr) minmax(0, 1.25fr)'},
                    }}
                >
                    <Box sx={{p: 2.5, borderRight: {md: `1px solid ${HAIRLINE}`}}}>
                        <SectionLabel sx={{mb: 0.5}}>Device info</SectionLabel>
                        <Box sx={{display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', columnGap: 2}}>
                            <InfoRow label="Host">
                                <ExternalLink href={device.host ? `http://${device.host}.local` : null}>
                                    {device.host}
                                </ExternalLink>
                            </InfoRow>
                            <InfoRow label="Access URL">
                                <ExternalLink href={device.accessUrl}>{device.accessUrl}</ExternalLink>
                            </InfoRow>
                            <InfoRow label="MAC address">{device.macAddr || '—'}</InfoRow>
                            <InfoRow label="Registered node">{device.originNode || '—'}</InfoRow>
                            <InfoRow label="Last online">
                                {formatTime(device.lastOnline)}
                                <Typography component="span" variant="caption" sx={{color: 'text.secondary', ml: 0.75}}>
                                    ({fromNow(device.lastOnline)})
                                </Typography>
                            </InfoRow>
                            <InfoRow label="Last registered">
                                {formatTime(device.lastRegistered)}
                                <Typography component="span" variant="caption" sx={{color: 'text.secondary', ml: 0.75}}>
                                    ({fromNow(device.lastRegistered)})
                                </Typography>
                            </InfoRow>
                            <InfoRow label="Update interval">
                                {device.updateInterval ? `${device.updateInterval / 1000 / 60} min` : '—'}
                            </InfoRow>
                            <InfoRow label="Type">{device.type || '—'}</InfoRow>
                        </Box>

                        {!readOnly && (
                            <>
                                <Divider sx={{my: 1.5, borderColor: HAIRLINE}}/>
                                <SectionLabel sx={{mb: 0.5}}>Visibility</SectionLabel>
                                {[
                                    ['showInDashboard', 'Show in dashboard'],
                                    ['showCharts', 'Show charts'],
                                    ['analytics', 'Analytics'],
                                ].map(([flag, label]) => (
                                    <Box
                                        key={flag}
                                        sx={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}
                                    >
                                        <Typography variant="body2">{label}</Typography>
                                        <Switch
                                            size="small"
                                            checked={Boolean(device[flag])}
                                            onChange={(e) => onFlagChange(device, flag, e.target.checked)}
                                        />
                                    </Box>
                                ))}
                            </>
                        )}
                    </Box>

                    <Box sx={{p: 2.5, minWidth: 0}}>
                        <SectionLabel sx={{mb: 0.75}}>All data points · live</SectionLabel>
                        {attributes.length === 0 ? (
                            <Typography variant="body2" sx={{color: 'text.secondary'}}>
                                This device has not reported any attributes.
                            </Typography>
                        ) : (
                            <TableContainer sx={{maxHeight: '55vh', borderRadius: '8px', border: `1px solid ${HAIRLINE}`}}>
                                <Table stickyHeader size="small">
                                    <TableHead>
                                        <TableRow>
                                            {['Name', 'Key', 'Value'].map((h) => (
                                                <TableCell
                                                    key={h}
                                                    sx={{
                                                        backgroundColor: '#161616',
                                                        color: 'text.secondary',
                                                        fontSize: '0.7rem',
                                                        fontWeight: 700,
                                                        letterSpacing: '0.06em',
                                                        textTransform: 'uppercase',
                                                        whiteSpace: 'nowrap',
                                                        borderColor: HAIRLINE,
                                                    }}
                                                >
                                                    {h}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {attributes.map((att) => {
                                            const value = live?.[att.key];
                                            return (
                                                <TableRow key={att.id ?? att.key} hover>
                                                    <TableCell sx={{borderColor: HAIRLINE}}>
                                                        {att.displayName}
                                                        <Typography
                                                            component="div"
                                                            sx={{color: 'text.secondary', fontSize: '0.65rem', lineHeight: 1.3}}
                                                        >
                                                            {att.type}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell
                                                        sx={{
                                                            borderColor: HAIRLINE,
                                                            color: 'text.secondary',
                                                            fontFamily: 'monospace',
                                                            fontSize: '0.75rem',
                                                        }}
                                                    >
                                                        {att.key}
                                                    </TableCell>
                                                    <TableCell sx={{borderColor: HAIRLINE, whiteSpace: 'nowrap'}}>
                                                        <LiveValue value={value} sx={{color: 'primary.main', fontWeight: 600}}>
                                                            {formatValue(value)}
                                                            {att.units && value !== undefined && value !== null ? ` ${att.units}` : ''}
                                                        </LiveValue>
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        )}
                    </Box>
                </Box>
            </DialogContent>
        </Dialog>
    );
}
