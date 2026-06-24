import {MapContainer, Marker, Polyline, Popup, TileLayer, useMap} from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import React, {useEffect, useMemo} from "react";
import './MapView.css'

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

const customIcon = (color, size = 14) => L.divIcon({
    className: '',
    html: `<div style="
        width: ${size}px; height: ${size}px;
        background: ${color};
        border: 2px solid #fff;
        border-radius: 50%;
        box-shadow: 0 0 6px rgba(0,0,0,0.6);
    "></div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
});

// Speed bucketed color, mirrors a typical "slow -> fast" traffic-light scale.
// Tweak thresholds (km/h) to taste.
const speedColor = (speed) => {
    if (speed == null) return '#94a3b8'; // slate - unknown
    if (speed < 5) return '#60a5fa';     // blue - idle / very slow
    if (speed < 30) return '#4ade80';    // green - normal
    if (speed < 60) return '#facc15';    // yellow - brisk
    return '#f87171';                    // red - fast
};

// Routes with more points than this get Douglas-Peucker simplified before
// rendering — keeps thousands of near-duplicate GPS pings from turning into
// thousands of Leaflet layers. Routes at or below this size render exactly
// as recorded (no geometry loss).
const SIMPLIFY_POINT_THRESHOLD = 1000;
// Perpendicular-distance tolerance for simplification, in degrees of
// lat/lng (~0.00005 deg ≈ 5m at the equator). Larger = more aggressive
// simplification, smaller path fidelity loss tolerance.
const SIMPLIFY_TOLERANCE_DEG = 0.00005;

// Perpendicular distance from point `p` to the line through `a`-`b`.
const perpendicularDistance = (p, a, b) => {
    const [px, py] = p;
    const [ax, ay] = a;
    const [bx, by] = b;
    const dx = bx - ax;
    const dy = by - ay;
    const lenSq = dx * dx + dy * dy;
    if (lenSq === 0) {
        const ddx = px - ax;
        const ddy = py - ay;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }
    const t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
    const projX = ax + t * dx;
    const projY = ay + t * dy;
    const ddx = px - projX;
    const ddy = py - projY;
    return Math.sqrt(ddx * ddx + ddy * ddy);
};

/**
 * Classic Douglas-Peucker line simplification, adapted to also carry along
 * a parallel `speeds` array so each surviving point keeps its original
 * speed value (needed for correct gradient coloring after simplification).
 * Operates on indices internally so route/speeds stay aligned throughout.
 */
const douglasPeucker = (route, speeds, tolerance) => {
    if (route.length <= 2) {
        return {route, speeds};
    }

    const keepIndices = new Set([0, route.length - 1]);

    const simplifySection = (startIdx, endIdx) => {
        if (endIdx - startIdx < 2) return;

        let maxDist = -1;
        let maxIdx = -1;
        const a = route[startIdx];
        const b = route[endIdx];

        for (let i = startIdx + 1; i < endIdx; i++) {
            const dist = perpendicularDistance(route[i], a, b);
            if (dist > maxDist) {
                maxDist = dist;
                maxIdx = i;
            }
        }

        if (maxDist > tolerance) {
            keepIndices.add(maxIdx);
            simplifySection(startIdx, maxIdx);
            simplifySection(maxIdx, endIdx);
        }
    };

    simplifySection(0, route.length - 1);

    const sortedIndices = Array.from(keepIndices).sort((a, b) => a - b);
    return {
        route: sortedIndices.map((i) => route[i]),
        speeds: speeds ? sortedIndices.map((i) => speeds[i]) : null,
    };
};

/**
 * Merges consecutive points that fall into the same speed color bucket into
 * a single multi-point Polyline, instead of one Polyline per point-pair.
 * This is lossless for the visible gradient (color resolution is identical)
 * but cuts the number of rendered Leaflet layers down to one per color run
 * rather than one per point — typically a 10-50x reduction on real routes
 * with sustained speed stretches.
 *
 * Returns an array of { positions, color } segments ready to render.
 */
const buildColorRunSegments = (route, routeSpeeds) => {
    if (route.length < 2) return [];

    const segments = [];
    let runStart = 0;
    let runColor = routeSpeeds ? speedColor(routeSpeeds[0]) : '#4ade80';

    for (let i = 1; i < route.length; i++) {
        const color = routeSpeeds ? speedColor(routeSpeeds[i]) : '#4ade80';
        if (color !== runColor) {
            // Close the current run at this point (shared vertex keeps the
            // line visually continuous between differently-colored runs).
            segments.push({positions: route.slice(runStart, i + 1), color: runColor});
            runStart = i;
            runColor = color;
        }
    }
    segments.push({positions: route.slice(runStart), color: runColor});

    return segments;
};

const FitBounds = ({route}) => {
    const map = useMap();
    useEffect(() => {
        if (route && route.length > 1) {
            map.fitBounds(route, {padding: [40, 40]});
        }
    }, [route, map]);
    return null;
};
const RecenterMap = ({position}) => {
    const map = useMap();

    useEffect(() => {
        map.setView(position, map.getZoom(), {
            animate: true,
        });
    }, [position, map]);

    return null;
};

/**
 * Renders the route as a speed-colored gradient, balancing visual fidelity
 * against render cost for large routes:
 *  1. If the route has more than SIMPLIFY_POINT_THRESHOLD points, it's
 *     Douglas-Peucker simplified first (geometry only, speeds carried along
 *     index-aligned) — collapses near-duplicate GPS pings on long/dense
 *     routes without visibly changing the path shape.
 *  2. Consecutive points sharing the same speed color bucket are merged
 *     into a single multi-point Polyline rather than one per point-pair —
 *     this is lossless for the gradient (same visual colors, same
 *     transitions) but renders far fewer Leaflet layers.
 *
 * routeSpeeds must be the same length and order as route (1 speed per
 * coordinate) before simplification. Falls back to solid green if omitted.
 */
const GradientRoute = ({route, routeSpeeds}) => {
    const segments = useMemo(() => {
        if (!route || route.length < 2) return [];

        const {route: simplifiedRoute, speeds: simplifiedSpeeds} =
            route.length > SIMPLIFY_POINT_THRESHOLD
                ? douglasPeucker(route, routeSpeeds, SIMPLIFY_TOLERANCE_DEG)
                : {route, speeds: routeSpeeds};

        return buildColorRunSegments(simplifiedRoute, simplifiedSpeeds);
    }, [route, routeSpeeds]);

    return (
        <>
            {segments.map((seg, i) => (
                <Polyline
                    key={`seg-${i}`}
                    positions={seg.positions}
                    pathOptions={{color: seg.color, weight: 4, opacity: 0.85}}
                />
            ))}
        </>
    );
};

/**
 * points (optional): richer per-coordinate metadata for intermediate markers, e.g.
 *   [{ lat, lng, speed, satellites, fix, timestamp }, ...]
 * Rendered as small color-coded dots (by speed) with a popup showing the metrics.
 * Pass a subset (e.g. every Nth point) from the caller to avoid marker clutter —
 * MapView does not down-sample points itself.
 */
export const MapView = React.memo(({lat, lng, h, w, route, points, routeSpeeds, centerMap = false}) => {
    const position = [lat || 0, lng || 0];
    const hasRoute = route && route.length > 1;
    const hasPoints = points && points.length > 0;

    return (
        <MapContainer
            center={position}
            zoom={16}
            preferCanvas={true}
            style={{
                height: h, width: w, borderRadius: '8px',
                // boxShadow: 'rgb(30 30 30) 0px 0px 6px 10px',
                overflow: 'hidden',
                background: 'transparent',
                border: 'none'
            }}
            className="nodrag"
        >
            {centerMap && <RecenterMap position={position}/>}

            <TileLayer
                url="https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                maxZoom={20}
                rotationAngle={60}
                subdomains={['mt0', 'mt1', 'mt2', 'mt3']}
                className="dark-tiles"
            />

            {hasRoute ? (
                <>
                    {centerMap && <FitBounds route={route}/>}
                    <GradientRoute route={route} routeSpeeds={routeSpeeds}/>
                    {/*/!* Intermediate metric markers (speed / satellites / fix), color-coded by speed *!/*/}
                    {/*{hasPoints && points.map((p, i) => (*/}
                    {/*    <Marker*/}
                    {/*        key={`pt-${i}`}*/}
                    {/*        position={[p.lat, p.lng]}*/}
                    {/*        icon={customIcon(speedColor(p.speed), 10)}*/}
                    {/*    >*/}
                    {/*        <Popup>*/}
                    {/*            <div style={{fontSize: 12, lineHeight: 1.5}}>*/}
                    {/*                {p.speed != null && <div><strong>Speed:</strong> {p.speed} km/h</div>}*/}
                    {/*                {p.satellites != null && <div><strong>Satellites:</strong> {p.satellites}</div>}*/}
                    {/*                {p.fix != null && <div><strong>Fix:</strong> {p.fix}</div>}*/}
                    {/*                {p.timestamp &&*/}
                    {/*                    <div><strong>Time:</strong> {new Date(p.timestamp).toLocaleTimeString()}</div>}*/}
                    {/*            </div>*/}
                    {/*        </Popup>*/}
                    {/*    </Marker>*/}
                    {/*))}*/}
                    {/* Start marker — green */}
                    <Marker position={route[0]} icon={customIcon('#4ade80')}/>
                    {/* End marker — red */}
                    <Marker position={route[route.length - 1]} icon={customIcon('#f87171')}/>
                </>
            ) : (
                <Marker position={position}>
                    <Popup>
                        Current Position
                    </Popup>
                </Marker>
            )}
        </MapContainer>
    );
});