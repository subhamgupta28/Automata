import React, {useEffect, useState} from 'react';
import {Card, CardContent, Typography, Grid2, Chip, Divider, CardActions, Switch, Snackbar} from '@mui/material';
import {getDevices, updateShowInDashboard} from "../services/apis.jsx";


const DeviceCard = ({device}) => {
    const [showInDashboard, setShowInDashboard] = useState(device.showInDashboard);

    const handleChange = (event) => {
        const fetchData = async () => {
            try {
                const devices = await updateShowInDashboard(device.id, event.target.checked);

            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
        setShowInDashboard(event.target.checked);

    };


    return (
        <Grid2 item xs={12} sm={2} md={6} lg={2}>
            <Card style={{
                marginBottom: '20px',
                width: '250px',
                boxShadow: '0 0 10px 0 rgba(0,0,0,0.2)',
                borderRadius: '12px',
            }}>
                <CardContent>
                    <Typography variant="h6" sx={{fontWeight: 'bold', color: 'primary.main'}}>
                        {device.name}
                        <Chip
                            label={device.status}
                            size='small'
                            sx={{
                                color: device.status === 'ONLINE' ? 'success.main' : 'error.main',
                                // color: 'white',
                                marginLeft: '10px'
                            }}
                        />

                    </Typography>

                    <Typography variant="body2" color="textSecondary">
                        Show In Dashboard: <Switch defaultChecked size="small" checked={showInDashboard}
                                             onChange={handleChange}/>
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                        Access URL: <a href={device.accessUrl} target="_blank"
                                       rel="noopener noreferrer">{device.accessUrl}</a>
                    </Typography>
                    <Divider sx={{margin: '16px 0'}}/>

                    {/*{device.attributes.map((attribute) => (*/}
                    {/*    <div key={attribute.id} sx={{marginBottom: 2}}>*/}
                    {/*        <Typography variant="body2" color="textPrimary">*/}
                    {/*            <strong>{attribute.displayName}</strong>: {attribute.key} ({attribute.units})*/}
                    {/*        </Typography>*/}
                    {/*    </div>*/}
                    {/*))}*/}

                    {/*<Divider sx={{ margin: '16px 0' }} />*/}


                </CardContent>
            </Card>
        </Grid2>
    );
};
const DeviceList = ({devices}) => {
    return (
        <div style={{ padding: '20px'}}>
            <Grid2 container spacing={1}>
                {devices.map((device) => (
                    <DeviceCard key={device.id} device={device}/>
                ))}
            </Grid2>
        </div>
    );
};
export default function Devices() {
    const [devicesData, setDevicesData] = useState([]);
    useEffect(() => {
        const fetchData = async () => {
            try {
                const devices = await getDevices();
                setDevicesData(devices);
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchData();
    }, []);
    return (
        <div style={{paddingTop: '60px'}}>
            {/*<Action/>*/}

            <DeviceList devices={devicesData}/>
        </div>
    )
}