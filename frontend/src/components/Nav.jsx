import * as React from 'react';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import AdbIcon from '@mui/icons-material/Adb';
import {useEffect} from "react";
import {getDevices, getNotifications, getServerTime} from "../services/apis.jsx";
import {NavLink} from "react-router-dom";
import {Alert, Menu, MenuItem} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import MenuIcon from '@mui/icons-material/NotificationImportant';

// const pages = ['Home', 'Actions'];
const settings = ['Profile', 'Account', 'Dashboard', 'Logout'];

function Nav() {
    const [anchorElNav, setAnchorElNav] = React.useState(null);
    const [anchorElUser, setAnchorElUser] = React.useState(null);
    const [notifications, setNotifications] = React.useState([]);
    const [time, setTime] = React.useState(Date());
    const date = new Date();
    const formattedDateTime = date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: true // Use 12-hour format with AM/PM
    });


    useEffect(() => {
        // setTime(formattedDateTime)
        const fetchData = async () => {
            const t = await getNotifications();
            console.log("notifications", t);
            setNotifications(t);
        }

        fetchData();

    }, [anchorElUser])

    const handleOpenNavMenu = (event) => {
        setAnchorElNav(event.currentTarget);
    };
    const handleOpenUserMenu = (event) => {
        setAnchorElUser(event.currentTarget);
    };

    const handleCloseNavMenu = () => {
        setAnchorElNav(null);
    };

    const handleCloseUserMenu = () => {
        setAnchorElUser(null);
    };



    return (
        <AppBar  elevation={0} style={{backgroundColor:'transparent', backdropFilter: 'blur(1px)'}}>
            <Container maxWidth="xl">
                <Toolbar disableGutters variant="dense" >
                    <AdbIcon sx={{display: {xs: 'none', md: 'flex'}, mr: 1}}/>
                    <Typography
                        variant="h6"
                        noWrap
                        component="a"
                        href="/"
                        sx={{
                            mr: 2,
                            display: {xs: 'none', md: 'flex'},
                            fontFamily: 'monospace',
                            fontWeight: 700,
                            letterSpacing: '.3rem',
                            color: 'inherit',
                            textDecoration: 'none',
                        }}
                    >
                        Automata
                    </Typography>
                    <AdbIcon sx={{display: {xs: 'flex', md: 'none'}, mr: 1}}/>
                    <Typography
                        variant="h5"
                        noWrap
                        component="a"
                        href="#app-bar-with-responsive-menu"
                        sx={{
                            mr: 2,
                            display: {xs: 'flex', md: 'none'},
                            flexGrow: 1,
                            fontFamily: 'monospace',
                            fontWeight: 700,
                            letterSpacing: '.3rem',
                            color: 'inherit',
                            textDecoration: 'none',
                        }}
                    >
                        Automata
                    </Typography>
                    <Box sx={{flexGrow: 1, display: {xs: 'none', md: 'flex'}}}>
                        <NavLink style={{color: 'white', display: 'block'}} to="/" end>
                            Home
                        </NavLink>
                        <NavLink style={{marginLeft:'14px', color: 'white', display: 'block'}} to="/actions" end>
                            Actions
                        </NavLink>
                        <NavLink style={{marginLeft:'14px', color: 'white', display: 'block'}} to="/devices" end>
                            Devices
                        </NavLink>
                    </Box>
                    <Box>
                        {/*<Typography style={{marginRight: "10px"}}*/}
                        {/*>*/}
                        {/*    {time}*/}
                        {/*</Typography>*/}
                        <IconButton
                            size="large"
                            aria-label="account of current user"
                            aria-controls="menu-appbar"
                            aria-haspopup="true"
                            onClick={handleOpenUserMenu}
                            color="inherit"
                        >
                            <MenuIcon />
                        </IconButton>
                        <Menu
                            sx={{ mt: '45px' }}
                            id="menu-appbar"
                            anchorEl={anchorElUser}
                            anchorOrigin={{
                                vertical: 'top',
                                horizontal: 'right',
                            }}
                            keepMounted
                            transformOrigin={{
                                vertical: 'top',
                                horizontal: 'right',
                            }}
                            open={Boolean(anchorElUser)}
                            onClose={handleCloseUserMenu}
                        >
                            {notifications.map((setting) => (
                                <MenuItem key={setting.id} onClick={handleCloseUserMenu}>
                                    {setting.message}
                                </MenuItem>
                            ))}
                        </Menu>
                    </Box>

                </Toolbar>
            </Container>
        </AppBar>
    );
}

export default Nav;
