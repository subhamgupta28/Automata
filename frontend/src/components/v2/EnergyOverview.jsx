import * as React from "react";
import { Card, CardContent, Typography } from "@mui/material";
import { BarChart } from "@mui/x-charts/BarChart";
import { LineChart } from "@mui/x-charts/LineChart";
import dayjs from "dayjs";

const EnergyOverview = ({ data }) => {
    const sorted = [...data].sort(
        (a, b) => new Date(a.timestamp) - new Date(b.timestamp)
    );

    const labels = sorted.map(d =>
        dayjs(d.timestamp).format("DD MMM")
    );

    const discharge = sorted.map(d => d.totalWh);
    const charge = sorted.map(d => d.chargeTotalWh);
    const percent = sorted.map(d => d.percent);

    return (
        <Card>
            <CardContent>
                <Typography variant="h6" gutterBottom>
                    Energy Overview — Last 7 Days
                </Typography>

                <BarChart
                    height={320}
                    xAxis={[{ scaleType: "band", data: labels }]}
                    series={[
                        {
                            data: discharge,
                            label: "Discharge (Wh)",
                            stack: "energy"
                        },
                        {
                            data: charge,
                            label: "Charge (Wh)",
                            stack: "energy"
                        }
                    ]}
                    margin={{ top: 20, bottom: 40, left: 60, right: 20 }}
                />

                <LineChart
                    height={200}
                    xAxis={[{ scaleType: "band", data: labels }]}
                    series={[
                        {
                            data: percent,
                            label: "Battery %",
                            valueFormatter: (v) => `${v}%`
                        }
                    ]}
                    margin={{ top: 10, bottom: 30, left: 60, right: 20 }}
                />
            </CardContent>
        </Card>
    );
};

export default EnergyOverview;
