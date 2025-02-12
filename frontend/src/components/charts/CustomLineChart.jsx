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
    console.log("chartData", chartData);
    if (chartData.data)
        chartData.data.map((c) => {
            list.push(c[chartData.dataKey]);
        });
    console.log("chartData l", list);
    return (
        <div>
            <ChartContainer
                width={550}
                height={230}
                series={[{type: 'line', data: list}]}
                xAxis={[{scaleType: 'point', data: chartData.timestamps}]}
                sx={{
                    [`& .${lineElementClasses.root}`]: {
                        stroke: '#8884d8',
                        strokeWidth: 2,
                    },
                    [`& .${markElementClasses.root}`]: {
                        stroke: '#8884d8',
                        scale: '0.8',
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