import * as React from 'react';
import { Gauge, gaugeClasses } from '@mui/x-charts/Gauge';
import { Box, Typography } from '@mui/material';
import OpacityIcon from '@mui/icons-material/Opacity'; // Droplet
import WhatshotIcon from '@mui/icons-material/Whatshot'; // Dry/heat-like symbol


export default function DashedHumidityGauge({ humidity = 50 }) {
    const Icon = humidity < 40 ? WhatshotIcon : OpacityIcon; // dry vs wet
    return (
        <Box sx={{ position: 'relative', mx: 'auto' }}>
            <Typography variant="subtitle1" sx={{ mb: 1, color: '#fff' }}>
                Humidity
            </Typography>
            <Gauge
                height={145}
                cornerRadius="50%"
                value={humidity}
                valueMax={100}
                startAngle={0}
                endAngle={360}
                innerRadius="75%"
                outerRadius="100%"
                sx={{

                    [`& .${gaugeClasses.valueText}`]: {
                        fill: '#fff',
                        fontSize: 24,
                        fontWeight: 'bold',
                        display: 'none' // hide built-in % text
                    }
                }}
            />
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
                <Icon fontSize="inherit" />
            </Box>
            {/* Show humidity text below icon */}
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
                {Math.round(humidity)}%
            </Typography>

        </Box>
    );
}
