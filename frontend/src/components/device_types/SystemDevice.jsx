import {Card} from "@mui/material";
import Typography from "@mui/material/Typography";
import React from "react";

export default function SystemDevice ({devices, messages}){
    const mainData = [...devices[0].attributes];
    return(
        <div style={{
            gridTemplateColumns: 'repeat(2, 1fr)',
            display: 'grid',
            gap: '4px',
            marginTop: '10px'
        }}>
            {mainData.map((m) => (
                <Card
                    key={m.id}
                    elevation={0}
                    style={{
                        borderRadius: '8px',
                        padding: '4px',
                        // backgroundColor: 'transparent',
                        // backdropFilter: 'blur(7px)',
                        // borderColor: '#606060',
                        // borderWidth: '2px',
                        // borderStyle: 'dashed',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'space-between',
                        alignItems: 'center'
                    }}
                >

                    <Typography style={{display: 'flex', fontSize: '18px'}} color="primary"
                                fontWeight="bold">
                        {/*{m.displayName.includes("Temp") && <TemperatureGauge temp={liveData?.[m.key]}/>}*/}
                        {/*{liveData?.[m.key]}*/}
                        {m.units}
                    </Typography>
                    <Typography>{m.displayName}</Typography>
                </Card>
            ))}
        </div>
    )
}