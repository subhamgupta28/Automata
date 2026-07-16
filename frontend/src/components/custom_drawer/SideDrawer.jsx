import * as React from 'react';
import {lazy, Suspense} from 'react';
import {styled} from '@mui/material/styles';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import CssBaseline from '@mui/material/CssBaseline';
import IconButton from '@mui/material/IconButton';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Tooltip from '@mui/material/Tooltip';
import Collapse from '@mui/material/Collapse';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import Divider from '@mui/material/Divider';
import {Link as RouterLink, NavLink, Route, Routes, useLocation} from "react-router-dom";
import {AppCacheProvider} from "../../services/AppCacheContext.jsx";
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import HomeIcon from '@mui/icons-material/Home';
import DeveloperBoardIcon from '@mui/icons-material/DeveloperBoard';
import SettingsIcon from '@mui/icons-material/Settings';
import AssessmentIcon from '@mui/icons-material/Assessment';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import Notifications from "../Notifications.jsx";
import {SnackbarProvider} from "notistack";
import {Card} from "@mui/material";
import SignUp from "../auth/SignUp.jsx";
import SignIn from "../auth/SignIn.jsx";
import PrivateRoute from "../auth/PrivateRoute.jsx";
import {useAuth} from "../auth/AuthContext.jsx";
import LoginIcon from '@mui/icons-material/Login';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import OptionsMenu from "./OptionsMenu.jsx";
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import isEmpty from "../../utils/Helper.jsx";
import {ReactFlowProvider} from "@xyflow/react";
import {Dashboard, GridView, TrendingUp} from "@mui/icons-material";
import AppIcon from "../../../public/icon-color.png"
import AutomationLiveInspector from "../automation/AutomationInspector.jsx";
import {ConfigurationView} from "../dashboard/ConfigurationView.jsx";
import Recordings from "../integrations/AutomataRecordings.jsx";
import {Bot, Disc3Icon, Map, StoreIcon} from "lucide-react";
import {MapDevices} from "../device_types/MapDevices.jsx";
import HomeManagement from "../home/HomeManagement.jsx";
import LoadingScreen from "../../utils/LoadingScreen.jsx"; // Import the new component

// Lazy-load heavy route components
const DeviceNodes = lazy(() => import("../home/DeviceNodes.jsx"));
const Devices = lazy(() => import("../Devices.jsx"));
const MobileView = lazy(() => import("../dashboard/MobileView.jsx"));
const AnalyticsView = lazy(() => import("../dashboard/AnalyticsView.jsx"));
const AutomationAnalyticsView = lazy(() => import("../automation/AutomationAnalyticsView.jsx"));
const ActionBoard = lazy(() => import("../action/ActionBoard.jsx"));
const Exp = lazy(() => import("../dashboard/Exp.jsx"));
const Welcome = lazy(() => import("../Welcome.jsx"));
const SpotifyPlayer = lazy(() => import("../integrations/SpotifyPlayer.jsx"));
const VirtualDeviceForm = lazy(() => import("../v2/VirtualDeviceForm.jsx"));
const DashboardV2 = lazy(() => import("../v2/DashboardV2.jsx"));
const Presentation = lazy(() => import("../demo/Presentation.jsx"));
const AdminLoginDashboard = lazy(() => import("../admin/AdminLoginDashboard.jsx"));

const PageLoader = () => (
    <Box sx={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%'}}>
        <LoadingScreen/>
    </Box>
);

const drawerWidth = 220;

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

// ─── Breadcrumb bar shown above the main content ────────────────────────────

const breadcrumbNameMap = {
    '/': 'Home',
    '/actions': 'Automations',
    '/automation-analytics': 'Automation Analytics',
    '/analytics': 'Analytics',
    '/virtual': 'Virtual Device',
    '/dashboard': 'Dashboard',
    '/devices': 'Devices',
    '/map': 'Map',
    '/recording': 'Recording',
    '/configure': 'Configure',
    '/admin/login-analytics': 'Admin Panel',
    '/home-management': 'Home Management',
};

function LinkRouter(props) {
    return <Link {...props} component={RouterLink}/>;
}


// ─── Nav group label (only shown when drawer is open) ───────────────────────

function GroupLabel({label, open}) {
    if (!open) return (
        <Divider sx={{my: 0.5, mx: 1, borderColor: 'rgba(255,255,255,0.08)'}}/>
    );
    return (
        <Box sx={{px: 2.5, pt: 1.5, pb: 0.25}}>
            <Typography variant="caption"
                        sx={{
                            color: 'rgba(255,255,255,0.35)',
                            fontWeight: 700,
                            letterSpacing: '0.08em',
                            textTransform: 'uppercase',
                            fontSize: '0.65rem'
                        }}>
                {label}
            </Typography>
        </Box>
    );
}

// ─── Collapsible group component ────────────────────────────────────────────

function NavGroup({group, drawerOpen, location}) {
    const [expanded, setExpanded] = React.useState(true);

    // Auto-expand group if a child is active
    const hasActiveChild = group.children.some(item => location.pathname === item.url);

    React.useEffect(() => {
        if (hasActiveChild) setExpanded(true);
    }, [hasActiveChild]);

    const renderItem = (item, nested = false) => (
        <ListItem key={item.name} disablePadding sx={{display: 'block'}}>
            <Tooltip
                title={item.name}
                placement="right"
                disableHoverListener={drawerOpen}
                slotProps={{
                    tooltip: {
                        sx: {
                            fontSize: '1rem',
                            color: '#f6e07c',
                            backgroundColor: 'rgb(255 255 255 / 10%)',
                            backdropFilter: 'blur(3px)'
                        }
                    }
                }}
            >
                <ListItemButton
                    selected={location.pathname === item.url}
                    component={NavLink}
                    to={item.url}
                    sx={{
                        borderRadius: 2,
                        mx: 1,
                        my: 0.25,
                        px: nested && drawerOpen ? 3.5 : 2.5,
                        justifyContent: drawerOpen ? 'initial' : 'center',
                        minHeight: 40,
                        '&.active, &.Mui-selected': {
                            // backgroundColor: 'rgb(240 219 120 / 0.12)',
                            '& .MuiListItemIcon-root': {color: '#f0dc7a'},
                            '& .MuiListItemText-primary': {color: '#f0dc7b', fontWeight: 600},
                        },
                    }}
                >
                    <ListItemIcon
                        sx={{minWidth: 0, justifyContent: 'center', mr: drawerOpen ? 2 : 'auto', fontSize: 20}}>
                        {item.icon}
                    </ListItemIcon>
                    <ListItemText
                        primary={item.name}
                        sx={{
                            opacity: drawerOpen ? 1 : 0,
                            '& .MuiListItemText-primary': {fontSize: '0.85rem'}
                        }}
                    />
                </ListItemButton>
            </Tooltip>
        </ListItem>
    );

    // If the group has no label it's a flat section, just render children
    if (!group.label) {
        return <>{group.children.map(item => renderItem(item))}</>;
    }

    return (
        <>
            <GroupLabel label={group.label} open={drawerOpen}/>

            {/* Group header button (acts as toggle when drawer is open) */}
            {group.headerItem && drawerOpen && (
                <ListItem disablePadding sx={{display: 'block'}}>
                    <ListItemButton
                        onClick={() => setExpanded(s => !s)}
                        sx={{
                            borderRadius: 2,
                            mx: 1,
                            my: 0.25,
                            px: 2.5,
                            minHeight: 40,
                            justifyContent: 'initial',
                        }}
                    >
                        <ListItemIcon sx={{minWidth: 0, mr: 2, justifyContent: 'center'}}>
                            {group.headerItem.icon}
                        </ListItemIcon>
                        <ListItemText
                            primary={group.headerItem.name}
                            sx={{'& .MuiListItemText-primary': {fontSize: '0.85rem', fontWeight: 600}}}
                        />
                        {expanded ? <ExpandLess sx={{fontSize: 16, color: 'rgba(255,255,255,0.4)'}}/> :
                            <ExpandMore sx={{fontSize: 16, color: 'rgba(255,255,255,0.4)'}}/>}
                    </ListItemButton>
                </ListItem>
            )}

            {/* Collapsible children */}
            {drawerOpen ? (
                <Collapse in={expanded} timeout="auto" unmountOnExit component="li"
                          style={{listStyle: 'none', padding: 0}}>
                    <List disablePadding>
                        {group.children.map(item => renderItem(item, true))}
                    </List>
                </Collapse>
            ) : (
                // When drawer is closed, always show icons (no collapse)
                <>{group.children.map(item => renderItem(item))}</>
            )}
        </>
    );
}

// ─── Main SideDrawer ─────────────────────────────────────────────────────────

export default function SideDrawer() {
    const [open, setOpen] = React.useState(false);
    const {user, logout, isGuest} = useAuth();
    const location = useLocation();

    // ── Nav group definitions ──────────────────────────────────────────────
    const authGroups = [
        {
            label: null,        // no label = flat, no collapse
            children: [
                {name: 'Home', url: '/', icon: <HomeIcon/>},
            ]
        },
        {
            label: 'Automation',
            headerItem: {name: 'Automations', icon: <AutoAwesomeIcon/>},
            children: [
                {name: 'Automations', url: '/actions', icon: <Bot/>},
                {name: 'Live Inspector', url: '/automation-analytics', icon: <TrendingUp/>},
            ]
        },
        {
            label: 'Analytics',
            headerItem: {name: 'Analytics', icon: <AssessmentIcon/>},
            children: [
                {name: 'Analytics', url: '/analytics', icon: <AssessmentIcon/>},
            ]
        },
        {
            label: 'Devices',
            headerItem: {name: 'Devices', icon: <DeveloperBoardIcon/>},
            children: [
                {name: 'Virtual Device', url: '/virtual', icon: <Dashboard/>},
                {name: 'Dashboard', url: '/dashboard', icon: <GridView/>},
                {name: 'Devices', url: '/devices', icon: <DeveloperBoardIcon/>},
                {name: 'Map', url: '/map', icon: <Map/>},
                {name: 'Recording', url: '/recording', icon: <Disc3Icon/>},
            ]
        },
        {
            label: 'System',
            children: [
                {name: 'Home Management', url: '/home-management', icon: <StoreIcon/>},
                {name: 'Configure', url: '/configure', icon: <SettingsIcon/>},
                ...(user?.role?.toUpperCase() === 'ADMIN' ? [{
                    name: 'Admin Panel',
                    url: '/admin/login-analytics',
                    icon: <AdminPanelSettingsIcon/>
                }] : []),
            ]
        },
    ];

    const guestGroups = [
        {
            label: null,
            children: [
                {name: 'Home', url: '/', icon: <HomeIcon/>},
                {name: 'Automations', url: '/actions', icon: <AutoAwesomeIcon/>},
                {name: 'Analytics', url: '/analytics', icon: <AssessmentIcon/>},
                {name: 'Dashboard', url: '/dashboard', icon: <GridView/>},
                {name: 'Live Inspector', url: '/automation-analytics', icon: <TrendingUp/>},
            ]
        }
    ];

    const authActions = isEmpty(user)
        ? [
            {
                label: null,
                children: [
                    {name: 'Welcome', url: '/welcome', icon: <HomeIcon/>},
                    {name: 'Sign In', url: '/signin', icon: <LoginIcon/>},
                    {name: 'Sign Up', url: '/signup', icon: <PersonAddIcon/>},
                ]
            }
        ]
        : [];

    const activeGroups = isEmpty(user)
        ? authActions
        : (isGuest ? guestGroups : authGroups);

    const handleDrawerClose = () => setOpen(s => !s);

    return (
        <Box sx={{display: 'flex', height: '100dvh', width: '100%', background: 'transparent'}}>
            <CssBaseline/>

            {!isEmpty(user) && (
                <Drawer
                    variant="permanent"
                    open={open}
                    elevation={4}
                    style={{
                        backdropFilter: 'blur(7px)',
                        background: 'transparent',
                        position: 'relative',
                        zIndex: 10,
                        margin: '10px',
                        borderRadius: '10px'
                    }}
                >
                    <Box sx={{display: 'flex', flexDirection: 'column', height: '100%'}}>
                        {/* Header / logo toggle */}
                        <DrawerHeader>
                            <IconButton onClick={handleDrawerClose}>
                                {open
                                    ? <ChevronLeftIcon/>
                                    : <img src={AppIcon} alt="home" style={{height: '28px'}}/>
                                }
                            </IconButton>
                        </DrawerHeader>

                        {/* Nav groups */}
                        <List
                            component="nav"
                            disablePadding
                            sx={{flexGrow: 1, overflowY: 'auto', overflowX: 'hidden', py: 1}}
                        >
                            {activeGroups.map((group, i) => (
                                <NavGroup
                                    key={i}
                                    group={group}
                                    drawerOpen={open}
                                    location={location}
                                />
                            ))}
                        </List>

                        {/* User avatar / options at the bottom */}
                        {!isEmpty(user) && <OptionsMenu drawerOpen={open}/>}
                    </Box>
                </Drawer>
            )}

            <Box component="main" sx={{flexGrow: 1, height: '100dvh', display: 'flex', flexDirection: 'column'}}>
                {/* Breadcrumb bar */}
                {/*{!isEmpty(user) && <AppBreadcrumbs/>}*/}

                <Box sx={{flexGrow: 1, overflow: 'auto'}}>
                    <AppCacheProvider>
                        <ReactFlowProvider>
                            <SnackbarProvider maxSnack={3} preventDuplicate>
                                <Suspense fallback={<PageLoader/>}>
                                    <Routes location={location} key={location.pathname}>
                                        {/* Public */}
                                        <Route path="/welcome" element={<Welcome/>}/>
                                        <Route path="/mob" element={<MobileView/>}/>
                                        <Route path="/exp" element={<Exp/>}/>
                                        <Route path="spotify" element={<SpotifyPlayer/>}/>
                                        <Route path="signup" element={<SignUp/>}/>
                                        <Route path="signin" element={<SignIn/>}/>
                                        {/* Protected */}
                                        <Route index element={<PrivateRoute path="/" element={<DashboardV2/>}/>}/>
                                        <Route path="analytics"
                                               element={<PrivateRoute path="/analytics" element={<AnalyticsView/>}/>}/>
                                        <Route path="automation-analytics"
                                               element={<PrivateRoute path="/automation-analytics"
                                                                      element={<AutomationLiveInspector/>}/>}/>
                                        <Route path="presentation"
                                               element={<PrivateRoute path="/presentation"
                                                                      element={<Presentation/>}/>}/>
                                        <Route path="virtual"
                                               element={<PrivateRoute path="/virtual"
                                                                      element={<VirtualDeviceForm/>}/>}/>
                                        <Route path="dashboard"
                                               element={<PrivateRoute path="/dashboard" element={<DeviceNodes/>}/>}/>
                                        <Route path="actions"
                                               element={<PrivateRoute path="/actions" element={<ActionBoard/>}/>}/>
                                        <Route path="exp" element={<PrivateRoute path="/exp" element={<Exp/>}/>}/>
                                        <Route path="devices"
                                               element={<PrivateRoute path="/devices" element={<Devices/>}/>}/>
                                        <Route path="recording"
                                               element={<PrivateRoute path="/recording" element={<Recordings/>}/>}/>
                                        <Route path="map"
                                               element={<PrivateRoute path="/map" element={<MapDevices/>}/>}/>
                                        <Route path="configure"
                                               element={<PrivateRoute path="/configure"
                                                                      element={<ConfigurationView/>}/>}/>
                                        <Route path="home-management"
                                               element={<PrivateRoute path="/home-management"
                                                                      element={<HomeManagement/>}/>}/>
                                        <Route path="admin/login-analytics"
                                               element={<PrivateRoute path="/admin/login-analytics"
                                                                      element={<AdminLoginDashboard/>}
                                                                      requiredRole="ADMIN"/>}/>
                                    </Routes>
                                </Suspense>
                                {!isEmpty(user) && (
                                    <Notifications/>
                                )}
                            </SnackbarProvider>
                        </ReactFlowProvider>
                    </AppCacheProvider>
                </Box>


            </Box>
        </Box>
    );
}