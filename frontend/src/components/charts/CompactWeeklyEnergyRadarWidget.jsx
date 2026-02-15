import React, {useEffect, useState} from "react";
import {RadarChart} from "@mui/x-charts/RadarChart";
import {getEnergyAnalytics} from "../../services/apis.jsx";
import Stack from "@mui/material/Stack";
import {ToggleButton, ToggleButtonGroup} from "@mui/material";
import Typography from "@mui/material/Typography";

function valueFormatter(v) {
    if (v === null) {
        return 'NaN';
    }
    return `${v.toLocaleString()} Wh`;
}

export function CompactWeeklyEnergyRadarWidget({vid}) {
    const [status, setStatus] = useState("DISCHARGE")
    const [series, setSeries] = useState([]);
    const [labels, setLabels] = useState(["0"]);
    const [max, setMax] = useState(200);

    useEffect(() => {
        const fetch = async () => {
            const res = await getEnergyAnalytics(vid, status === "DISCHARGE" ? "totalWh" : "chargeTotalWh");
            // console.log("data", res)
            const {labels, data} = res;
            setLabels(labels);
            setSeries(data);
        }
        fetch();
    }, [status])

    // const labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

    const [highlightedItem, setHighlightedItem] = React.useState(null);

    const withOptions = (series) =>
        series.map((item) => ({
            ...item,
            fillArea: true,
            type: 'radar',
            valueFormatter
        }));

    // const maxValue = Math.max(...values) * 1.2;
    const handleHighLightedSeries = (event, newHighLightedSeries) => {
        if (newHighLightedSeries !== null) {
            setHighlightedItem((prev) => ({
                ...prev,
                seriesId: newHighLightedSeries,
            }));
        }
    };
    const handleChange = (event, newAlignment) => {
        setStatus(newAlignment);
    };

    return (
        <div style={{
            padding: '8px',
            display: 'flex',
            flexDirection: 'column',
        }}>

            <div style={{
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center'
            }}>
                {series.length > 0 && labels.length > 0 && (
                    <RadarChart
                        height={240}
                        highlight="series"
                        shape="circular"
                        hideLegend
                        highlightedItem={highlightedItem}
                        onHighlightChange={setHighlightedItem}
                        slotProps={{
                            tooltip: {trigger: 'item'},
                        }}
                        series={withOptions(series)}
                        radar={{
                            startAngle: 25,
                            // max: 250,
                            metrics: labels,
                        }}
                    />
                )}

                <ToggleButtonGroup
                    value={highlightedItem?.seriesId ?? null}
                    exclusive
                    orientation="vertical"
                    onChange={handleHighLightedSeries}
                    aria-label="highlighted series"
                    style={{
                        marginLeft: '20px'
                    }}
                    size="small"
                >
                    {series.map(item => (
                        <ToggleButton
                            key={item.id}
                            value={item.id}
                            aria-label={`series ${item.label}`}
                        >
                            {item.label}
                        </ToggleButton>
                    ))}
                </ToggleButtonGroup>
            </div>
            <div style={{display:'flex', alignItems:'center', justifyContent:'space-between'}}>
                <Typography>
                    Weekly energy usage
                </Typography>
                <ToggleButtonGroup
                    color="primary"
                    value={status}
                    size="small"
                    exclusive
                    onChange={handleChange}
                    aria-label="status"
                >
                    <ToggleButton value="DISCHARGE">Discharge</ToggleButton>
                    <ToggleButton value="CHARGING">Charge</ToggleButton>
                </ToggleButtonGroup>
            </div>

        </div>
    );
}
