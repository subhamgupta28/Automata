import {useEffect, useState} from "react";

export function useGeolocation() {
    const [coords, setCoords] = useState(null);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!navigator.geolocation) {
            setError("Geolocation is not supported by this browser.");
            return;
        }

        navigator.geolocation.getCurrentPosition(
            (pos) =>
                setCoords({
                    latitude: pos.coords.latitude,
                    longitude: pos.coords.longitude,
                }),
            (err) => setError(err.message),
            {timeout: 10000, maximumAge: 5 * 60 * 1000}
        );
    }, []);

    return {coords, error};
}