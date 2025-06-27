import * as React from 'react';
import { Box } from '@mui/material';
import WhatshotIcon from '@mui/icons-material/Whatshot';   // Hot
import WbSunnyIcon from '@mui/icons-material/WbSunny';     // Warm
import AcUnitIcon from '@mui/icons-material/AcUnit';       // Cold

export default function TemperatureIcons({ temp = 25 }) {
    let Icon, color;

    if (temp < 24) {
        Icon = AcUnitIcon;
        color = '#00BFFF'; // DeepSkyBlue
    } else if (temp < 35) {
        Icon = WbSunnyIcon;
        color = '#fcb32f'; // Orange
    } else {
        Icon = WhatshotIcon;
        color = '#ff6a00'; // OrangeRed
    }

    return (
        <Box display="flex" justifyContent="center" alignItems="center">
            <Icon sx={{ fontSize: 24, color }} />
        </Box>
    );
}
