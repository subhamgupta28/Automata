import {
    Button, Card,
    CardContent,
    Chip,
    Dialog,
    DialogContent,
    DialogTitle,
    Grid2,
    Modal,
    Switch,
    Typography
} from "@mui/material";
import React, {useEffect, useState} from "react";
// import {getDevices} from "../../services/apis.jsx";
import Stack from "@mui/material/Stack";
import Divider from "@mui/material/Divider";
import {useCachedDevices} from "../../services/AppCacheContext.jsx";

export default function AddActionDialog({isOpen, onClose}){
    const {devices, loading, error} = useCachedDevices();
    // useEffect(() => {
    //     const fetchDevices = async () => {
    //         try {
    //             const devices = await getDevices();
    //             console.log("devices", devices);
    //             setDevices(devices)
    //         } catch (err) {
    //             console.error("Failed to fetch devices:", err);
    //         }
    //     };
    //
    //     fetchDevices();
    // }, []);
    return(
        <Modal onClose={onClose} open={isOpen} >
            <div style={{overflow: 'auto', padding:'20px', backdropFilter: 'blur(1px)'}}>


                <Stack spacing={2} style={{ height: '90dvh'}} direction="row" divider={<Divider orientation='vertical' flexItem />}>
                    <div style={{width:"33%"}}>
                        Triggers
                        <Grid2 container spacing={1}>
                            {devices.map((device) => (
                                <Grid2 item xs={12} sm={2} md={6} lg={2} key={device.id}>
                                    <Card style={{
                                        marginBottom: '20px',
                                        width: '150px',
                                        boxShadow: '0 0 10px 0 rgba(0,0,0,0.2)',
                                        borderRadius: '12px',
                                    }}>
                                        <CardContent>
                                            <Typography variant="body" sx={{fontWeight: 'bold', color: 'primary.main'}}>
                                                {device.name}
                                            </Typography>
                                            <Divider sx={{margin: '16px 0'}}/>

                                            {device.attributes.map((attribute) => (
                                                <div key={attribute.id}>
                                                    <Typography variant="body2" color="textPrimary">
                                                        <strong>{attribute.displayName}</strong>: {attribute.key} ({attribute.units})
                                                    </Typography>
                                                </div>
                                            ))}

                                            {/*<Divider sx={{ margin: '16px 0' }} />*/}


                                        </CardContent>
                                    </Card>
                                </Grid2>
                            ))}
                        </Grid2>
                    </div>
                    <div style={{width:"33%"}}>
                        Actions
                    </div>
                    <div style={{width:"33%"}}>Item 3</div>
                </Stack>
                <Button variant="contained" onClick={onClose}>Close</Button>
            </div>
        </Modal>
    )
}