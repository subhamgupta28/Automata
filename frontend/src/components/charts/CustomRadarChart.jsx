import React, { useEffect, useState, useRef } from "react";
import { RadarChart } from "@mui/x-charts/RadarChart";
import {
    Card,
    CardContent,
    Typography,
    Box,
    ToggleButton,
    ToggleButtonGroup,
} from "@mui/material";
import { useDeviceLiveData } from "../../services/DeviceDataProvider.jsx";
import { getDetailChartData } from "../../services/apis.jsx";

// Metric labels (can be dynamic if needed)
const radarMetrics = ['W', 'V', 'I', '%'];

export default function CustomRadarChart({ deviceId, name, messages, attributes }) {
    const [data, setData] = useState([]);
    const [range, setRange] = useState("day");
    const dataRef = useRef([]);


    useEffect(() => {
        if (messages) {
            const newEntry = {
                ...messages,
                label: "Live",
                id: `live-${Date.now()}`
            };

            const updatedData = [...dataRef.current.slice(-4), newEntry];
            dataRef.current = updatedData;
            setData(updatedData);
        }
    }, [messages, deviceId]);

    const radarSeries = data.map((entry, index) => ({
        id: entry.id || `entry-${index}`,
        label: entry.label || `#${index + 1}`,
        data: attributes.map((attr) => entry[attr] || 0),
        fillArea: true,
        hideMark: true,
    }));

    return (
        <div style={{
            borderColor: 'grey',
            borderWidth: '2px',
            borderStyle: 'dashed',
            borderRadius: '12px',
        }}>
            <RadarChart
                series={radarSeries}
                hideLegend={true}
                radar={{
                    metrics: attributes.length ? attributes.map(attr => attr.toUpperCase()) : radarMetrics,
                    startAngle: 45,
                }}
                highlight="series"
                shape="circular"
                slotProps={{ tooltip: { trigger: 'item' } }}
                sx={{
                    '& .MuiChartsLegend-root': {
                        gridTemplateColumns: 'repeat(2, 1fr)',
                        display: 'grid',
                    },
                }}
            />
        </div>
    );
}
