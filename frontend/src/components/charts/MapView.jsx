import { MapContainer, TileLayer, Marker } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import React from "react";

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

export const MapView = React.memo(({ lat, lng, h, w }) => {
    const position = [lat || 0, lng || 0];

    return (
        <MapContainer
            center={position}
            zoom={18}
            style={{ height: h, width: w, borderRadius: '8px', marginTop: '10px' }}
            className="nodrag"
        >
            <TileLayer
                url="https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                maxZoom={20}
                subdomains={['mt0', 'mt1', 'mt2', 'mt3']}
            />
            <Marker position={position} />
        </MapContainer>
    );
});