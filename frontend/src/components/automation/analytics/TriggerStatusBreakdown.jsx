import React from 'react';
import { Paper, Typography, Box, Stack, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { PieChart, Pie, Cell, Legend, Tooltip, ResponsiveContainer } from 'recharts';

export default function TriggerStatusBreakdown({
    triggered = 0,
    restored = 0,
    skipped = 0,
    notMet = 0,
    userOverride = 0,
}) {
    const theme = useTheme();

    const data = [
        { name: 'Triggered', value: triggered, color: '#42a5f5' },
        { name: 'Restored', value: restored, color: '#4caf50' },
        { name: 'Skipped', value: skipped, color: '#ffc107' },
        { name: 'Not Met', value: notMet, color: '#f44336' },
        { name: 'User Override', value: userOverride, color: '#9c27b0' },
    ].filter((item) => item.value > 0);

    if (data.length === 0) {
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
                    No execution data available
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
                Execution Status Breakdown
            </Typography>
            <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                    <Pie
                        data={data}
                        cx="50%"
                        cy="50%"
                        labelLine={false}
                        label={({ name, value }) => `${name}: ${value}`}
                        outerRadius={80}
                        fill="#8884d8"
                        dataKey="value"
                    >
                        {data.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                    </Pie>
                    <Tooltip
                        contentStyle={{
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            border: `1px solid ${theme.palette.primary.main}`,
                            borderRadius: '8px',
                        }}
                        labelStyle={{ color: theme.palette.primary.main }}
                    />
                </PieChart>
            </ResponsiveContainer>

            {/* Legend with counts */}
            <Stack spacing={1} sx={{ marginTop: '16px' }}>
                {data.map((item) => (
                    <Box key={item.name} sx={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Box sx={{ width: '12px', height: '12px', backgroundColor: item.color, borderRadius: '2px' }} />
                        <Typography variant="caption" sx={{ flex: 1, color: theme.palette.text.secondary }}>
                            {item.name}
                        </Typography>
                        <Typography variant="caption" sx={{ color: theme.palette.text.primary, fontWeight: 'bold' }}>
                            {item.value}
                        </Typography>
                    </Box>
                ))}
            </Stack>
        </Paper>
    );
}
