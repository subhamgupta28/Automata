import {Gauge, gaugeClasses} from '@mui/x-charts/Gauge';
import Typography from "@mui/material/Typography";
import React from "react";
import HumidityGauge from "./HumidityGauge.jsx";
import WhatshotIcon from "@mui/icons-material/Whatshot";
import OpacityIcon from "@mui/icons-material/Opacity";
import {Box} from "@mui/material";

export const GaugeChart = React.memo(({value, maxValue, displayName}) => {
    const isHumidity = displayName.toString().includes("Humid");
    const Icon = value < 40 ? WhatshotIcon : OpacityIcon; // dry vs wet
    return (
        <>
            <Box sx={{position: 'relative', mx: 'auto'}}>
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
                        [`& .${gaugeClasses.valueText}`]: {
                            fill: '#fff',
                            fontSize: 24,
                            fontWeight: 'bold',
                            display: isHumidity ? 'none' : 'normal' // hide built-in % text
                        }
                    }}

                    text={
                        ({value, valueMax}) => `${value} / ${maxValue}`
                    }
                />
                <Typography textAlign='center'>
                    {displayName}
                </Typography>
                {isHumidity &&
                    <div>


                        <Box
                            sx={{
                                position: 'absolute',
                                top: '50%',
                                left: '50%',
                                transform: 'translate(-50%, -50%)',
                                color: '#fff',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontSize: 36
                            }}
                        >
                            <Icon fontSize="inherit"/>
                        </Box>
                        <Typography
                            variant="caption"
                            sx={{
                                position: 'absolute',
                                top: '64%',
                                left: '50%',
                                transform: 'translateX(-50%)',
                                color: '#fff',
                                fontSize: 16,
                                fontWeight: 'bold',
                            }}
                        >
                            {Math.round(value)}%

                        </Typography>
                    </div>
                }


            </Box>
        </>
    )
});