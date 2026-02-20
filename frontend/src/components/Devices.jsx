import React, {useEffect, useState} from 'react';
import {
    Card,
    CardContent,
    Typography,
    Chip,
    Divider,
    CardActions,
    Switch,
    Snackbar,
    CircularProgress, Backdrop, Collapse, Box, Stack
} from '@mui/material';
import {updateAttribute, updateShowInDashboard} from "../services/apis.jsx";
import {styled} from '@mui/material/styles';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell, {tableCellClasses} from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import {useCachedDevices} from "../services/AppCacheContext.jsx";
import dayjs from "dayjs";


const StyledTableCell = styled(TableCell)(({theme}) => ({
    [`&.${tableCellClasses.head}`]: {
        backgroundColor: theme.palette.common.black,
        color: theme.palette.common.white,
    },
    [`&.${tableCellClasses.body}`]: {
        fontSize: 14,
    },
}));

const StyledTableRow = styled(TableRow)(({theme}) => ({
    '&:nth-of-type(odd)': {
        backgroundColor: theme.palette.action.hover,
    },
    // hide last border
    '&:last-child td, &:last-child th': {
        border: 0,
    },
}));

export default function Devices() {
    const {devices, loading, error} = useCachedDevices();
    const [devicesData, setDevicesData] = useState([]);
    const [openRow, setOpenRow] = useState("");
    const [showInDashboard, setShowInDashboard] = useState(false);
    const [openBackdrop, setOpenBackdrop] = useState(false);

    useEffect(() => {
        setOpenBackdrop(loading)
        const fetchData = async () => {
            try {

                setDevicesData(devices);
                setOpenBackdrop(loading)
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, [devices]);

    const handleChange = (device, attribute, checked) => {
        const fetchData = async () => {
            try {
                await updateAttribute(device.id, attribute, checked);
                setDevicesData(prevData =>
                    prevData.map(d =>
                        d.id === device.id ? {...d, [attribute]: checked} : d
                    )
                );
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
        // setShowInDashboard(checked);

    };

    return (
        <div
            style={{
                padding: '20px',
                height: '100vh',
                width: '95vw',// Full viewport
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',   // Prevent page scroll
                boxSizing: 'border-box',
            }}
        >
            {/*<Action/>*/}
            <TableContainer component={Paper}
                            sx={{
                                borderRadius: '10px',
                                flex: 1,            // Take remaining space
                                overflow: 'auto',   // Scroll only inside table
                            }}
            >
                <Table
                    stickyHeader              // 👈 enables sticky header
                    sx={{
                        minWidth: 1200,         // 👈 increase if you expect many columns
                        whiteSpace: 'nowrap',   // 👈 prevents columns from wrapping
                    }}
                    aria-label="customized table"
                >
                    <TableHead>
                        <TableRow>
                            <StyledTableCell>Device Name</StyledTableCell>
                            <StyledTableCell align="right">Status</StyledTableCell>
                            <StyledTableCell align="right">Show In Dashboard</StyledTableCell>
                            <StyledTableCell align="right">Show Charts</StyledTableCell>
                            <StyledTableCell align="right">Analytics</StyledTableCell>
                            <StyledTableCell align="right">Access URL</StyledTableCell>
                            {/*<StyledTableCell align="right">Host</StyledTableCell>*/}
                            {/*<StyledTableCell align="right">Mac Address</StyledTableCell>*/}
                            {/*<StyledTableCell align="right">Last Online</StyledTableCell>*/}
                            {/*<StyledTableCell align="right">Last Registered</StyledTableCell>*/}
                            {/*<StyledTableCell align="right">Update Interval(Min.)</StyledTableCell>*/}
                            {/*<StyledTableCell align="right">Type</StyledTableCell>*/}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {devicesData?.map((device) => {
                            const isOpen = openRow === device.id;
                            return (
                                <React.Fragment key={device.id}>
                                    <StyledTableRow
                                        hover
                                        onClick={() =>
                                            setOpenRow(isOpen ? null : device.id)
                                        }
                                        sx={{cursor: "pointer"}}
                                    >
                                        <StyledTableCell component="th" scope="row">
                                            {device.name}
                                        </StyledTableCell>
                                        <StyledTableCell align="right">
                                            <Chip
                                                label={device.status}
                                                size='small'
                                                sx={{
                                                    color: device.status === 'ONLINE' ? 'success.main' : 'error.main',
                                                    // color: 'white',
                                                    marginLeft: '10px'
                                                }}
                                            />
                                        </StyledTableCell>

                                        <StyledTableCell align="right">
                                            <Switch defaultChecked size="small" checked={device.showInDashboard}
                                                    onChange={(e) => handleChange(device, "showInDashboard", e.target.checked)}/>
                                        </StyledTableCell>
                                        <StyledTableCell align="right">
                                            <Switch defaultChecked size="small" checked={device.showCharts}
                                                    onChange={(e) => handleChange(device, "showCharts", e.target.checked)}/>
                                        </StyledTableCell>
                                        <StyledTableCell align="right">
                                            <Switch defaultChecked size="small" checked={device.analytics}
                                                    onChange={(e) => handleChange(device, "analytics", e.target.checked)}/>
                                        </StyledTableCell>
                                        <StyledTableCell align="right">
                                            <a href={device.accessUrl} target="_blank"
                                               rel="noopener noreferrer">{device.accessUrl}</a>
                                        </StyledTableCell>

                                    </StyledTableRow>
                                    {/* Expandable Row */}
                                    <TableRow>
                                        <TableCell
                                            colSpan={12} // must match total column count
                                            sx={{p: 0, border: 0}}
                                        >
                                            <Collapse in={isOpen} timeout="auto" unmountOnExit>
                                                <Stack direction="row"
                                                       divider={<Divider orientation="vertical" flexItem/>} spacing={2}
                                                >
                                                    <div style={{width: '55%'}}>
                                                        <Table size="small">
                                                            <TableBody>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600, width: "250px"}}>
                                                                        Host
                                                                    </TableCell>
                                                                    <TableCell>
                                                                        <a
                                                                            href={'http://' + device.host + '.local'}
                                                                            target="_blank"
                                                                            rel="noopener noreferrer"
                                                                        >
                                                                            {device.host}
                                                                        </a>
                                                                    </TableCell>
                                                                </TableRow>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600}}>
                                                                        Mac Address
                                                                    </TableCell>
                                                                    <TableCell>{device.macAddr}</TableCell>
                                                                </TableRow>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600}}>
                                                                        Last Online
                                                                    </TableCell>
                                                                    <TableCell>
                                                                        {dayjs(device.lastOnline).format("MMMM D, YYYY h:mm A")}{" "}
                                                                        ({dayjs(device.lastOnline).fromNow()})
                                                                    </TableCell>
                                                                </TableRow>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600}}>
                                                                        Last Registered
                                                                    </TableCell>
                                                                    <TableCell>
                                                                        {dayjs(device.lastRegistered).format("MMMM D, YYYY h:mm A")}{" "}
                                                                        ({dayjs(device.lastRegistered).fromNow()})
                                                                    </TableCell>
                                                                </TableRow>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600}}>
                                                                        Update Interval (Min.)
                                                                    </TableCell>
                                                                    <TableCell>
                                                                        {device.updateInterval / 1000 / 60}
                                                                    </TableCell>
                                                                </TableRow>

                                                                <TableRow>
                                                                    <TableCell sx={{fontWeight: 600}}>
                                                                        Type
                                                                    </TableCell>
                                                                    <TableCell>{device.type}</TableCell>
                                                                </TableRow>

                                                            </TableBody>
                                                        </Table>

                                                    </div>
                                                    <Box sx={{padding: 2, width: '40%', height: "40dvh"}}>
                                                        <TableContainer
                                                            sx={{
                                                                height: '100%',
                                                                overflow: 'auto', // 👈 scroll happens here
                                                            }}
                                                        >
                                                            <Table stickyHeader size="small">
                                                                <TableHead>
                                                                    <TableRow>
                                                                        <StyledTableCell>Name</StyledTableCell>
                                                                        <StyledTableCell>Key</StyledTableCell>
                                                                        <StyledTableCell>Units</StyledTableCell>
                                                                    </TableRow>
                                                                </TableHead>

                                                                <TableBody>
                                                                    {device.attributes.map((att, index) => (
                                                                        <StyledTableRow key={index}>
                                                                            <StyledTableCell>{att.displayName}</StyledTableCell>
                                                                            <StyledTableCell>{att.key}</StyledTableCell>
                                                                            <StyledTableCell>{att.units}</StyledTableCell>
                                                                        </StyledTableRow>
                                                                    ))}
                                                                </TableBody>
                                                            </Table>
                                                        </TableContainer>
                                                    </Box>
                                                </Stack>

                                            </Collapse>
                                        </TableCell>
                                    </TableRow>
                                </React.Fragment>
                            );
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
            <Backdrop
                sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                open={openBackdrop}
            >
                <CircularProgress color="inherit"/>
            </Backdrop>
            {/*<DeviceList devices={devicesData}/>*/}
        </div>
    )
}