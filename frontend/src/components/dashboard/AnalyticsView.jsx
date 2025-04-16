import ChartDetail from "../charts/ChartDetail.jsx";
import React, {useEffect, useState} from "react";
import {getDashboardDevices} from "../../services/apis.jsx";
import {Card} from "@mui/material";

export default function AnalyticsView(){
    const [device, setDevice] = useState([])

    useEffect(()=>{
        const fet = async () => {
            const devices = await getDashboardDevices();
            setDevice(devices)
        }
        fet();
    },[])


    return(
        <div style={{paddingTop: '50px'}}>
            {device.map(t=>(
                <Card style={{padding:'20px', margin:'10px'}}>
                    <h4>{t.name}</h4>
                    <ChartDetail deviceId={t.id}/>
                </Card>
            ))}
        </div>
    )
}