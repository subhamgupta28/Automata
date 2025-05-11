import * as React from 'react';
import {styled, useTheme} from '@mui/material/styles';
import Box from '@mui/material/Box';
import MuiDrawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import CssBaseline from '@mui/material/CssBaseline';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import InboxIcon from '@mui/icons-material/MoveToInbox';
import MailIcon from '@mui/icons-material/Mail';
import {NavLink, Route, Routes} from "react-router-dom";
import DeviceNodes from "../dashboard/DeviceNodes.jsx";
import Devices from "../Devices.jsx";
import MobileView from "../dashboard/MobileView.jsx";
import AnalyticsView from "../dashboard/AnalyticsView.jsx";
import {ConfigurationView} from "../dashboard/ConfigurationView.jsx";
import {AppCacheProvider} from "../../services/AppCacheContext.jsx";
import ActionBoard from "../action/ActionBoard.jsx";
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import Typography from "@mui/material/Typography";
import HomeIcon from '@mui/icons-material/Home';
import DeveloperBoardIcon from '@mui/icons-material/DeveloperBoard';
import SettingsIcon from '@mui/icons-material/Settings';
import AssessmentIcon from '@mui/icons-material/Assessment';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import AdbIcon from "@mui/icons-material/Adb";
import Notifications from "../Notifications.jsx";
import {SnackbarProvider} from "notistack";
import {Card} from "@mui/material";
import Exp from "../dashboard/Exp.jsx";
import {DeviceDataProvider} from "../../services/DeviceDataProvider.jsx";
import SignUp from "../auth/SignUp.jsx";
import SignIn from "../auth/SignIn.jsx";
import PrivateRoute from "../auth/PrivateRoute.jsx";
import {AuthProvider} from "../auth/AuthContext.jsx";

const drawerWidth = 200;

const openedMixin = (theme) => ({
    width: drawerWidth,
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.enteringScreen,
    }),
    overflowX: 'hidden',
});

const closedMixin = (theme) => ({
    transition: theme.transitions.create('width', {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    overflowX: 'hidden',
    width: `calc(${theme.spacing(5)} + 1px)`,
    [theme.breakpoints.up('sm')]: {
        width: `calc(${theme.spacing(8)} + 1px)`,
    },
});

const DrawerHeader = styled('div')(({theme}) => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    padding: theme.spacing(0, 1.5),
    // necessary for content to be below app bar
    ...theme.mixins.toolbar,
}));


const Drawer = styled(MuiDrawer, {shouldForwardProp: (prop) => prop !== 'open'})(
    ({theme}) => ({
        width: drawerWidth,
        flexShrink: 0,
        whiteSpace: 'nowrap',
        boxSizing: 'border-box',
        variants: [
            {
                props: ({open}) => open,
                style: {
                    ...openedMixin(theme),
                    '& .MuiDrawer-paper': openedMixin(theme),
                },
            },
            {
                props: ({open}) => !open,
                style: {
                    ...closedMixin(theme),
                    '& .MuiDrawer-paper': closedMixin(theme),
                },
            },
        ],
    }),
);

export default function SideDrawer() {
    const [open, setOpen] = React.useState(false);
    const [selectedIndex, setSelectedIndex] = React.useState(0);

    const listItems = [
        {
            name: 'Home',
            url: '',
            icon: <HomeIcon/>
        },
        {
            name: 'Automations',
            url: '/actions',
            icon: <AutoAwesomeIcon/>
        },
        {
            name: 'Devices',
            url: '/devices',
            icon: <DeveloperBoardIcon/>
        },
        {
            name: 'Configure',
            url: '/configure',
            icon: <SettingsIcon/>
        },
        {
            name: 'Analytics',
            url: '/analytics',
            icon: <AssessmentIcon/>
        },
    ];

    const handleListItemClick = (event, index) => {
        setSelectedIndex(index);
    };
    const handleDrawerOpen = () => {
        setOpen(true);
    };

    const handleDrawerClose = () => {
        setOpen(s => !s);
    };

    return (
        <Box sx={{display: 'flex'}}>
            <CssBaseline/>

            <Drawer variant="permanent" open={open} elevation={0}
                    style={{
                        backgroundColor: 'rgba(255, 255, 255, 0.0)',
                        backdropFilter: 'blur(1px)',
                        height: '100dvh'
                    }}>
                <DrawerHeader>
                    {/*<IconButton onClick={handleDrawerClose}>*/}
                    {/*   <MenuIcon />*/}
                    {/*</IconButton>*/}
                    <IconButton onClick={handleDrawerClose}>
                        {open ? <ChevronLeftIcon/> : <MenuIcon/>}
                    </IconButton>
                </DrawerHeader>
                {/*<Divider/>*/}
                <List>
                    {listItems.map((item, index) => (
                        <ListItem key={item.name} disablePadding sx={{display: 'block'}}>
                            <ListItemButton
                                selected={selectedIndex === index}
                                onClick={(event) => handleListItemClick(event, index)}
                                component={NavLink}
                                to={item.url}
                                sx={[
                                    {
                                        // minHeight: 48,
                                        borderRadius: 2,
                                        borderColor: 'red',
                                        margin: 1,
                                        px: 2.5,
                                    },
                                    open
                                        ? {
                                            justifyContent: 'initial',
                                        }
                                        : {
                                            justifyContent: 'center',
                                        },
                                ]}
                            >
                                <ListItemIcon
                                    sx={[
                                        {
                                            minWidth: 0,
                                            justifyContent: 'center',
                                        },
                                        open
                                            ? {
                                                mr: 3,
                                            }
                                            : {
                                                mr: 'auto',
                                            },
                                    ]}
                                >
                                    {item.icon}
                                </ListItemIcon>
                                <ListItemText
                                    primary={item.name}
                                    sx={[
                                        open
                                            ? {
                                                opacity: 1,
                                            }
                                            : {
                                                opacity: 0,
                                            },
                                    ]}
                                />
                            </ListItemButton>
                        </ListItem>
                    ))}
                </List>
                {/*<Divider/>*/}
            </Drawer>
            <Box component="main" sx={{flexGrow: 1}}>

                <Card
                    elevation={10}
                    variant="outlined"
                    sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                    style={{
                        marginLeft: "10px",
                        position: 'absolute',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        padding: '1px 2px 2px 1px',
                        backgroundColor: 'rgba(200, 200, 200, 0.0)',
                        backdropFilter: 'blur(4px)',
                        borderRadius: '0px 0px 10px 10px'
                    }}
                >
                    <AdbIcon sx={{display: {md: 'flex'}}}/>
                    <Typography
                        variant="h6"
                        noWrap
                        component="a"
                        href="/"
                        sx={{

                            display: {md: 'flex'},
                            fontFamily: 'monospace',
                            fontWeight: 700,
                            letterSpacing: '.3rem',
                            color: 'inherit',
                            textDecoration: 'none',
                        }}
                    >
                        Automata
                    </Typography>
                </Card>

                <AuthProvider>
                    <AppCacheProvider>
                        <DeviceDataProvider>
                            <Routes>
                                {/*open*/}
                                <Route path="/" element={<DeviceNodes/>}/>
                                <Route path="mob" element={<MobileView/>}/>
                                <Route path="exp" element={<Exp/>}/>
                                <Route path="analytics" element={<AnalyticsView/>}/>
                                <Route path="signup" element={<SignUp/>}/>
                                <Route path="signin" element={<SignIn/>}/>
                                {/*protected*/}
                                <Route path="actions" element={<PrivateRoute element={<ActionBoard/>}/>}/>
                                <Route path="exp" element={<PrivateRoute element={<Exp/>}/>}/>
                                <Route path="devices" element={<PrivateRoute element={<Devices/>}/>}/>
                                <Route path="configure" element={<PrivateRoute element={<ConfigurationView/>}/>}/>
                            </Routes>
                        </DeviceDataProvider>
                    </AppCacheProvider>
                </AuthProvider>
                <SnackbarProvider maxSnack={3} preventDuplicate>
                    <Notifications/>
                </SnackbarProvider>
            </Box>
        </Box>
    );
}
