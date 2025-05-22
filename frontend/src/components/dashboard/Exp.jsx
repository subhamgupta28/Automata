import React, {useMemo} from "react";
import { BarChart } from '@mui/x-charts/BarChart';
import { Grid, Paper } from '@mui/material';
import AccessAlarms from "@mui/icons-material/AccessAlarms";
import CottageOutlinedIcon from "@mui/icons-material/CottageOutlined";
import {axisClasses} from "@mui/x-charts/ChartsAxis";


const Exp = () => {
    return (
        <div>
            <CustomGrid/>
        </div>
    );
};

export default Exp;



const CustomGrid = () => {
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
                  colors={['#b9b9b9']}
                  xAxis={[{ scaleType: 'band', dataKey: chartData.dataKey, data: chartData.timestamps, zoom: true }]}
                  borderRadius={10}
                  {...chartSetting}
        />
    )
};
