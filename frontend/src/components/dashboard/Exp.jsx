import React from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { ToggleButton, ToggleButtonGroup, Card, CardContent, Typography, Box } from '@mui/material';

const data = [
    { year: '2015', total: 5, avg: 10 },
    { year: '2016', total: 40, avg: 30 },
    { year: '2017', total: 35, avg: 70 },
    { year: '2018', total: 60, avg: 65 },
    { year: '2019', total: 10, avg: 20 },
    { year: '2020', total: 75, avg: 35 },
    { year: '2021', total: 30, avg: 75 },
    { year: '2022', total: 20, avg: 65 },
];

export default function Exp() {
    const [view, setView] = React.useState('year');

    const handleViewChange = (event, newView) => {
        if (newView !== null) setView(newView);
    };

    return (
        <Card sx={{ backgroundColor: '#0f0f1b', color: 'white', p: 2, borderRadius: 4, boxShadow: 3 }}>
            <CardContent>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                    <Typography variant="h6">Energy Consumption</Typography>
                    <ToggleButtonGroup value={view} exclusive onChange={handleViewChange}>
                        <ToggleButton value="day">Day</ToggleButton>
                        <ToggleButton value="month">Month</ToggleButton>
                        <ToggleButton value="year">Year</ToggleButton>
                    </ToggleButtonGroup>
                </Box>
                <ResponsiveContainer width="100%" height={300}>
                    <LineChart data={data} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                        <defs>
                            <linearGradient id="colorTotal" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#4d5dfb" stopOpacity={0.8} />
                                <stop offset="95%" stopColor="#4d5dfb" stopOpacity={0} />
                            </linearGradient>
                            <linearGradient id="colorAvg" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#b38aff" stopOpacity={0.8} />
                                <stop offset="95%" stopColor="#b38aff" stopOpacity={0} />
                            </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#2c2c3a" />
                        <XAxis dataKey="year" stroke="#ccc" />
                        <YAxis stroke="#ccc" />
                        <Tooltip contentStyle={{ backgroundColor: '#1f1f2e', borderColor: '#4d5dfb' }} labelStyle={{ color: '#fff' }} />
                        <Legend verticalAlign="top" height={36} iconType="circle" wrapperStyle={{ color: 'white' }} />
                        <Line type="monotone" dataKey="total" stroke="#4d5dfb" fillOpacity={1} fill="url(#colorTotal)" dot={{ r: 4 }} activeDot={{ r: 6 }} name="Total" />
                        <Line type="monotone" dataKey="avg" stroke="#b38aff" fillOpacity={1} fill="url(#colorAvg)" dot={{ r: 4 }} activeDot={{ r: 6 }} name="Per 1 Utility Avg." />
                    </LineChart>
                </ResponsiveContainer>
            </CardContent>
        </Card>
    );
}
