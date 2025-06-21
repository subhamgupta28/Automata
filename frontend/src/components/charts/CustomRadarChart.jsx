import * as React from 'react';
import { RadarChart } from '@mui/x-charts/RadarChart';

// Data from https://ourworldindata.org/emissions-by-fuel

function valueFormatter(v) {
    if (v === null) {
        return 'NaN';
    }
    return `${v.toLocaleString()}t CO2eq/pers`;
}

export default function CustomRadarChart({liveData, radarData}) {
    return (
        <RadarChart
            height={300}
            series={[
                { label: 'Temp', data: [66.65, 10, 16.5, 15, 200, 78], valueFormatter },
            ]}
            radar={{
                metrics: ['Power', 'Current', 'Voltage', 'Capacity','Energy', 'Percent'],
            }}
        />
    );
}
