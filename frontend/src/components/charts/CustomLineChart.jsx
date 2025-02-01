import React, {useEffect, useState} from "react";
import {Line} from "react-chartjs-2";
import faker from 'faker';
import {
    ChartContainer, LinePlot, MarkPlot,
    lineElementClasses,
    markElementClasses, LineChart,
} from "@mui/x-charts";

export default function CustomLineChart({chartData}) {
    const list = [];
    console.log(chartData);
    const values = chartData.data.map((c)=>{

        list.push(c[chartData.dataKey]);
    });
    const pData = [1400, 2398, 4800, 3908, 4800, 3800, 4300];
    const xLabels = [
        'Page A',
        'Page B',
        'Page C',
        'Page D',
        'Page E',
        'Page F',
        'Page G',
    ];
    return (
        <div>
            <ChartContainer
                width={650}
                height={300}
                series={[{type: 'line', data: list}]}
                xAxis={[{scaleType: 'point', data: chartData.timestamps}]}
                sx={{
                    [`& .${lineElementClasses.root}`]: {
                        stroke: '#8884d8',
                        strokeWidth: 2,
                    },
                    [`& .${markElementClasses.root}`]: {
                        stroke: '#8884d8',
                        scale: '0.6',
                        fill: '#fff',
                        strokeWidth: 2,
                    },
                }}
                disableAxisListener
            >
                <LinePlot/>
                <MarkPlot/>
            </ChartContainer>

            {/*<LineChart*/}
            {/*    width={650}*/}
            {/*    height={300}*/}
            {/*    series={[*/}
            {/*        { data: list, label: 'pv' },*/}
            {/*    ]}*/}
            {/*    xAxis={[{ scaleType: 'point', data: chartData.timestamps }]}*/}
            {/*/>*/}
        </div>
    )
}