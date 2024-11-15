import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet'; // Import necessary components
import L from 'leaflet'; // Import leaflet for custom icons (if needed)
import 'leaflet/dist/leaflet.css'; // Import the Leaflet CSS file for proper map styling


export default function MapView({lat, lng})  {
    const position = [lat, lng]; // Latitude, Longitude for the map center
    const zoom = 18; // Map zoom level

    const googleSat = L.tileLayer('http://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });

    return (

        <MapContainer className="nodrag" center={position} zoom={zoom} layers={googleSat} style={{ height: '40vh', width: '62vh', borderRadius:'12px' }}>
            <TileLayer
                url="http://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}" // TileLayer URL (OSM)
            />
            <Marker position={position}>

            </Marker>
        </MapContainer>
    );
}