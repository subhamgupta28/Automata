import React from 'react';
// Dummy data following the EnergyStat Java model (one entry per day)
// Dummy data following the EnergyStat Java model (one entry per day)
import {Box, Card, CardContent, LinearProgress, Typography} from "@mui/material";
import {styled} from "@mui/material/styles";
import TopBar, {StatsRow} from "./SmartHomeDashboard.jsx";
import PeopleIcon from "@mui/icons-material/People";
import LockOpenIcon from "@mui/icons-material/LockOpen";
import WindowIcon from "@mui/icons-material/Window";
import HomeIcon from "@mui/icons-material/Home";
import AlarmIcon from "@mui/icons-material/Alarm";
import GridViewIcon from "@mui/icons-material/GridView";
import NotificationsIcon from "@mui/icons-material/Notifications";

const BatteryBar = styled(LinearProgress)(({theme, value}) => {
    let color = theme.palette.success.main;
    if (value < 20) color = theme.palette.error.main;
    else if (value < 50) color = theme.palette.warning.main;

    return {
        height: 50,
        borderRadius: 8,
        backgroundColor: theme.palette.grey[300],
        "& .MuiLinearProgress-bar": {
            borderRadius: 8,
            backgroundColor: color,
        },
    };
});

function BatteryGaugeCard({level = 75}) {
    return (
        <Card
            sx={{
                width: "100%",
                maxWidth: 420,
                borderRadius: 3,
            }}
            elevation={3}
        >
            <CardContent>
                <Box display="flex" justifyContent="space-between" mb={1}>
                    <Typography variant="subtitle1" fontWeight={600}>
                        Battery Level
                    </Typography>
                    <Typography variant="subtitle1" fontWeight={600}>
                        {level}%
                    </Typography>
                </Box>

                <BatteryBar variant="determinate" value={level}/>
            </CardContent>
        </Card>
    );
}

const series = [
    {label: 'Battery 250Wh', data: [2000, 1700, 1400, 1159, 1850, 1653]},
    {label: 'Battery 270Wh', data: [1250, 980, 860, 1199, 485, 965]},
    {label: 'Battery 500Wh', data: [1000, 700, 400, 159, 850, 653]},
];


const Exp = () => {
    const weather = {
        location: "Cortes, Madrid, Spain",
        time: "Tuesday, 3:00 PM",
        temp: 20,
        condition: "Cloud",
        humidity: 41,
        precipitation: 7,
        wind: 23,
        aqi: 358,
    };


    return (
        <div style={{marginTop: '10px'}}>


            {/*<AutomationSummaryBar/>*/}
            {/*<AutomationAnalyticsList/>*/}
            {/*<AutomationFlowInspector/>*/}
            <TopBar
                userName="Subham"
                userRoom="Living room"
                time="2:59"
                weather={{label: "Partly cloudy", temp: "18.7°C"}}
                homeStats={{
                    lightsOn: 3,
                    windowsOpen: 1,
                    security: "Armed home",
                    doorLocked: false,
                }}
                alarm="08:00"
                notifications={6}
                scenes={4}
                occupancy={2}
                location="Home"
            />

            <StatsRow
                items={[
                    {icon: <NotificationsIcon/>, label: "Temperature", value: "6"},
                    {icon: <GridViewIcon/>, label: "Temperature", value: "4"},
                    {icon: <AlarmIcon/>, label: "Temperature", value: "08:00"},
                    {icon: <HomeIcon/>, label: "Temperature", value: "Home"},
                    {icon: <WindowIcon/>, label: "Temperature", value: "1"},
                    {icon: <LockOpenIcon/>, label: "Temperature", value: ""},
                    {icon: <PeopleIcon/>, label: "Temperature", value: ""},
                    // ...
                ]}
            />
            {/*<RoomsGrid/>*/}
            {/*<SmartHomeDashboard2/>*/}

        </div>
    );
};

export default Exp;
