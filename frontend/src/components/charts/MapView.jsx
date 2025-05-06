import {MapContainer, TileLayer, Marker, Popup, CircleMarker} from 'react-leaflet'; // Import necessary components
import L from 'leaflet'; // Import leaflet for custom icons (if needed)
import 'leaflet/dist/leaflet.css';
import React from "react";


export const MapView = React.memo(({lat, lng, h, w}) =>  {
    if (!lat || !lng) {
        lat = 0
        lng = 0;
    }
    const position = [lat, lng]; // Latitude, Longitude for the map center
    const zoom = 18; // Map zoom level

    const googleSat = L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });

    return (

        <MapContainer className="nodrag" center={position} zoom={zoom} layers={googleSat} style={{ height: h, width: w, borderRadius:'8px', marginTop:'10px' }}>
            {/*<TileLayer*/}
            {/*    url="https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}" // TileLayer URL (OSM)*/}
            {/*/>*/}
            <Marker position={position}>

            </Marker>
        </MapContainer>
    );
});