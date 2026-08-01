// EnergyBatteryChartNode.jsx
import React, {useEffect, useRef, useState} from "react";
import {Box, Card, ToggleButton, ToggleButtonGroup} from "@mui/material";
import Typography from "@mui/material/Typography";
import {getEnergyAnalytics} from "../../services/apis.jsx";
import {useCardGlowEffect} from "../../utils/useCardGlowEffect.jsx";
import '../../App.css'
import {C} from "./WeatherCardV2.jsx";
import {BarChart} from "@mui/x-charts/BarChart";

function valueFormatter(v) {
    if (v === null) {
        return 'NaN';
    }
    return `${v.toLocaleString()} Wh`;
}

export const EnergyBatteryChartNode = React.memo(({id, data, isConnectable, selected}) => {
    const cardRef = useRef(null);
    useCardGlowEffect(cardRef, true);

    const {
        height,
        name,
        width,
    } = data.value;

    const vid = id;

    const [status, setStatus] = useState("DISCHARGE");
    const [series, setSeries] = useState([]);
    const [labels, setLabels] = useState(["0"]);

    useEffect(() => {
        const fetch = async () => {
            const res = await getEnergyAnalytics(vid, status === "DISCHARGE" ? "totalWh" : "chargeTotalWh");
            const {labels, data} = res;
            setLabels(labels);
            setSeries(data);
        }
        fetch();
    }, [status, vid])

    const handleChange = (event, newAlignment) => {
        if (newAlignment !== null) setStatus(newAlignment);
    };

    // Minimal styling: single muted tone, no per-series color cycling
    const withOptions = (series) =>
        series.map((item) => ({
            ...item,
            valueFormatter,
            color: status === "DISCHARGE" ? "#D85A30" : "#1D9E75",
        }));

    const chartHeight = Math.max((height || 200) - 60, 100);

    return (
        <Card
            ref={cardRef}
            className="card-glow-container"
            variant="elevated" style={{
            background: 'transparent',
            border: `1px solid ${C.border}`,
            backgroundColor: 'rgb(0 0 0 / 0%)',
            minHeight: height, height: '100%', minWidth: width, padding: '10px', borderRadius: '12px',
            boxShadow: 'none'
        }}>
            <div className="card-glow"/>
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginLeft: '4px',
                marginBottom: '2px'
            }}>
                <Typography style={{fontWeight: 500, fontSize: '13px', letterSpacing: '0.2px', opacity: 0.85}}>
                    {name}
                </Typography>
                <ToggleButtonGroup
                    value={status}
                    size="small"
                    exclusive
                    onChange={handleChange}
                    aria-label="status"
                    style={{height: '22px'}}
                >
                    <ToggleButton
                        value="DISCHARGE"
                        style={{fontSize: '10px', padding: '0 8px', border: 'none', textTransform: 'none'}}
                    >
                        Discharge
                    </ToggleButton>
                    <ToggleButton
                        value="CHARGING"
                        style={{fontSize: '10px', padding: '0 8px', border: 'none', textTransform: 'none'}}
                    >
                        Charge
                    </ToggleButton>
                </ToggleButtonGroup>
            </div>

            <Box style={{display: 'flex', justifyContent: 'center'}}>
                {series.length > 0 && labels.length > 0 && series.every(s => s.data && s.data.length === labels.length) && (
                    <BarChart
                        height={chartHeight}
                        series={withOptions(series)}
                        xAxis={[{
                            scaleType: 'band',
                            data: labels,
                            disableTicks: true,
                            tickLabelStyle: {fontSize: 9, fill: 'rgba(255,255,255,0.45)'},
                        }]}
                        yAxis={[{
                            position: 'none',
                            disableLine: true,
                            disableTicks: true,
                            tickLabelStyle: {fontSize: 0}
                        }]}
                        borderRadius={2}
                        grid={{horizontal: false, vertical: false}}
                        slotProps={{legend: {hidden: true}}}
                        sx={{
                            '& .MuiChartsAxis-line': {stroke: 'rgba(255,255,255,0.12)'},
                            '& .MuiBarElement-root': {maxWidth: 10},
                            marginTop: 1
                        }}
                        // margin={{top: 8, bottom: 8, left: 8, right: 8}}
                    />
                )}
            </Box>
        </Card>
    );
});