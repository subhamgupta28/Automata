import React, {useMemo, useState} from 'react';
import {Box, Chip, Collapse, Divider, Paper, Stack, Typography,} from '@mui/material';
import {ExpandLess, ExpandMore, GpsFixed, GpsOff, MapOutlined, Satellite, Speed,} from '@mui/icons-material';
import {extractGpsRoute, findGpsDeviceId} from "./Helper.jsx";
import {MapView} from "../components/charts/MapView.jsx";


// ── helpers ───────────────────────────────────────────────────────────────────

const fmtCoord = (n) => (n != null ? Number(n).toFixed(6) : '—');
const fmtVal = (v, unit = '') => (v != null ? `${v}${unit ? ' ' + unit : ''}` : '—');

/**
 * Derives route stats from the raw route array + all readings.
 * - Total points, start/end coords, bounding box center for initial map view
 * - Last known speed, altitude, satellite count from the final reading
 */
const deriveStats = (route, buckets, gpsDeviceId) => {
    if (!route.length) return null;

    // Collect all GPS readings in order
    const allReadings = buckets
        .filter((b) => !gpsDeviceId || b.deviceId === gpsDeviceId)
        .flatMap((b) => b.readings ?? []);

    const last = allReadings.at(-1) ?? {};

    const lats = route.map((p) => p[0]);
    const lngs = route.map((p) => p[1]);
    const centerLat = (Math.min(...lats) + Math.max(...lats)) / 2;
    const centerLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;

    // Approximate total distance via Haversine between consecutive points
    let distKm = 0;
    for (let i = 1; i < route.length; i++) {
        distKm += haversineKm(route[i - 1], route[i]);
    }

    return {
        points: route.length,
        start: route[0],
        end: route.at(-1),
        centerLat,
        centerLng,
        distKm: distKm.toFixed(2),
        speed: last.SPEED ?? last.speed,
        altitude: last.ALT ?? last.altitude,
        satellites: last.SATS ?? last.satellites,
        fix: last.FIX ?? last.fix,
    };
};

const haversineKm = ([lat1, lon1], [lat2, lon2]) => {
    const R = 6371;
    const dL = ((lat2 - lat1) * Math.PI) / 180;
    const dl = ((lon2 - lon1) * Math.PI) / 180;
    const a =
        Math.sin(dL / 2) ** 2 +
        Math.cos((lat1 * Math.PI) / 180) *
        Math.cos((lat2 * Math.PI) / 180) *
        Math.sin(dl / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

// ── StatPill ──────────────────────────────────────────────────────────────────
const StatPill = ({icon, label, value}) => (
    <Paper variant="outlined" sx={{px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1, minWidth: 100}}>
        <Box color="text.secondary" display="flex">{icon}</Box>
        <Box>
            <Typography variant="caption" color="text.secondary" display="block" lineHeight={1.2}>
                {label}
            </Typography>
            <Typography variant="body2" fontWeight={600} lineHeight={1.3}>
                {value}
            </Typography>
        </Box>
    </Paper>
);

// ── GpsRoutePanel ─────────────────────────────────────────────────────────────
/**
 * Props
 * ─────
 * session   — full session object with session.buckets[] already populated
 * devices   — full devices array from useCachedDevices()
 */
const GpsRoutePanel = ({session, devices}) => {
    const [expanded, setExpanded] = useState(true);

    const gpsDeviceId = useMemo(
        () => findGpsDeviceId(session?.deviceIds, devices),
        [session?.deviceIds, devices],
    );

    // Filter buckets to GPS device only (or all if not found)
    const gpsBuckets = useMemo(() => {
        if (!session?.buckets?.length) return [];
        return gpsDeviceId
            ? session.buckets.filter((b) => b.deviceId === gpsDeviceId)
            : session.buckets;
    }, [session?.buckets, gpsDeviceId]);

    const route = useMemo(() => extractGpsRoute(gpsBuckets), [gpsBuckets]);
    const stats = useMemo(() => deriveStats(route, gpsBuckets, gpsDeviceId), [route, gpsBuckets, gpsDeviceId]);

    // ── No GPS data ────────────────────────────────────────────────────────
    if (!gpsDeviceId && !route.length) return null;

    if (!route.length) {
        return (
            <Paper variant="outlined" sx={{p: 2, mt: 2}}>
                <Stack direction="row" alignItems="center" spacing={1} color="text.secondary">
                    <GpsOff fontSize="small"/>
                    <Typography variant="body2">
                        No valid GPS fix found in this recording.
                        Check that the GPS device had a fix (FIX ≠ 0) during the session.
                    </Typography>
                </Stack>
            </Paper>
        );
    }

    const [startLat, startLng] = route[0];

    return (
        <Paper variant="outlined" sx={{mt: 2, overflow: 'hidden'}}>
            {/* ── Header ────────────────────────────────────────────────────── */}
            <Stack
                direction="row" alignItems="center" justifyContent="space-between"
                px={2} py={1.5} sx={{cursor: 'pointer'}}
                onClick={() => setExpanded((p) => !p)}
            >
                <Stack direction="row" alignItems="center" spacing={1}>
                    <MapOutlined sx={{fontSize: 18, color: '#1D9E75'}}/>
                    <Typography variant="subtitle2" fontWeight={600}>GPS Route Replay</Typography>
                    <Chip
                        size="small"
                        label={`${route.length} pts · ${stats?.distKm} km`}
                        sx={{fontSize: 11, height: 20}}
                    />
                </Stack>
                {expanded ? <ExpandLess fontSize="small"/> : <ExpandMore fontSize="small"/>}
            </Stack>

            <Collapse in={expanded}>
                <Divider/>

                {/* ── Stat pills ─────────────────────────────────────────────── */}
                <Stack direction="row" flexWrap="wrap" gap={1} px={2} py={1.5}>
                    <StatPill
                        icon={<Speed sx={{fontSize: 16}}/>}
                        label="Last speed"
                        value={fmtVal(stats?.speed, 'km/h')}
                    />
                    <StatPill
                        icon={<GpsFixed sx={{fontSize: 16}}/>}
                        label="Altitude"
                        value={fmtVal(stats?.altitude, 'm')}
                    />
                    <StatPill
                        icon={<Satellite sx={{fontSize: 16}}/>}
                        label="Satellites"
                        value={fmtVal(stats?.satellites)}
                    />
                    <StatPill
                        icon={<GpsFixed sx={{fontSize: 16}}/>}
                        label="Start"
                        value={`${fmtCoord(stats?.start?.[0])}, ${fmtCoord(stats?.start?.[1])}`}
                    />
                    <StatPill
                        icon={<GpsFixed sx={{fontSize: 16}}/>}
                        label="End"
                        value={`${fmtCoord(stats?.end?.[0])}, ${fmtCoord(stats?.end?.[1])}`}
                    />
                </Stack>

                <Divider/>

                {/* ── Map ───────────────────────────────────────────────────── */}
                <Box sx={{px: 0, pt: 0}}>
                    <MapView
                        lat={startLat}
                        lng={startLng}
                        h="400px"
                        w="100%"
                        route={route}
                    />
                </Box>

                {/* ── Footer: start → end coords ────────────────────────────── */}
                <Stack
                    direction="row" justifyContent="space-between" flexWrap="wrap"
                    px={2} py={1} sx={{bgcolor: 'action.hover'}}
                >
                    <Typography variant="caption" color="text.secondary">
                        <strong>Start:</strong> {fmtCoord(stats?.start?.[0])}, {fmtCoord(stats?.start?.[1])}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        <strong>End:</strong> {fmtCoord(stats?.end?.[0])}, {fmtCoord(stats?.end?.[1])}
                    </Typography>
                </Stack>
            </Collapse>
        </Paper>
    );
};

export default GpsRoutePanel;