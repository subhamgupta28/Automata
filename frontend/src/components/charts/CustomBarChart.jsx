import React, {useMemo} from "react";
import {axisClasses} from "@mui/x-charts/ChartsAxis";
import {BarChart} from "@mui/x-charts";


export default function CustomBarChart ({chartData}){

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
            valueFormatter
        }],
        height: 250,
        width: 650,
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
                  colors={['#b9b9b9']}
                  xAxis={[{ scaleType: 'band', dataKey: chartData.dataKey, data: chartData.timestamps }]}
                  borderRadius={10}
                  {...chartSetting}
        />
    )

}