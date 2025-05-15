import * as React from 'react';
import { styled } from '@mui/material/styles';
import Divider, { dividerClasses } from '@mui/material/Divider';
import Menu from '@mui/material/Menu';
import MuiMenuItem from '@mui/material/MenuItem';
import { paperClasses } from '@mui/material/Paper';
import { listClasses } from '@mui/material/List';
import ListItemText from '@mui/material/ListItemText';
import ListItemIcon, { listItemIconClasses } from '@mui/material/ListItemIcon';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import MoreVertRoundedIcon from '@mui/icons-material/MoreVertRounded';
import MenuButton from './MenuButton';
import Box from "@mui/material/Box";
import Avatar from "@mui/material/Avatar";
import {useAuth} from "../auth/AuthContext.jsx";
import isEmpty from "../../utils/Helper.jsx";

const MenuItem = styled(MuiMenuItem)({
    margin: '2px 0',
});

export default function OptionsMenu() {
    const [anchorEl, setAnchorEl] = React.useState(null);
    const open = Boolean(anchorEl);
    const { user, logout } = useAuth();
    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    const handleClose = (e) => {
        setAnchorEl(null);
    };
    const handleLogout = (e) => {
        logout()
        setAnchorEl(null);
    };
    return (
        <div style={{display:'flex',  justifyContent:'center', margin:'10px'}}>
            <MenuButton
                aria-label="Open menu"
                onClick={handleClick}
                sx={{ borderColor: 'transparent',
                    display: 'flex',
                }}
            >
                <Avatar>{!isEmpty(user) && user?.firstName[0]?.toUpperCase()}</Avatar>
            </MenuButton>
            <Menu
                anchorEl={anchorEl}
                id="menu"
                open={open}
                onClose={handleClose}
                onClick={handleClose}
                transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                sx={{
                    [`& .${listClasses.root}`]: {
                        padding: '4px',
                    },
                    [`& .${paperClasses.root}`]: {
                        padding: 0,
                    },
                    [`& .${dividerClasses.root}`]: {
                        margin: '4px -4px',
                    },
                }}
            >
                <MenuItem onClick={handleClose}>More Coming Soon</MenuItem>
                {/*<MenuItem onClick={handleClose}>My account</MenuItem>*/}
                <Divider />
                {/*<MenuItem onClick={handleClose}>Add another account</MenuItem>*/}
                {/*<MenuItem onClick={handleClose}>Settings</MenuItem>*/}
                <Divider />
                <MenuItem
                    onClick={handleLogout}
                    id="logout"
                    sx={{
                        [`& .${listItemIconClasses.root}`]: {
                            ml: 'auto',
                            minWidth: 0,
                        },
                    }}
                >
                    <ListItemText>Logout</ListItemText>
                    <ListItemIcon>
                        <LogoutRoundedIcon fontSize="small" />
                    </ListItemIcon>
                </MenuItem>
            </Menu>
        </div>
    );
}