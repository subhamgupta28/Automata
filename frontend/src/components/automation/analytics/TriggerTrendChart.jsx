import React from 'react';
import { Paper, Typography, Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function TriggerTrendChart({ triggersByDay }) {
    const theme = useTheme();

    if (!triggersByDay || Object.keys(triggersByDay).length === 0) {
        return (
            <Paper
                sx={{
                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                    borderRadius: '12px',
                    padding: '20px',
                    border: `1px solid rgba(255, 216, 33, 0.2)`,
                    height: '300px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
            >
                <Typography variant="body2" sx={{ color: theme.palette.text.secondary }}>
                    No trigger data available
                </Typography>
            </Paper>
        );
    }

    // Transform data from Map/Object
    const chartData = Object.entries(triggersByDay || {}).map(([day, count]) => ({
        date: day,
        triggers: count,
    }));

    return (
        <Paper
            sx={{
                backgroundColor: 'rgba(255, 255, 255, 0.05)',
                borderRadius: '12px',
                padding: '20px',
                border: `1px solid rgba(255, 216, 33, 0.2)`,
            }}
        >
            <Typography
                variant="h6"
                sx={{
                    color: theme.palette.text.primary,
                    marginBottom: '16px',
                    fontWeight: 'bold',
                }}
            >
                Trigger Trend
            </Typography>
            <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 216, 33, 0.1)" />
                    <XAxis
                        dataKey="date"
                        stroke={theme.palette.text.secondary}
                        style={{ fontSize: '12px' }}
                    />
                    <YAxis stroke={theme.palette.text.secondary} style={{ fontSize: '12px' }} />
                    <Tooltip
                        contentStyle={{
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            border: `1px solid ${theme.palette.primary.main}`,
                            borderRadius: '8px',
                        }}
                        labelStyle={{ color: theme.palette.primary.main }}
                    />
                    <Legend />
                    <Line
                        type="monotone"
                        dataKey="triggers"
                        stroke={theme.palette.primary.main}
                        strokeWidth={2}
                        dot={{ fill: theme.palette.primary.main, r: 4 }}
                        activeDot={{ r: 6 }}
                    />
                </LineChart>
            </ResponsiveContainer>
        </Paper>
    );
}
