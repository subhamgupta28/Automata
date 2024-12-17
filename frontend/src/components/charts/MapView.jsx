import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet'; // Import necessary components
import L from 'leaflet'; // Import leaflet for custom icons (if needed)
import 'leaflet/dist/leaflet.css'; // Import the Leaflet CSS file for proper map styling


export default function MapView({lat, lng})  {
    if (!lat || !lng) {
        lat = 0
        lng = 0;
    }
    const position = [lat, lng]; // Latitude, Longitude for the map center
    const zoom = 18; // Map zoom level

    const googleSat = L.tileLayer('https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });

    return (

        <MapContainer className="nodrag" center={position} zoom={zoom} layers={googleSat} style={{ height: '280px', width: '200px', borderRadius:'8px' }}>
            <TileLayer
                url="https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}" // TileLayer URL (OSM)
            />
            <Marker position={position}>

            </Marker>
        </MapContainer>
    );
}