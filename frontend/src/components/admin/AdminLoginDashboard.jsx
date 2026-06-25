import React, {useEffect, useState} from 'react';
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CardHeader,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Grid,
    MenuItem,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TablePagination,
    TableRow,
    TextField,
    Typography,
    useTheme,
} from '@mui/material';
import {
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    Line,
    LineChart,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import {getLoginAnalytics} from '../../services/apis';
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import LoadingScreen from "../../utils/LoadingScreen.jsx";

dayjs.extend(relativeTime)

const COLORS = ['#00C49F', '#FFBB28', '#FF8042', '#0088FE', '#82CA9D', '#FFC658'];

export default function AdminLoginDashboard() {
    const theme = useTheme();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [stats, setStats] = useState(null);
    const [recentLogins, setRecentLogins] = useState([]);
    const [summary, setSummary] = useState(null);
    const [selectedUser, setSelectedUser] = useState(null);
    const [userLoginHistory, setUserLoginHistory] = useState([]);
    const [openUserDialog, setOpenUserDialog] = useState(false);
    const [pageRecent, setPageRecent] = useState(0);
    const [rowsPerPageRecent, setRowsPerPageRecent] = useState(5);
    const [pageUser, setPageUser] = useState(0);
    const [rowsPerPageUser, setRowsPerPageUser] = useState(5);
    const [hoursFilter, setHoursFilter] = useState(24);

    // Fetch all data on component mount
    useEffect(() => {
        fetchAllData();
    }, [hoursFilter]);

    const fetchAllData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [statsRes, recentRes, summaryRes] = await Promise.all([
                getLoginAnalytics('stats'),
                getLoginAnalytics(`recent?hours=${hoursFilter}`),
                getLoginAnalytics('summary'),
            ]);

            setStats(statsRes.data);
            setRecentLogins(recentRes.data);
            setSummary(summaryRes.data);
        } catch (err) {
            setError(err.message || 'Failed to load dashboard data');
            console.error('Error loading analytics:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleViewUserHistory = async (userEmail) => {
        try {
            const res = await getLoginAnalytics(`user/${userEmail}`);
            const userStat = stats.find((s) => s.email === userEmail);
            setSelectedUser(userStat);
            setUserLoginHistory(res.data);
            setOpenUserDialog(true);
        } catch (err) {
            setError(`Failed to load history for ${userEmail}`);
        }
    };

    const handleCloseUserDialog = () => {
        setOpenUserDialog(false);
        setSelectedUser(null);
        setUserLoginHistory([]);
        setPageUser(0);
    };

    const handleChangePageRecent = (event, newPage) => {
        setPageRecent(newPage);
    };

    const handleChangeRowsPerPageRecent = (event) => {
        setRowsPerPageRecent(parseInt(event.target.value, 10));
        setPageRecent(0);
    };

    const handleChangePageUser = (event, newPage) => {
        setPageUser(newPage);
    };

    const handleChangeRowsPerPageUser = (event) => {
        setRowsPerPageUser(parseInt(event.target.value, 10));
        setPageUser(0);
    };

    if (loading) {
        return (
            <Box sx={{display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh'}}>
                <LoadingScreen/>
            </Box>
        );
    }

    if (error) {
        return (
            <Box sx={{p: 2}}>
                <Alert severity="error">{error}</Alert>
                <Button onClick={fetchAllData} sx={{mt: 2}} variant="contained">
                    Retry
                </Button>
            </Box>
        );
    }

    // Prepare data for charts
    const loginTrendData = recentLogins.reduce((acc, login) => {
        const date = dayjs(login.loginTime).format('MMM dd HH:mm');
        const existingEntry = acc.find((e) => e.date === date);
        if (existingEntry) {
            existingEntry.count += 1;
        } else {
            acc.push({date, count: 1});
        }
        return acc;
    }, []);

    const browserData = recentLogins.reduce((acc, login) => {
        const browser = login.browser || 'Unknown';
        const existingEntry = acc.find((e) => e.name === browser);
        if (existingEntry) {
            existingEntry.value += 1;
        } else {
            acc.push({name: browser, value: 1});
        }
        return acc;
    }, []);

    const osData = recentLogins.reduce((acc, login) => {
        const os = login.operatingSystem || 'Unknown';
        const existingEntry = acc.find((e) => e.name === os);
        if (existingEntry) {
            existingEntry.value += 1;
        } else {
            acc.push({name: os, value: 1});
        }
        return acc;
    }, []);

    return (
        <Box sx={{p: 2, background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)'}}>
            <Typography variant="h4" sx={{mb: 3, color: '#ffd821', fontWeight: 'bold'}}>
                🔐 Admin Login Analytics Dashboard
            </Typography>

            {/* Summary Cards */}
            {summary && (
                <Grid container spacing={2} sx={{mb: 3}}>
                    <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white'}}>
                            <CardContent>
                                <Typography color="inherit" gutterBottom>
                                    Total Users
                                </Typography>
                                <Typography variant="h4" sx={{fontWeight: 'bold'}}>
                                    {summary.totalUsers}
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', color: 'white'}}>
                            <CardContent>
                                <Typography color="inherit" gutterBottom>
                                    Total Logins
                                </Typography>
                                <Typography variant="h4" sx={{fontWeight: 'bold'}}>
                                    {summary.totalLogins}
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{background: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', color: 'white'}}>
                            <CardContent>
                                <Typography color="inherit" gutterBottom>
                                    Successful
                                </Typography>
                                <Typography variant="h4" sx={{fontWeight: 'bold'}}>
                                    {summary.successfulLogins}
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                        <Card sx={{background: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)', color: 'white'}}>
                            <CardContent>
                                <Typography color="inherit" gutterBottom>
                                    Failed
                                </Typography>
                                <Typography variant="h4" sx={{fontWeight: 'bold'}}>
                                    {summary.failedLogins}
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            {/* Filters */}
            <Card sx={{mb: 3, background: '#0f3460', border: '1px solid #e94560'}}>
                <CardContent>
                    <TextField
                        select
                        label="Time Range"
                        value={hoursFilter}
                        onChange={(e) => setHoursFilter(e.target.value)}
                        variant="outlined"
                        sx={{
                            width: 200,
                            '& .MuiOutlinedInput-root': {
                                color: '#fff',
                                '& fieldset': {borderColor: '#e94560'},
                                '&:hover fieldset': {borderColor: '#ffd821'},
                            },
                            '& .MuiInputBase-input::placeholder': {color: '#999'},
                        }}
                    >
                        <MenuItem value={1}>Last 1 hour</MenuItem>
                        <MenuItem value={6}>Last 6 hours</MenuItem>
                        <MenuItem value={24}>Last 24 hours</MenuItem>
                        <MenuItem value={168}>Last 7 days</MenuItem>
                        <MenuItem value={720}>Last 30 days</MenuItem>
                    </TextField>
                </CardContent>
            </Card>

            {/* Charts */}
            <Grid container spacing={2} sx={{mb: 3}}>
                <Grid item xs={12} md={6}>
                    <Card sx={{background: '#0f3460', border: '1px solid #e94560'}}>
                        <CardHeader title="Login Trends" sx={{color: '#ffd821'}}/>
                        <CardContent>
                            <ResponsiveContainer width="100%" height={300}>
                                <LineChart data={loginTrendData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#444"/>
                                    <XAxis dataKey="date" stroke="#999"/>
                                    <YAxis stroke="#999"/>
                                    <Tooltip contentStyle={{background: '#16213e', border: '1px solid #e94560'}}/>
                                    <Line type="monotone" dataKey="count" stroke="#00C49F" strokeWidth={2}/>
                                </LineChart>
                            </ResponsiveContainer>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12} md={6}>
                    <Card sx={{background: '#0f3460', border: '1px solid #e94560'}}>
                        <CardHeader title="Browser Distribution" sx={{color: '#ffd821'}}/>
                        <CardContent>
                            <ResponsiveContainer width="100%" height={300}>
                                <PieChart>
                                    <Pie
                                        data={browserData}
                                        cx="50%"
                                        cy="50%"
                                        labelLine={false}
                                        label={({name, percent}) => `${name} ${(percent * 100).toFixed(0)}%`}
                                        outerRadius={80}
                                        fill="#8884d8"
                                        dataKey="value"
                                    >
                                        {browserData.map((entry, index) => (
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]}/>
                                        ))}
                                    </Pie>
                                    <Tooltip/>
                                </PieChart>
                            </ResponsiveContainer>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid item xs={12}>
                    <Card sx={{background: '#0f3460', border: '1px solid #e94560'}}>
                        <CardHeader title="Operating System Distribution" sx={{color: '#ffd821'}}/>
                        <CardContent>
                            <ResponsiveContainer width="100%" height={300}>
                                <BarChart data={osData}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#444"/>
                                    <XAxis dataKey="name" stroke="#999"/>
                                    <YAxis stroke="#999"/>
                                    <Tooltip contentStyle={{background: '#16213e', border: '1px solid #e94560'}}/>
                                    <Bar dataKey="value" fill="#82CA9D"/>
                                </BarChart>
                            </ResponsiveContainer>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>

            {/* User Statistics Table */}
            <Card sx={{mb: 3, background: '#0f3460', border: '1px solid #e94560'}}>
                <CardHeader title="User Login Statistics" sx={{color: '#ffd821'}}/>
                <TableContainer>
                    <Table>
                        <TableHead sx={{background: '#16213e'}}>
                            <TableRow>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>User</TableCell>
                                <TableCell align="right" sx={{color: '#ffd821', fontWeight: 'bold'}}>
                                    Total Logins
                                </TableCell>
                                <TableCell align="right" sx={{color: '#ffd821', fontWeight: 'bold'}}>
                                    Success
                                </TableCell>
                                <TableCell align="right" sx={{color: '#ffd821', fontWeight: 'bold'}}>
                                    Failed
                                </TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Last Login</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Unique IPs</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Action</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {stats &&
                                stats.map((stat) => (
                                    <TableRow key={stat.email} sx={{'&:hover': {background: '#1a1a2e'}}}>
                                        <TableCell sx={{color: '#fff'}}>
                                            <div>
                                                <strong>{stat.firstName} {stat.lastName}</strong>
                                                <br/>
                                                <small style={{color: '#999'}}>{stat.email}</small>
                                            </div>
                                        </TableCell>
                                        <TableCell align="right" sx={{color: '#00C49F', fontWeight: 'bold'}}>
                                            {stat.totalLogins}
                                        </TableCell>
                                        <TableCell align="right" sx={{color: '#82CA9D'}}>
                                            {stat.successfulLogins}
                                        </TableCell>
                                        <TableCell align="right" sx={{color: '#FF8042'}}>
                                            {stat.failedLogins}
                                        </TableCell>
                                        <TableCell sx={{color: '#fff'}}>
                                            {stat.lastLogin ? dayjs(stat.lastLogin).format('MMM dd, yyyy HH:mm') : 'N/A'}
                                        </TableCell>
                                        <TableCell sx={{color: '#fff'}}>
                                            {stat.uniqueIps ? stat.uniqueIps.length : 0}
                                        </TableCell>
                                        <TableCell>
                                            <Button
                                                size="small"
                                                variant="outlined"
                                                onClick={() => handleViewUserHistory(stat.email)}
                                                sx={{
                                                    color: '#ffd821',
                                                    borderColor: '#ffd821',
                                                    '&:hover': {background: '#ffd821', color: '#000'},
                                                }}
                                            >
                                                View History
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Card>

            {/* Recent Logins Table */}
            <Card sx={{background: '#0f3460', border: '1px solid #e94560'}}>
                <CardHeader title="Recent Logins" sx={{color: '#ffd821'}}/>
                <TableContainer>
                    <Table>
                        <TableHead sx={{background: '#16213e'}}>
                            <TableRow>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>User</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Login Time</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>IP Address</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Browser</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>OS</TableCell>
                                <TableCell sx={{color: '#ffd821', fontWeight: 'bold'}}>Status</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {recentLogins
                                .slice(pageRecent * rowsPerPageRecent, pageRecent * rowsPerPageRecent + rowsPerPageRecent)
                                .map((login, idx) => (
                                    <TableRow key={idx} sx={{'&:hover': {background: '#1a1a2e'}}}>
                                        <TableCell sx={{color: '#fff'}}>
                                            <div>
                                                <strong>{login.firstName} {login.lastName}</strong>
                                                <br/>
                                                <small style={{color: '#999'}}>{login.email}</small>
                                            </div>
                                        </TableCell>
                                        <TableCell sx={{color: '#fff'}}>
                                            {dayjs(login.loginTime).format('MMM dd, yyyy HH:mm:ss')}
                                        </TableCell>
                                        <TableCell sx={{color: '#fff', fontFamily: 'monospace'}}>
                                            {login.ipAddress}
                                        </TableCell>
                                        <TableCell sx={{color: '#fff'}}>{login.browser}</TableCell>
                                        <TableCell sx={{color: '#fff'}}>{login.operatingSystem}</TableCell>
                                        <TableCell>
                                            <Typography
                                                variant="body2"
                                                sx={{
                                                    color: login.success ? '#00C49F' : '#FF8042',
                                                    fontWeight: 'bold',
                                                }}
                                            >
                                                {login.success ? '✓ Success' : '✗ Failed'}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TablePagination
                    rowsPerPageOptions={[5, 10, 25]}
                    component="div"
                    count={recentLogins.length}
                    rowsPerPage={rowsPerPageRecent}
                    page={pageRecent}
                    onPageChange={handleChangePageRecent}
                    onRowsPerPageChange={handleChangeRowsPerPageRecent}
                    sx={{background: '#16213e', color: '#fff'}}
                />
            </Card>

            {/* User History Dialog */}
            <Dialog
                open={openUserDialog}
                onClose={handleCloseUserDialog}
                maxWidth="md"
                fullWidth
                PaperProps={{
                    sx: {
                        background: '#0f3460',
                        border: '1px solid #e94560',
                    },
                }}
            >
                <DialogTitle sx={{color: '#ffd821', fontWeight: 'bold', background: '#16213e'}}>
                    {selectedUser && `${selectedUser.firstName} ${selectedUser.lastName} - Login History`}
                </DialogTitle>
                <DialogContent sx={{pt: 2}}>
                    {selectedUser && (
                        <Box sx={{mb: 2}}>
                            <Typography sx={{color: '#fff', mb: 1}}>
                                <strong>Email:</strong> {selectedUser.email}
                            </Typography>
                            <Typography sx={{color: '#fff', mb: 1}}>
                                <strong>Unique
                                    Devices:</strong> {selectedUser.devices ? selectedUser.devices.length : 0}
                            </Typography>
                            {selectedUser.devices && selectedUser.devices.length > 0 && (
                                <Box sx={{color: '#999', fontSize: '0.9rem'}}>
                                    {selectedUser.devices.map((device, idx) => (
                                        <div key={idx}>• {device}</div>
                                    ))}
                                </Box>
                            )}
                        </Box>
                    )}

                    <Typography variant="h6" sx={{color: '#ffd821', mb: 2, fontWeight: 'bold'}}>
                        Login Details
                    </Typography>

                    <TableContainer>
                        <Table>
                            <TableHead sx={{background: '#16213e'}}>
                                <TableRow>
                                    <TableCell sx={{color: '#ffd821'}}>Login Time</TableCell>
                                    <TableCell sx={{color: '#ffd821'}}>IP</TableCell>
                                    <TableCell sx={{color: '#ffd821'}}>Browser</TableCell>
                                    <TableCell sx={{color: '#ffd821'}}>OS</TableCell>
                                    <TableCell sx={{color: '#ffd821'}}>Status</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {userLoginHistory
                                    .slice(pageUser * rowsPerPageUser, pageUser * rowsPerPageUser + rowsPerPageUser)
                                    .map((login, idx) => (
                                        <TableRow key={idx}>
                                            <TableCell sx={{color: '#fff', fontSize: '0.85rem'}}>
                                                {dayjs(login.loginTime).format('MMM dd HH:mm')}
                                            </TableCell>
                                            <TableCell
                                                sx={{color: '#fff', fontFamily: 'monospace', fontSize: '0.85rem'}}>
                                                {login.ipAddress}
                                            </TableCell>
                                            <TableCell sx={{color: '#fff', fontSize: '0.85rem'}}>
                                                {login.browser}
                                            </TableCell>
                                            <TableCell sx={{color: '#fff', fontSize: '0.85rem'}}>
                                                {login.operatingSystem}
                                            </TableCell>
                                            <TableCell sx={{fontSize: '0.85rem'}}>
                                                <Typography
                                                    sx={{
                                                        color: login.success ? '#00C49F' : '#FF8042',
                                                        fontWeight: 'bold',
                                                    }}
                                                >
                                                    {login.success ? '✓' : '✗'}
                                                </Typography>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                    <TablePagination
                        rowsPerPageOptions={[5, 10]}
                        component="div"
                        count={userLoginHistory.length}
                        rowsPerPage={rowsPerPageUser}
                        page={pageUser}
                        onPageChange={handleChangePageUser}
                        onRowsPerPageChange={handleChangeRowsPerPageUser}
                        sx={{background: '#16213e', color: '#fff'}}
                    />
                </DialogContent>
                <DialogActions sx={{background: '#16213e', pt: 1, pb: 1, pr: 2}}>
                    <Button onClick={handleCloseUserDialog} variant="contained" sx={{background: '#e94560'}}>
                        Close
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
