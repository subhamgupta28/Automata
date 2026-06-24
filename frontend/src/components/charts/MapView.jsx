import {MapContainer, Marker, Polyline, Popup, TileLayer, useMap} from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import React, {useEffect} from "react";
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
 * points (optional): richer per-coordinate metadata for intermediate markers, e.g.
 *   [{ lat, lng, speed, satellites, fix, timestamp }, ...]
 * Rendered as small color-coded dots (by speed) with a popup showing the metrics.
 * Pass a subset (e.g. every Nth point) from the caller to avoid marker clutter —
 * MapView does not down-sample points itself.
 */
export const MapView = React.memo(({lat, lng, h, w, route, points}) => {
    const position = [lat || 0, lng || 0];
    const hasRoute = route && route.length > 1;
    const hasPoints = points && points.length > 0;

    return (
        <MapContainer
            center={position}
            zoom={16}
            style={{
                height: h, width: w, borderRadius: '8px',
                // boxShadow: 'rgb(30 30 30) 0px 0px 6px 10px',
                overflow: 'hidden',
                background: 'transparent',
                border: 'none'
            }}
            className="nodrag"
        >
            <RecenterMap position={position}/>
            <TileLayer
                url="https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                maxZoom={20}
                rotationAngle={60}
                subdomains={['mt0', 'mt1', 'mt2', 'mt3']}
                className="dark-tiles"
            />

            {hasRoute ? (
                <>
                    <FitBounds route={route}/>
                    <Polyline
                        positions={route}
                        pathOptions={{color: '#4ade80', weight: 4, opacity: 0.85}}
                    />
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