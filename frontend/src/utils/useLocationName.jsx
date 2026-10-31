import {useEffect, useState} from "react";

export function useLocationName(coords) {
    const [name, setName] = useState(null);

    useEffect(() => {
        if (!coords) return;

        const controller = new AbortController();
        const {latitude, longitude} = coords;

        fetch(
            `https://api.bigdatacloud.net/data/reverse-geocode-client` +
            `?latitude=${latitude}&longitude=${longitude}&localityLanguage=en`,
            {signal: controller.signal}
        )
            .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
            .then((data) => {
                const parts = [data.city || data.locality, data.principalSubdivision, data.countryName]
                    .filter(Boolean);
                setName(parts.join(", "));
            })
            .catch((err) => {
                if (err?.name !== "AbortError") setName(null); // non-fatal
            });

        return () => controller.abort();
    }, [coords]);

    return name;
}