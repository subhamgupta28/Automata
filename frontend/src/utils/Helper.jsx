import {useEffect, useRef, useState} from "react";

export default function isEmpty(obj) {
    for (const prop in obj) {
        if (Object.hasOwn(obj, prop)) {
            return false;
        }
    }
    return true;
}

export function useAnimatedNumber(value, duration = 600) {
    const [display, setDisplay] = useState(value);
    const startValue = useRef(value);
    const startTime = useRef(null);

    useEffect(() => {
        startValue.current = display;
        startTime.current = null;

        function animate(timestamp) {
            if (!startTime.current) startTime.current = timestamp;
            const progress = Math.min(
                (timestamp - startTime.current) / duration,
                1
            );

            const next =
                startValue.current +
                (value - startValue.current) * progress;

            setDisplay(next);

            if (progress < 1) {
                requestAnimationFrame(animate);
            }
        }

        requestAnimationFrame(animate);
    }, [value, duration]);

    return display;
}