import { LineChart } from '@mui/x-charts/LineChart';
import {Handle, Position} from "@xyflow/react";
import React, {useEffect, useState} from "react";
import {Alert, Card, CardActions, CardContent, Slider} from "@mui/material";
import Typography from "@mui/material/Typography";
import {BarChart} from "@mui/x-charts";
import { axisClasses } from '@mui/x-charts/ChartsAxis';
function valueFormatter(value) {
    return `${value}mm`;
}
function BarChartComp({attribute, device}) {
    const dataset = [
        {
            seoul: 21,
            month: 'Jan',
        },
        {
            seoul: 28,
            month: 'Feb',
        },
        {
            seoul: 41,
            month: 'Mar',
        },
        {
            seoul: 73,
            month: 'Apr',
        },
        {
            seoul: 99,
            month: 'May',
        },
        {
            seoul: 44,
            month: 'June',
        }
    ];
    const chartSetting = {
        yAxis: [
            {
                label: 'rainfall (mm)',
            },
        ],
        series: [{ dataKey: 'seoul', valueFormatter }],
        height: 300,
        width: 600,
        sx: {
            [`& .${axisClasses.directionY} .${axisClasses.label}`]: {
                transform: 'translateX(-10px)',
            },
        },
    };
    return(
        <div>
            <Card style={{display: 'flex', borderRadius: '12px', marginLeft: '3px', marginRight: '3px' , marginTop:'4px'}}>
                <CardContent style={{minWidth: '200px', alignItems: 'center', margin:'4px'}}>
                    <BarChart
                        dataset={dataset}
                        xAxis={[
                            { scaleType: 'band', dataKey: 'month' },
                        ]}
                        borderRadius={10}
                        {...chartSetting}
                    />
                    <Slider
                        className="nodrag"
                        aria-label="Temperature"
                        orientation="vertical"
                        style={{height: '300px', width: '100px', borderRadius: '22px'}}
                        valueLabelDisplay="auto"
                        defaultValue={30}
                    />
                    {/*<Typography textAlign='center' color='white' variant="h5">*/}
                    {/*    {attribute.displayName}*/}
                    {/*</Typography>*/}
                </CardContent>
            </Card>

        </div>
    )
}

export default function ChartNode ({data, isConnectable}){
    const [barData, setBarData] = useState([{key: ""}]);


    useEffect(() => {
        setBarData(data.value.attributes.filter((t) => t.type === "DATA|CHART"));
    }, [data.live]);

    return(
        <div className="text-updater-node">
            <Alert icon={false} variant="filled" severity='info'
                   style={{borderRadius: '16px', padding: '0.5px'}}>
                <Card style={{borderRadius: '12px', marginLeft: '3px', marginRight: '2px'}}>
                    <CardContent style={{minWidth: '200px', alignItems: 'center', padding:'0', display: 'flex', flexDirection: 'row',}}>
                        {/*{barData && barData.map((attribute, i) => (*/}
                        {/*    <BarChartComp attribute={attribute} device={data.value}/>*/}
                        {/*))}*/}
                        <BarChartComp />
                    </CardContent>
                    {/*<Typography textAlign='center' color='white' variant="h5">*/}
                    {/*    {data.value.name}*/}
                    {/*</Typography>*/}
                </Card>
            </Alert>
            <Handle
                type="source"
                position={Position.Left}
                id="c"
                style={{top: 30}}
                isConnectable={isConnectable}
            />
        </div>
    )
}