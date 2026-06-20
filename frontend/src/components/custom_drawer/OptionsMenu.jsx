import * as React from 'react';
import {styled} from '@mui/material/styles';
import Divider from '@mui/material/Divider';
import Menu from '@mui/material/Menu';
import MuiMenuItem from '@mui/material/MenuItem';
import {paperClasses} from '@mui/material/Paper';
import {listClasses} from '@mui/material/List';
import ListItemText from '@mui/material/ListItemText';
import ListItemIcon, {listItemIconClasses} from '@mui/material/ListItemIcon';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import MenuButton from './MenuButton';
import Box from "@mui/material/Box";
import Avatar from "@mui/material/Avatar";
import {useAuth} from "../auth/AuthContext.jsx";
import isEmpty from "../../utils/Helper.jsx";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import {useHome} from "../home/HomeContext.jsx"; // Import useHome
import {Check, Home} from 'lucide-react';
import {dividerClasses} from "@mui/material";

const MenuItem = styled(MuiMenuItem)({
    margin: '2px 0',
});

export default function OptionsMenu({drawerOpen}) {
    const [anchorEl, setAnchorEl] = React.useState(null);
    const open = Boolean(anchorEl);
    const {user, logout, isGuest} = useAuth();
    const {homes, selectedHome, selectHome, loading: homesLoading} = useHome(); // Use HomeContext

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    const handleClose = () => {
        setAnchorEl(null);
    };
    const handleLogout = () => {
        logout();
        setAnchorEl(null);
    };

    const handleSelectHome = (homeId) => {
        selectHome(homeId);
        handleClose();
    };

    return (
        <div style={{
            display: 'flex',
            justifyContent: drawerOpen ? 'initial' : 'center',
            alignItems: 'center',
            margin: '10px'
        }}>
            <MenuButton
                aria-label="Open menu"
                onClick={handleClick}
                sx={{
                    borderColor: 'transparent',
                    display: 'flex',
                }}
            >
                <Avatar sx={{
                    backgroundColor: isGuest ? '#ff9800' : 'primary.main'
                }}>
                    {!isEmpty(user) && (isGuest ? 'G' : user?.firstName?.[0]?.toUpperCase())}
                </Avatar>
            </MenuButton>
            {drawerOpen && (
                <Box sx={{
                    width: '100%',
                    marginLeft: '10px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'flex-start',
                    gap: 0.5
                }}>
                    <Typography variant="body2" fontWeight={600}>
                        {isGuest ? 'Guest User' : user?.firstName}
                    </Typography>
                    {selectedHome && (
                        <Chip
                            icon={<Home size={12}/>}
                            label={selectedHome.name}
                            size="small"
                            variant="outlined"
                            sx={{height: 20}}
                        />
                    )}
                </Box>
            )}
            <Menu
                anchorEl={anchorEl}
                id="menu"
                open={open}
                onClose={handleClose}
                transformOrigin={{horizontal: 'right', vertical: 'top'}}
                anchorOrigin={{horizontal: 'right', vertical: 'bottom'}}
                sx={{
                    [`& .${listClasses.root}`]: {
                        padding: '4px',
                    },
                    [`& .${paperClasses.root}`]: {
                        padding: 0,
                        minWidth: 180,
                    },
                    [`& .${dividerClasses.root}`]: {
                        margin: '4px -4px',
                    },
                }}
            >
                <Box sx={{px: 2, py: 1}}>
                    <Typography variant="caption" color="text.secondary">Signed in as</Typography>
                    <Typography variant="body2" fontWeight={600}>{user?.email}</Typography>
                </Box>
                <Divider/>

                {/* Home selection */}
                {!isGuest && homes.length > 1 && (
                    <>
                        <Box sx={{px: 2, pt: 1, pb: 0.5}}>
                            <Typography variant="caption" color="text.secondary">Switch Home</Typography>
                        </Box>
                        {homes.map((home) => (
                            <MenuItem key={home.id} onClick={() => handleSelectHome(home.id)}>
                                <ListItemIcon>
                                    <Home size={16}/>
                                </ListItemIcon>
                                <ListItemText>{home.name}</ListItemText>
                                {selectedHome?.id === home.id && (
                                    <Check size={16} style={{marginLeft: 'auto'}}/>
                                )}
                            </MenuItem>
                        ))}
                        <Divider/>
                    </>
                )}

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
                        <LogoutRoundedIcon fontSize="small"/>
                    </ListItemIcon>
                </MenuItem>
            </Menu>
        </div>
    );
}