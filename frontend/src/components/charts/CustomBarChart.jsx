import React, {useMemo} from "react";
import {axisClasses} from "@mui/x-charts/ChartsAxis";
import {BarChart, lineElementClasses} from "@mui/x-charts";


export default function CustomBarChart ({chartData}){

    console.log("chartdata",chartData)
    const valueFormatter = (value) => {
        return `${value} ${chartData.unit}`;
    };
    const chartSetting = useMemo(() => ({
        yAxis: [
            {
                label: chartData.label,
            },
        ],
        series: [{
            dataKey: chartData.dataKey,
            // label: 'Showing last 8 Hours data',
            color: `url(#Gradient1)`,
            valueFormatter
        }],
        height: 300,
        width: 800,
        sx: {
            [`& .${axisClasses.directionY} .${axisClasses.label}`]: {
                transform: 'translateX(-10px)',
            },
        },
    }), [chartData]);
    return(
        <BarChart className="nodrag"
                  dataset={chartData.data}
            // barLabel={(item, context) => {
            //     return item.value?.toString();
            // }}
                  colors={['#e5e5e5']}
                  xAxis={[{ scaleType: 'band', dataKey: chartData.dataKey, data: chartData.timestamps, zoom: true }]}
                  borderRadius={6}
                  {...chartSetting}
        >
            <defs>
                <linearGradient
                    id={`Gradient1`}
                    x1="0%"
                    y1="0%"
                    x2="0%"
                    y2="100%"
                >
                    <stop offset="0%" stopColor="rgb(244,255,87)" stopOpacity="0.8"/>
                    <stop offset="100%" stopColor="#ffffff" stopOpacity="0.1"/>
                </linearGradient>
            </defs>
        </BarChart>
    )

}