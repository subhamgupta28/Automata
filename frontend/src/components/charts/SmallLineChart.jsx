// components/SmallLineChart.jsx
import * as React from 'react';
import { LineChart } from '@mui/x-charts/LineChart';

export default function SmallLineChart({ value }) {
    const [data, setData] = React.useState([]);

    React.useEffect(() => {
        if (typeof value === 'number') {
            setData((prev) => {
                const updated = [...prev.slice(-19), value];
                return updated;
            });
        }
    }, [value]);

    return (
        <LineChart
            xAxis={[{ data: data.map((_, i) => i), scaleType: 'point', hideTooltip: true, axis: null }]}
            series={[{ data, color: '#00e0ff' }]}
            height={40}
            margin={{ top: 0, bottom: 0, left: 0, right: 0 }}
            grid={{ vertical: false, horizontal: false }}
            sx={{ '.MuiLineElement-root': { strokeWidth: 2 } }}
        />
    );
}
