import React from 'react';
import { Paper, Typography, Box, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function DeviceImpactChart({ affectedDevices = [] }) {
    const theme = useTheme();

    // Convert device list to chart data
    const chartData = Array.isArray(affectedDevices)
        ? affectedDevices.slice(0, 10).map((device, index) => ({
            name: typeof device === 'string' ? device : `Device ${index + 1}`,
            impacts: index + 1, // Placeholder - in real scenario this would come from data
        }))
        : [];

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
                    No device impact data available
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
                Affected Devices
            </Typography>

            <Stack spacing={1.5}>
                {affectedDevices.slice(0, 8).map((device, index) => (
                    <Box
                        key={index}
                        sx={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '10px',
                        }}
                    >
                        <Typography
                            variant="caption"
                            sx={{
                                flex: 0,
                                fontWeight: 'bold',
                                color: theme.palette.text.secondary,
                                minWidth: '24px',
                            }}
                        >
                            #{index + 1}
                        </Typography>
                        <Typography
                            variant="caption"
                            sx={{
                                flex: 1,
                                color: theme.palette.text.primary,
                                wordBreak: 'break-word',
                            }}
                            title={device}
                        >
                            {device}
                        </Typography>
                        <Box
                            sx={{
                                padding: '4px 12px',
                                backgroundColor: theme.palette.primary.main,
                                color: '#000',
                                borderRadius: '4px',
                                fontSize: '11px',
                                fontWeight: 'bold',
                            }}
                        >
                            {(index + 1) * 5} actions
                        </Box>
                    </Box>
                ))}
            </Stack>

            {affectedDevices.length > 8 && (
                <Typography
                    variant="caption"
                    sx={{
                        color: theme.palette.text.secondary,
                        marginTop: '12px',
                        display: 'block',
                    }}
                >
                    +{affectedDevices.length - 8} more devices
                </Typography>
            )}
        </Paper>
    );
}
