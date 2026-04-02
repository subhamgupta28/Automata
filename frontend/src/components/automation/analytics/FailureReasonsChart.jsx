import React from 'react';
import { Paper, Typography, Box, Stack, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, Legend, ResponsiveContainer } from 'recharts';

export default function FailureReasonsChart({ failureReasons = [] }) {
    const theme = useTheme();

    // Parse failure reasons - expected format: "Condition failed (15)", "Timeout (8)", etc.
    const chartData = (failureReasons || []).map((reasonStr) => {
        const match = reasonStr.match(/(.+?)\s*\((\d+)\)/);
        if (match) {
            return {
                reason: match[1].trim(),
                count: parseInt(match[2], 10),
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
                    No failure data available
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
                Top Failure Reasons
            </Typography>
            <ResponsiveContainer width="100%" height={300}>
                <BarChart data={chartData} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 216, 33, 0.1)" />
                    <XAxis type="number" stroke={theme.palette.text.secondary} style={{ fontSize: '12px' }} />
                    <YAxis
                        dataKey="reason"
                        type="category"
                        stroke={theme.palette.text.secondary}
                        style={{ fontSize: '11px' }}
                        width={100}
                    />
                    <RechartsTooltip
                        contentStyle={{
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            border: `1px solid ${theme.palette.primary.main}`,
                            borderRadius: '8px',
                        }}
                        labelStyle={{ color: theme.palette.primary.main }}
                    />
                    <Bar
                        dataKey="count"
                        fill="#e53935"
                        radius={[0, 8, 8, 0]}
                        name="Failures"
                    />
                </BarChart>
            </ResponsiveContainer>
        </Paper>
    );
}
