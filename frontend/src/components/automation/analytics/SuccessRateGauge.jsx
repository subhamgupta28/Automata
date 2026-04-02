import React from 'react';
import { Paper, Typography, Box, LinearProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';

export default function SuccessRateGauge({ successRate = 0 }) {
    const theme = useTheme();

    const getColor = (rate) => {
        if (rate >= 80) return '#4caf50'; // Green
        if (rate >= 60) return '#ffc107'; // Amber
        return '#e53935'; // Red
    };

    const color = getColor(successRate);

    return (
        <Paper
            sx={{
                backgroundColor: 'rgba(255, 255, 255, 0.05)',
                borderRadius: '12px',
                padding: '20px',
                border: `1px solid rgba(255, 216, 33, 0.2)`,
                height: '300px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
            }}
        >
            <Typography
                variant="caption"
                sx={{
                    color: theme.palette.text.secondary,
                    textTransform: 'uppercase',
                    letterSpacing: '0.5px',
                    marginBottom: '12px',
                }}
            >
                Success Rate
            </Typography>

            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginBottom: '20px',
                }}
            >
                <Typography
                    variant="h2"
                    sx={{
                        color: color,
                        fontWeight: 'bold',
                        fontSize: '3.5rem',
                    }}
                >
                    {successRate?.toFixed(1)}%
                </Typography>
            </Box>

            <Box sx={{ width: '100%' }}>
                <LinearProgress
                    variant="determinate"
                    value={Math.min(successRate, 100)}
                    sx={{
                        height: '10px',
                        borderRadius: '5px',
                        backgroundColor: 'rgba(255, 255, 255, 0.1)',
                        '& .MuiLinearProgress-bar': {
                            backgroundColor: color,
                        },
                    }}
                />
            </Box>

            <Typography
                variant="caption"
                sx={{
                    color: theme.palette.text.secondary,
                    marginTop: '12px',
                }}
            >
                {successRate >= 80 ? '✓ Excellent' : successRate >= 60 ? '⚠ Good' : '✗ Needs Attention'}
            </Typography>
        </Paper>
    );
}
