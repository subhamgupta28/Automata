import {Dialog, DialogContent, DialogTitle} from "@mui/material";
import React, {useEffect, useState} from "react";
import {getDevices} from "../../services/apis.jsx";

export default function AddActionDialog({isOpen, onClose}){
    const [devices, setDevices] = useState([]);
    useEffect(() => {
        const fetchDevices = async () => {
            try {
                const devices = await getDevices();
                console.log("devices", devices);
                setDevices(devices);
            } catch (err) {
                console.error("Failed to fetch devices:", err);
            }
        };

        fetchDevices();
    }, []);
    return(
        <Dialog fullWidth maxWidth="md" onClose={onClose} open={isOpen}>
            <DialogTitle>Create Action</DialogTitle>
            <DialogContent style={{overflow: 'auto'}}>
                <div>
                    {devices.map((device) => (
                        <div key={device.id}>
                            {device.name}
                        </div>
                    ))}
                </div>
            </DialogContent>
        </Dialog>
    )
}