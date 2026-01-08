import {useEffect, useRef, useState} from "react";
import {getEnergyStats} from "../services/apis.jsx";

export default function isEmpty(obj) {
    for (const prop in obj) {
        if (Object.hasOwn(obj, prop)) {
            return false;
        }
    }
    return true;
}
export async function useEnergyStats(deviceIds) {
    // const [statsData, setStatsData] = useState(() => ({
    let statsData = {
        totalWh: 0,
        peakWh: 0,
        lowestWh: 0,
        chargeTotalWh: 0,
        chargePeakWh: 0,
        chargeLowestWh: 0,
        percent: 0,
        // NEW trend fields
        totalWhTrend: 0,
        peakWhTrend: 0,
        lowestWhTrend: 0,
        percentTrend: 0,
    };
    // ));

    // useEffect(() => {
    //     if (!deviceIds?.length) return;

        const fetchStats = async () => {
            try {
                const results = await Promise.all(
                    deviceIds.map(id => getEnergyStats(id))
                );

                const combined = results.reduce(
                    (acc, res) => ({
                        totalWh: acc.totalWh + Number(res?.totalWh || 0),
                        peakWh: acc.peakWh + Number(res?.peakWh || 0),
                        lowestWh: acc.lowestWh + Number(res?.lowestWh || 0),
                        chargeTotalWh: acc.chargeTotalWh + Number(res?.chargeTotalWh || 0),
                        chargePeakWh: acc.chargePeakWh + Number(res?.chargePeakWh || 0),
                        chargeLowestWh: acc.chargeLowestWh + Number(res?.chargeLowestWh || 0),
                        // Trends now come from backend
                        totalWhTrend: acc.totalWhTrend + Number(res?.totalWhTrend || 0),
                        peakWhTrend: acc.peakWhTrend + Number(res?.peakWhTrend || 0),
                        lowestWhTrend: acc.lowestWhTrend + Number(res?.lowestWhTrend || 0),
                        percentTrend: acc.percentTrend + Number(res?.percentTrend || 0),
                        percent: acc.percent + Number(res?.percent || 0),
                    }),
                    {
                        totalWh: 0,
                        peakWh: 0,
                        lowestWh: 0,
                        chargeTotalWh: 0,
                        chargePeakWh: 0,
                        chargeLowestWh: 0,
                        totalWhTrend: 0,
                        peakWhTrend: 0,
                        lowestWhTrend: 0,
                        percentTrend: 0,
                        percent: 0,
                    }
                );

                if (combined.percent) {
                    combined.percent = combined.percent / results.length;
                    combined.percentTrend = (combined.percentTrend < 0 ?
                        Math.abs(combined.percentTrend) : combined.percentTrend);

                }

                statsData = combined;
            } catch (err) {
                console.error("Energy stats fetch failed", err);
            }
        };

        await fetchStats();
    //     const id = setInterval(fetchStats, 60_000);
    //
    //     return () => clearInterval(id);
    // }, [deviceIds]);

    return statsData;
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