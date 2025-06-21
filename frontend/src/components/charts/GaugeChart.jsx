import {Gauge, gaugeClasses} from '@mui/x-charts/Gauge';
import Typography from "@mui/material/Typography";
import React from "react";
import HumidityGauge from "./HumidityGauge.jsx";

export const GaugeChart = React.memo(({value, maxValue, displayName}) => {
    const isHumidity = displayName.toString().includes("Humidity");
    return (
        <>
            <div style={{display: 'flex', justifyContent: 'center'}}>
                {
                    isHumidity ?
                        <HumidityGauge humidity={value} displayName={displayName}/>
                        :
                        <div>
                            <Gauge
                                value={parseInt(value)}
                                startAngle={-130}
                                endAngle={130}
                                height={145}
                                valueMin={0}
                                cornerRadius="50%"
                                valueMax={maxValue}

                                innerRadius="75%"
                                outerRadius="100%"
                                sx={{
                                    [`& .${gaugeClasses.valueText}`]: {
                                        transform: 'translate(0px, 0px)',
                                    },
                                    [`& .${gaugeClasses.valueArc}`]: {
                                        fill: '#b9b9b9',
                                    },
                                }}
                                text={
                                    ({value, valueMax}) => `${value} / ${maxValue}`
                                }
                            />
                            <Typography textAlign='center'>
                                {displayName}
                            </Typography>
                        </div>

                }
            </div>
        </>
    )
});