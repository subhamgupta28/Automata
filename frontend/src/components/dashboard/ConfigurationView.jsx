// WiFiSettings.tsx
import React, { useEffect, useState } from 'react';
import {
    Box,
    Button,
    Card,
    CardContent,
    Grid,
    TextField,
    Typography,
} from '@mui/material';
import axios from 'axios';
import {getWiFiDetails, saveWiFiList} from "../../services/apis.jsx";
import Paper from "@mui/material/Paper";
import {styled} from "@mui/material/styles";

const WiFiSettings = () => {
    const [wifiList, setWifiList] = useState([
        { ssid: '', password: '' },
        { ssid: '', password: '' },
        { ssid: '', password: '' },
    ]);
    const [loading, setLoading] = useState(false);

    const fetchWiFiList = async () => {
        try {
            const response = await getWiFiDetails();
            const data = response;
            setWifiList([
                { ssid: data.wn1 || '', password: data.wp1 || '' },
                { ssid: data.wn2 || '', password: data.wp2 || '' },
                { ssid: data.wn3 || '', password: data.wp3 || '' },
            ]);
        } catch (error) {
            console.error('Error fetching WiFi list:', error);
        }
    };

    useEffect(() => {
        fetchWiFiList();
    }, []);

    const handleChange = (index, field, value) => {
        const newList = [...wifiList];
        newList[index][field] = value;
        setWifiList(newList);
    };

    const handleSave = async () => {
        // POST or PUT logic here (e.g., /saveWiFiList endpoint)
        try {
            const payload = {
                wn1: wifiList[0].ssid,
                wp1: wifiList[0].password,
                wn2: wifiList[1].ssid,
                wp2: wifiList[1].password,
                wn3: wifiList[2].ssid,
                wp3: wifiList[2].password,
            };
            await saveWiFiList(payload); // You need to implement this on the backend
            alert('WiFi details saved!');
        } catch (error) {
            console.error('Failed to save WiFi details:', error);
            alert('Failed to save WiFi details.');
        }
    };

    return (
        <Card>
            <CardContent>
                <Typography variant="h5" gutterBottom>
                    WiFi Settings
                </Typography>
                {wifiList.map((wifi, index) => (
                    <Box key={index} mb={3}>
                        <Typography variant="subtitle1" gutterBottom>
                            WiFi #{index + 1}
                        </Typography>
                        <Grid container spacing={2}>
                            <Grid item xs={6}>
                                <TextField
                                    size="small"
                                    label="SSID"
                                    fullWidth
                                    value={wifi.ssid}
                                    onChange={(e) => handleChange(index, 'ssid', e.target.value)}
                                />
                            </Grid>
                            <Grid item xs={6}>
                                <TextField
                                    size="small"
                                    label="Password"
                                    type="password"
                                    fullWidth
                                    value={wifi.password}
                                    onChange={(e) => handleChange(index, 'password', e.target.value)}
                                />
                            </Grid>
                        </Grid>
                    </Box>
                ))}
                <Button size="small" variant="contained" onClick={handleSave} disabled={loading}>
                    {loading ? 'Saving...' : 'Save WiFi Details'}
                </Button>
            </CardContent>
        </Card>
    );
};

const Item = styled(Paper)(({ theme }) => ({
    backgroundColor: '#fff',
    ...theme.typography.body2,
    padding: theme.spacing(1),
    textAlign: 'center',
    height:'95%',
    color: (theme.vars ?? theme).palette.text.secondary,
    ...theme.applyStyles('dark', {
        backgroundColor: '#1A2027',
    }),
}));
export function ConfigurationView(){

    return(
        <div style={{paddingTop: '10px', paddingRight:'12px', paddingLeft:'12px', height:'100dvh'}}>
            <Grid container spacing={1}>
                <Grid size={6}>
                    <WiFiSettings/>
                </Grid>
                <Grid size={6}>
                    <Item>size=4</Item>
                </Grid>
                <Grid size={4}>
                    <Item>size=4</Item>
                </Grid>
                <Grid size={8}>
                    <Item>size=8</Item>
                </Grid>
            </Grid>

        </div>
    )
}