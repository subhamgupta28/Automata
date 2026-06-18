import React, { useState, useEffect, useCallback } from 'react';
import {
    Box, Button, Typography, Card, CardContent, Grid, CircularProgress, Modal, TextField, Chip, Tabs, Tab, List, ListItem, ListItemText, ListItemIcon, IconButton, Select, MenuItem, FormControl, InputLabel,
} from '@mui/material';
import { Home, User, Plus, Key, Trash2, X, Copy, Users, Settings, Mail } from 'lucide-react';
import { getMyHomes, createHome, joinHome, getHomeMembers, changeMemberRole, revokeAccess, createInvite, deleteHome } from '../../services/apis';

const style = {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: 500,
    bgcolor: 'background.paper',
    border: '2px solid #000',
    boxShadow: 24,
    p: 4,
};

function HomeManagement() {
    const [homes, setHomes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [openCreate, setOpenCreate] = useState(false);
    const [openJoin, setOpenJoin] = useState(false);
    const [openManage, setOpenManage] = useState(false);
    const [selectedHome, setSelectedHome] = useState(null);
    const [newHomeName, setNewHomeName] = useState('');
    const [joinToken, setJoinToken] = useState('');

    const fetchHomes = useCallback(async () => {
        try {
            setLoading(true);
            const myHomes = await getMyHomes();
            setHomes(myHomes);
        } catch (err) {
            setError('Failed to fetch homes.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchHomes();
    }, [fetchHomes]);

    const handleCreateHome = async () => {
        try {
            await createHome({ name: newHomeName, timezone: 'UTC' });
            setOpenCreate(false);
            fetchHomes();
        } catch (error) {
            console.error("Failed to create home", error);
        }
    };

    const handleJoinHome = async () => {
        try {
            await joinHome(joinToken);
            setOpenJoin(false);
            fetchHomes();
        } catch (error) {
            console.error("Failed to join home", error);
        }
    };

    const handleOpenManage = (home) => {
        setSelectedHome(home);
        setOpenManage(true);
    };

    const handleCloseManage = () => {
        setOpenManage(false);
        setSelectedHome(null);
    };

    if (loading) {
        return <CircularProgress />;
    }

    if (error) {
        return <Typography color="error">{error}</Typography>;
    }

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h4" gutterBottom>Home Management</Typography>
            <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item>
                    <Button variant="contained" startIcon={<Plus />} onClick={() => setOpenCreate(true)}>
                        Create Home
                    </Button>
                </Grid>
                <Grid item>
                    <Button variant="outlined" startIcon={<Key />} onClick={() => setOpenJoin(true)}>
                        Join Home
                    </Button>
                </Grid>
            </Grid>

            <Grid container spacing={3}>
                {homes.map((home) => (
                    <Grid item xs={12} sm={6} md={4} key={home.id}>
                        <Card>
                            <CardContent>
                                <Typography variant="h6">{home.name}</Typography>
                                <Chip icon={<User />} label={home.myRole} size="small" />
                                <Button onClick={() => handleOpenManage(home)} startIcon={<Settings />} sx={{ mt: 2 }}>
                                    Manage
                                </Button>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            <Modal open={openCreate} onClose={() => setOpenCreate(false)}>
                <Box sx={style}>
                    <Typography variant="h6" component="h2">Create a new home</Typography>
                    <TextField
                        autoFocus
                        margin="dense"
                        label="Home Name"
                        type="text"
                        fullWidth
                        variant="standard"
                        value={newHomeName}
                        onChange={(e) => setNewHomeName(e.target.value)}
                    />
                    <Button onClick={handleCreateHome} sx={{ mt: 2 }} variant="contained">Create</Button>
                </Box>
            </Modal>

            <Modal open={openJoin} onClose={() => setOpenJoin(false)}>
                <Box sx={style}>
                    <Typography variant="h6" component="h2">Join a home</Typography>
                    <TextField
                        autoFocus
                        margin="dense"
                        label="Invite Token"
                        type="text"
                        fullWidth
                        variant="standard"
                        value={joinToken}
                        onChange={(e) => setJoinToken(e.target.value)}
                    />
                    <Button onClick={handleJoinHome} sx={{ mt: 2 }} variant="contained">Join</Button>
                </Box>
            </Modal>

            {selectedHome && (
                <ManageHomeModal
                    home={selectedHome}
                    open={openManage}
                    onClose={handleCloseManage}
                    onHomesChange={fetchHomes}
                />
            )}
        </Box>
    );
}

function ManageHomeModal({ home, open, onClose, onHomesChange }) {
    const [tab, setTab] = useState(0);
    const [members, setMembers] = useState([]);
    const [invites, setInvites] = useState([]);
    const [loading, setLoading] = useState(false);

    const fetchMembers = useCallback(async () => {
        if (!home) return;
        setLoading(true);
        try {
            const homeMembers = await getHomeMembers(home.id);
            setMembers(homeMembers);
        } catch (error) {
            console.error("Failed to fetch members", error);
        } finally {
            setLoading(false);
        }
    }, [home]);

    useEffect(() => {
        if (open) {
            fetchMembers();
        }
    }, [open, fetchMembers]);

    const handleTabChange = (event, newValue) => {
        setTab(newValue);
    };

    const handleRoleChange = async (userId, newRole) => {
        try {
            await changeMemberRole(home.id, userId, newRole);
            fetchMembers();
        } catch (error) {
            console.error("Failed to change role", error);
        }
    };

    const handleRevokeAccess = async (userId) => {
        try {
            await revokeAccess(home.id, userId);
            fetchMembers();
        } catch (error) {
            console.error("Failed to revoke access", error);
        }
    };

    const handleDeleteHome = async () => {
        try {
            await deleteHome(home.id);
            onHomesChange();
            onClose();
        } catch (error) {
            console.error("Failed to delete home", error);
        }
    };

    return (
        <Modal open={open} onClose={onClose}>
            <Box sx={style}>
                <Typography variant="h6" component="h2">Manage {home.name}</Typography>
                <Tabs value={tab} onChange={handleTabChange}>
                    <Tab label="Members" />
                    <Tab label="Invites" />
                    <Tab label="Danger Zone" />
                </Tabs>
                {tab === 0 && (
                    <List>
                        {loading ? <CircularProgress /> : members.map((member) => (
                            <ListItem key={member.id}>
                                <ListItemText primary={member.name} secondary={member.myRole} />
                                <FormControl size="small" sx={{ m: 1, minWidth: 120 }}>
                                    <InputLabel>Role</InputLabel>
                                    <Select
                                        value={member.myRole}
                                        label="Role"
                                        onChange={(e) => handleRoleChange(member.id, e.target.value)}
                                    >
                                        <MenuItem value="ADMIN">Admin</MenuItem>
                                        <MenuItem value="MEMBER">Member</MenuItem>
                                    </Select>
                                </FormControl>
                                <IconButton onClick={() => handleRevokeAccess(member.id)}>
                                    <Trash2 />
                                </IconButton>
                            </ListItem>
                        ))}
                    </List>
                )}
                {tab === 2 && (
                    <Box sx={{ mt: 2 }}>
                        <Button variant="contained" color="error" onClick={handleDeleteHome}>
                            Delete Home
                        </Button>
                    </Box>
                )}
            </Box>
        </Modal>
    );
}

export default HomeManagement;