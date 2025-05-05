import * as React from 'react';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import HomeRoundedIcon from '@mui/icons-material/HomeRounded';
import AnalyticsRoundedIcon from '@mui/icons-material/AnalyticsRounded';
import PeopleRoundedIcon from '@mui/icons-material/PeopleRounded';
import AssignmentRoundedIcon from '@mui/icons-material/AssignmentRounded';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import InfoRoundedIcon from '@mui/icons-material/InfoRounded';
import HelpRoundedIcon from '@mui/icons-material/HelpRounded';
import {NavLink} from "react-router-dom";

const mainListItems = [
    { text: 'Home', icon: <HomeRoundedIcon />, url:'/' },
    { text: 'Automations', icon: <AnalyticsRoundedIcon />, url:'/actions' },
    { text: 'Analytics', icon: <PeopleRoundedIcon />, url:'/analytics' },
    { text: 'Devices', icon: <AssignmentRoundedIcon />, url:'/devices' },
];

const secondaryListItems = [
    { text: 'Configure', icon: <SettingsRoundedIcon />, url:'/configure' },
    { text: 'About', icon: <InfoRoundedIcon />, url:'/about' },
    { text: 'Feedback', icon: <HelpRoundedIcon />, url:'/feedback' },
];

export default function MenuContent() {
    return (
        <Stack sx={{ flexGrow: 1, p: 1, justifyContent: 'space-between' }}>
            <List dense>
                {mainListItems.map((item, index) => (
                    <ListItem key={index} disablePadding sx={{ display: 'block', }} component={NavLink} to={item.url} style={{borderRadius:'10px', color:'white'}}>
                        <ListItemButton selected={index === 0}>
                            <ListItemIcon>{item.icon}</ListItemIcon>
                            <ListItemText primary={item.text} />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>

            <List dense>
                {secondaryListItems.map((item, index) => (
                    <ListItem key={index} disablePadding component={NavLink} to={item.url} sx={{ display: 'block',color:'white' }}>
                        <ListItemButton>
                            <ListItemIcon>{item.icon}</ListItemIcon>
                            <ListItemText primary={item.text} />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
        </Stack>
    );
}