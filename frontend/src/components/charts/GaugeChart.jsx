import { Gauge, gaugeClasses } from '@mui/x-charts/Gauge';
import Typography from "@mui/material/Typography";

export function GaugeChart({value, maxValue, displayName}) {
    return(
        <>
            <div>
                <Gauge
                    value={parseInt(value)}
                    startAngle={-110}
                    endAngle={110}
                    height={100}
                    valueMin={0}
                    valueMax={maxValue}
                    sx={{
                        [`& .${gaugeClasses.valueText}`]: {
                            transform: 'translate(0px, 0px)',
                        },
                    }}
                    text={
                        ({ value, valueMax }) => `${value} / ${maxValue}`
                    }
                />
                <Typography textAlign='center'>
                    {displayName}
                </Typography>
            </div>
        </>
    )
}