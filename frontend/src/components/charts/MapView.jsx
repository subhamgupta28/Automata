import {MapContainer, Marker, Polyline, TileLayer, useMap} from 'react-leaflet';
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

const customIcon = (color) => L.divIcon({
    className: '',
    html: `<div style="
        width: 14px; height: 14px;
        background: ${color};
        border: 2px solid #fff;
        border-radius: 50%;
        box-shadow: 0 0 6px rgba(0,0,0,0.6);
    "></div>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7],
});

const FitBounds = ({route}) => {
    const map = useMap();
    useEffect(() => {
        if (route && route.length > 1) {
            map.fitBounds(route, {padding: [40, 40]});
        }
    }, [route, map]);
    return null;
};

export const MapView = React.memo(({lat, lng, h, w, route}) => {
    const position = [lat || 0, lng || 0];
    const hasRoute = route && route.length > 1;

    return (
        <MapContainer
            center={position}
            zoom={18}
            style={{height: h, width: w, borderRadius: '8px'}}
            className="nodrag"
        >
            <TileLayer
                url="https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                maxZoom={20}
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
                    {/* Start marker — green */}
                    <Marker position={route[0]} icon={customIcon('#4ade80')}/>
                    {/* End marker — red */}
                    <Marker position={route[route.length - 1]} icon={customIcon('#f87171')}/>
                </>
            ) : (
                <Marker position={position}/>
            )}
        </MapContainer>
    );
});