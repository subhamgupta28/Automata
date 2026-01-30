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

    const [series, setSeries] = useState([]);
    const [labels, setLabels] = useState([]);

    useEffect(() => {
        const fetch = async () => {
            const res = await getEnergyAnalytics(vid, "totalWh");
            console.log("data", res)
            const { labels, data } = res;
            setLabels(labels);
            setSeries(data);
        }
        fetch();
    }, [vid])

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
    return (
        <div style={{
            padding:'8px',
            display: 'flex',
            flexDirection: 'column',
        }}>

            <div style={{
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'center',
                alignItems: 'center'
            }}>
                <RadarChart
                    height={260}
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

                        metrics: labels,
                    }}
                />
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
            <Typography>
                Last 7 days energy usage
            </Typography>
        </div>
    );
}
