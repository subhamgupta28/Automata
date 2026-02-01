import React, {useEffect, useState} from "react";
import {Line} from "react-chartjs-2";

import {
    ChartContainer, LinePlot, MarkPlot,
    lineElementClasses,
    markElementClasses, LineChart,
} from "@mui/x-charts";
import {Box} from "@mui/material";
import {getEnergyAnalytics} from "../../services/apis.jsx";

const margin = { right: 24 };

const seriesData = [
    { data: [2400, 1398, 9800, 3908, 4800, 3800, 4300], label: 'pv' },
    { data: [4000, 3000, 2000, 2780, 1890, 2390, 3490], label: 'uv' },
];

const xLabels = [
    'Page A',
    'Page B',
    'Page C',
    'Page D',
    'Page E',
    'Page F',
    'Page G',
];

export default function CustomLineChart({ vid, status="DISCHARGE" }) {
    const [series, setSeries] = useState([]);
    const [labels, setLabels] = useState(["0"]);
    const [max, setMax] = useState(200);

    useEffect(() => {
        const fetch = async () => {
            const res = await getEnergyAnalytics(vid, status === "DISCHARGE" ? "totalWh" : "chargeTotalWh");
            console.log("data", res)
            const {labels, data} = res;
            setLabels(labels);
            setSeries(data);
        }
        fetch();
    }, [])
    return (
        <Box sx={{ width: '100%', height: 300 }}>
            <LineChart
                series={series}
                hideLegend
                xAxis={[{ scaleType: 'point', data: labels }]}
                yAxis={[{ position: 'none' }]}
                // margin={margin}
                slots={{
                    mark: (props) => (
                        <CustomMark
                            {...props}
                            seriesData={series}
                        />
                    ),
                }}
            />
        </Box>
    );
}

function CustomMark({ x, y, color, dataIndex, id, seriesData }) {
    console.log(id, dataIndex, seriesData)
    const seriesIndex = Number(id.split('-').pop());
    const value = seriesData.filter(s=> s.id === id)[0].data[dataIndex];

    if (value == null) return null;

    return (
        <g>
            <circle cx={x} cy={y} r={4} fill={color} />
            <text
                x={x}
                y={y - 12}
                textAnchor="middle"
                fill={color}
                fontSize={12}
                fontWeight="bold"
            >
                {value}
            </text>
        </g>
    );
}