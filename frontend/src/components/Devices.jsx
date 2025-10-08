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
    CircularProgress, Backdrop
} from '@mui/material';
import {getDevices, updateAttribute, updateShowInDashboard} from "../services/apis.jsx";
import { styled } from '@mui/material/styles';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell, { tableCellClasses } from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';


const StyledTableCell = styled(TableCell)(({ theme }) => ({
    [`&.${tableCellClasses.head}`]: {
        backgroundColor: theme.palette.common.black,
        color: theme.palette.common.white,
    },
    [`&.${tableCellClasses.body}`]: {
        fontSize: 14,
    },
}));

const StyledTableRow = styled(TableRow)(({ theme }) => ({
    '&:nth-of-type(odd)': {
        backgroundColor: theme.palette.action.hover,
    },
    // hide last border
    '&:last-child td, &:last-child th': {
        border: 0,
    },
}));

export default function Devices() {
    const [devicesData, setDevicesData] = useState([]);
    const [showInDashboard, setShowInDashboard] = useState(false);
    const [openBackdrop, setOpenBackdrop] = useState(false);

    useEffect(() => {
        setOpenBackdrop(true)
        const fetchData = async () => {
            try {
                const devices = await getDevices();
                setDevicesData(devices);
                setOpenBackdrop(false)
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, []);

    const handleChange = (device, attribute, checked) => {
        const fetchData = async () => {
            try {
                await updateAttribute(device.id, attribute, checked);
                setDevicesData(prevData =>
                    prevData.map(d =>
                        d.id === device.id ? { ...d, [attribute]: checked } : d
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
        <div style={{paddingTop: '40px', paddingLeft: '40px', paddingRight: '40px',height:'100%',}}>
            {/*<Action/>*/}
            <TableContainer component={Paper} style={{borderRadius:'10px', height:'90dvh',scrollbarWidth: "none"}}>
                <Table sx={{ minWidth: 700 }} aria-label="customized table">
                    <TableHead>
                        <TableRow>
                            <StyledTableCell>Device Name</StyledTableCell>
                            <StyledTableCell align="right">Status</StyledTableCell>
                            <StyledTableCell align="right">Show In Dashboard</StyledTableCell>
                            <StyledTableCell align="right">Show Charts</StyledTableCell>
                            <StyledTableCell align="right">Analytics</StyledTableCell>
                            <StyledTableCell align="right">Access URL:</StyledTableCell>
                            <StyledTableCell align="right">Host</StyledTableCell>
                            <StyledTableCell align="right">Update Interval(Min.)</StyledTableCell>
                            <StyledTableCell align="right">Type</StyledTableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {devicesData.map((device) => (
                            <StyledTableRow key={device.id}>
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
                                            onChange={(e)=>handleChange(device, "showInDashboard", e.target.checked)}/>
                                </StyledTableCell>
                                <StyledTableCell align="right">
                                    <Switch defaultChecked size="small" checked={device.showCharts}
                                            onChange={(e)=>handleChange(device, "showCharts", e.target.checked)}/>
                                </StyledTableCell>
                                <StyledTableCell align="right">
                                    <Switch defaultChecked size="small" checked={device.analytics}
                                            onChange={(e)=>handleChange(device, "analytics", e.target.checked)}/>
                                </StyledTableCell>
                                <StyledTableCell align="right">
                                    <a href={device.accessUrl} target="_blank"
                                                                  rel="noopener noreferrer">{device.accessUrl}</a>
                                </StyledTableCell>
                                <StyledTableCell align="right">
                                    <a href={'http://'+device.host+'.local'} target="_blank"
                                       rel="noopener noreferrer">{device.host}</a>
                                </StyledTableCell>
                                <StyledTableCell align="right">{device.updateInterval/1000/60} </StyledTableCell>
                                <StyledTableCell align="right">{device.type} </StyledTableCell>
                            </StyledTableRow>
                        ))}
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