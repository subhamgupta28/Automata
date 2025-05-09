import {LineChart, lineElementClasses } from "@mui/x-charts";
import React, {useEffect, useState} from "react";
import {getDetailChartData} from "../../services/apis.jsx";

import {
    Card,
    CardContent,
    Typography,
    Box,
    Select,
    MenuItem,
    FormControl,
    InputLabel, ButtonGroup, Button, ToggleButton, ToggleButtonGroup,
} from "@mui/material";


export default function ChartDetail({ deviceId, name }) {
    const [data, setData] = useState([]);
    const [attributes, setAttributes] = useState([]);
    const [range, setRange] = useState("day");

    useEffect(() => {
        const fetchChartData = async () => {
            const d = await getDetailChartData(deviceId, range);
            setData(d.data);
            setAttributes(d.attributes);
        };
        fetchChartData();
    }, [deviceId, range]);

    const xLabels = data.map((item) => item.dateDay);

    const series = attributes.map((attr) => ({
        label: attr.charAt(0).toUpperCase() + attr.slice(1),
        data: data.map((item) => item[attr]),
        showMark: false,
    }));

    return (
        <Card elevation={1} sx={{ borderRadius: 3,  }}>
            <CardContent>
                <Box
                    display="flex"
                    justifyContent="space-between"
                    alignItems="center"
                    mb={2}
                    flexWrap="wrap"
                >
                    <Typography
                        variant="h6"
                        sx={{
                            fontWeight: 600,
                            color: "#90caf9",
                            mb: 2,
                        }}
                    >
                        {name}
                    </Typography>
                    <ToggleButtonGroup
                        color="primary"
                        size="small"
                        value={range}
                        exclusive
                        onChange={(e)=>setRange(e.target.value)}
                        aria-label="Platform"
                    >
                        <ToggleButton value="hour">Hour</ToggleButton>
                        <ToggleButton value="day">Day</ToggleButton>
                        <ToggleButton value="week">Week</ToggleButton>
                    </ToggleButtonGroup>
                </Box>

                <LineChart
                    height={300}
                    series={series}
                    yAxis={[{ position: 'none' }]}
                    xAxis={[{ scaleType: "band", data: xLabels }]}
                    sx={{
                        [`& .${lineElementClasses.root}`]: {
                            strokeWidth: 2,
                        },
                    }}
                />
            </CardContent>
        </Card>
    );
}
