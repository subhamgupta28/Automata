import * as React from 'react';
import {styled} from '@mui/material/styles';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import CssBaseline from '@mui/material/CssBaseline';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import {Navigate, NavLink, Route, Routes, useLocation} from "react-router-dom";
import DeviceNodes from "../home/DeviceNodes.jsx";
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
import {AuthProvider, useAuth} from "../auth/AuthContext.jsx";
import LoginIcon from '@mui/icons-material/Login';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import OptionsMenu from "./OptionsMenu.jsx";
import isEmpty from "../../utils/Helper.jsx";
import Welcome from "../Welcome.jsx";
import {useIsMobile} from "../../utils/useIsMobile.jsx";
import SpotifyPlayer from "../integrations/SpotifyPlayer.jsx";

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
    width: `calc(${theme.spacing(7)} + 1px)`,
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


const Drawer = styled(Card, {shouldForwardProp: (prop) => prop !== 'open'})(
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
    const [selectedIndex, setSelectedIndex] = React.useState("/");
    const {user, logout} = useAuth();
    const location = useLocation();

    // Auto redirect to /mobile if mobile and not already there
    // if (isMobile && location.pathname !== '/mob') {
    //     return <Navigate to="/mob" replace />;
    // }
    const publicItems = [
        // {name: 'Welcome', url: '/welcome', icon: <HomeIcon/>},

    ];

    const authItems = [
        {name: 'Home', url: '/', icon: <HomeIcon/>},
        {name: 'Automations', url: '/actions', icon: <AutoAwesomeIcon/>},
        {name: 'Analytics', url: '/analytics', icon: <AssessmentIcon/>},
        {name: 'Devices', url: '/devices', icon: <DeveloperBoardIcon/>},
        {name: 'Configure', url: '/configure', icon: <SettingsIcon/>},
    ];

    const authActions = isEmpty(user)
        ? [
            {name: 'Welcome', url: '/welcome', icon: <HomeIcon/>},
            {name: 'Sign In', url: '/signin', icon: <LoginIcon/>},
            {name: 'Sign Up', url: '/signup', icon: <PersonAddIcon/>},
        ]
        : [
            // { name: 'Logout', action: () => logout(), icon: <LogoutIcon /> },
        ];

    const renderListItem = (item) => (
        <ListItem key={item.name} disablePadding sx={{display: 'block'}}>
            {item.url ? (
                <ListItemButton
                    selected={location.pathname === item.url}
                    onClick={(event) => handleListItemClick(event, item.url)}
                    component={NavLink}
                    to={item.url}
                    sx={{
                        borderRadius: 2,
                        margin: 1,
                        px: 2.5,
                        justifyContent: open ? 'initial' : 'center',
                    }}
                >
                    <ListItemIcon sx={{minWidth: 0, justifyContent: 'center', mr: open ? 3 : 'auto'}}>
                        {item.icon}
                    </ListItemIcon>
                    <ListItemText primary={item.name} sx={{opacity: open ? 1 : 0}}/>
                </ListItemButton>
            ) : (
                <ListItemButton
                    onClick={item.action}
                    sx={{
                        borderRadius: 2,
                        margin: 1,
                        px: 2.5,
                        justifyContent: open ? 'initial' : 'center',
                    }}
                >
                    <ListItemIcon sx={{minWidth: 0, justifyContent: 'center', mr: open ? 3 : 'auto'}}>
                        {item.icon}
                    </ListItemIcon>
                    <ListItemText primary={item.name} sx={{opacity: open ? 1 : 0}}/>
                </ListItemButton>
            )}
        </ListItem>
    );


    const handleListItemClick = (event, index) => {
        // setSelectedIndex(index);
    };
    const handleDrawerOpen = () => {
        setOpen(true);
    };

    const handleDrawerClose = () => {
        setOpen(s => !s);
    };

    return (
        <Box sx={{display: 'flex', height:'100dvh', width:'100%', background:'transparent'}}>
            <CssBaseline/>


            {/*{!isEmpty(user) &&*/}
                <Drawer variant="permanent" open={open} elevation={4}
                        style={{
                            backgroundColor: 'rgba(0, 0, 0, 60%)',
                            backdropFilter: 'blur(7px)',
                            // background: "linear-gradient(135deg, rgb(255 224 43 / 10%), rgb(169 104 241 / 10%), rgb(90 200 250 / 10%))",
                            // boxShadow: "0 0 30px rgb(211 244 122 / 40%)",
                            // height: '96dvh',
                            // position:'absolute',
                            // zIndex:'1',
                            margin: '10px',
                            borderRadius: '10px'
                        }}>
                    <Box sx={{display: 'flex', flexDirection: 'column', height: '100%'}}>
                        <DrawerHeader>
                            <IconButton onClick={handleDrawerClose}>
                                {open ? <ChevronLeftIcon/> : <MenuIcon/>}
                            </IconButton>
                        </DrawerHeader>

                        <List sx={{flexGrow: 1}}>
                            {[...publicItems, ...(isEmpty(user) ? [] : authItems), ...authActions].map(renderListItem)}
                        </List>

                    {/* Avatar at the bottom */}
                    {!isEmpty(user) && (
                        <OptionsMenu drawerOpen={open}/>
                    )}

                </Box>

                    {/*<Divider/>*/}
                </Drawer>
            {/*}*/}

            <Box component="main" sx={{flexGrow: 1,}}>

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

                <AppCacheProvider>
                    <DeviceDataProvider>
                        <Routes>
                            {/*open*/}
                            <Route path="welcome" element={<Welcome/>}/>
                            <Route path="mob" element={<MobileView/>}/>
                            <Route path="exp" element={<Exp/>}/>
                            <Route path="spotify" element={<SpotifyPlayer/>}/>
                            <Route path="signup" element={<SignUp/>}/>
                            <Route path="signin" element={<SignIn/>}/>
                            {/*protected*/}
                            <Route path="/" element={<PrivateRoute element={<DeviceNodes/>} />} />
                            {/*<Route path="/" element={<PrivateRoute element={<DeviceNodes/>}/>}/>*/}
                            <Route path="analytics" element={<PrivateRoute element={<AnalyticsView/>}/>}/>
                            <Route path="actions" element={<PrivateRoute element={<ActionBoard/>}/>}/>
                            <Route path="exp" element={<PrivateRoute element={<Exp/>}/>}/>
                            <Route path="devices" element={<PrivateRoute element={<Devices/>}/>}/>
                            <Route path="configure" element={<PrivateRoute element={<ConfigurationView/>}/>}/>
                        </Routes>
                    </DeviceDataProvider>
                </AppCacheProvider>

                <SnackbarProvider maxSnack={3} preventDuplicate>
                    <Notifications/>
                </SnackbarProvider>
            </Box>
        </Box>
    );
}
