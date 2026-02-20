import LiquidFillGauge from "react-ts-liquid-gauge";
import React from "react";
import {Card} from "@mui/material";
import Typography from "@mui/material/Typography";

export default function BatteryCard({name, value=0}) {
    const data = [{}]

    return (
        <div style={{display: "flex", flexDirection:"column", justifyContent:"center", alignItems:"center", width:"110px"}}>
            <LiquidFillGauge
                value={value}
                width={100}
                height={100}
                style={{
                    borderRadius: "12px",
                }}
                waveAnimation={true}
                waveFrequency={3}
                waveAmplitude={2}
                gradient={true}
                riseAnimation={true}
                riseAnimationTime={1000}
                textStyle={{fill: "#fff"}}
                waveTextStyle={{fill: "#ffd821"}}
                shapeStyle={{fill: "#ffd821"}}
                waveStyle={{fill: "#ffd821"}}
                shapeType="rectangle"
            />
            <Typography>
                {name}
            </Typography>
        </div>
    )
}