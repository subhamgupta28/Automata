import React from 'react';
import { Paper, Typography, Box, Stack, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function HourlyDistributionChart({ mostCommonTimes = [] }) {
    const theme = useTheme();

    // Parse the most common times strings and convert to chart data
    const chartData = (mostCommonTimes || []).slice(0, 10).map((timeStr, index) => {
        // Expected format: "14:00 (5 times)" or similar
        const match = timeStr.match(/(\d{1,2}):(\d{2})\s*\((\d+)\s*times?\)/);
        if (match) {
            return {
                time: `${match[1]}:${match[2]}`,
                count: parseInt(match[3], 10),
                original: timeStr,
            };
        }
        return null;
    }).filter(Boolean);

    if (chartData.length === 0) {
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
                    No timing data available
                </Typography>
            </Paper>
        );
    }

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
                Most Common Trigger Times
            </Typography>
            <ResponsiveContainer width="100%" height={300}>
                <BarChart data={chartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 216, 33, 0.1)" />
                    <XAxis
                        dataKey="time"
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
                    <Bar dataKey="count" fill={theme.palette.primary.main} radius={[8, 8, 0, 0]} />
                </BarChart>
            </ResponsiveContainer>
        </Paper>
    );
}
