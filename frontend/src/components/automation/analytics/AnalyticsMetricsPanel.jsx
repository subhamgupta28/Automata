import React from 'react';
import { Paper, Typography, Box, Grid, Tooltip, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import InfoIcon from '@mui/icons-material/Info';

export default function AnalyticsMetricsPanel({ analytics }) {
    const theme = useTheme();

    const metrics = [
        {
            label: 'Total Evaluations',
            value: analytics.totalEvaluations || 0,
            tooltip: 'Total number of times this automation was evaluated',
            color: '#42a5f5',
        },
        {
            label: 'Triggered',
            value: analytics.triggeredCount || 0,
            tooltip: 'Number of times automation was successfully triggered',
            color: '#4caf50',
        },
        {
            label: 'Restored',
            value: analytics.restoredCount || 0,
            tooltip: 'Number of times automation was restored',
            color: '#2196f3',
        },
        {
            label: 'Skipped',
            value: analytics.skippedCount || 0,
            tooltip: 'Number of times automation was skipped',
            color: '#ffc107',
        },
        {
            label: 'Not Met',
            value: analytics.notMetCount || 0,
            tooltip: 'Number of times conditions were not met',
            color: '#f44336',
        },
        {
            label: 'User Override',
            value: analytics.userOverrideCount || 0,
            tooltip: 'Number of user interventions',
            color: '#9c27b0',
        },
        {
            label: 'Avg Conditions Passed',
            value: analytics.averageConditionsPassed?.toFixed(2) || '0.00',
            isDecimal: true,
            tooltip: 'Average conditions that passed per evaluation',
            color: '#00bcd4',
        },
    ];

    return (
        <Paper
            sx={{
                backgroundColor: 'rgba(255, 255, 255, 0.03)',
                borderRadius: '12px',
                padding: '20px',
                border: `1px solid rgba(255, 216, 33, 0.2)`,
                marginBottom: '20px',
            }}
        >
            <Typography
                variant="h6"
                sx={{
                    color: theme.palette.text.primary,
                    marginBottom: '20px',
                    fontWeight: 'bold',
                }}
            >
                Key Metrics
            </Typography>

            <Grid container spacing={2}>
                {metrics.map((metric, index) => (
                    <Grid item xs={6} sm={4} md={3} key={index}>
                        <Tooltip title={metric.tooltip}>
                            <Box
                                sx={{
                                    padding: '16px',
                                    backgroundColor: `${metric.color}15`,
                                    borderRadius: '8px',
                                    border: `1px solid ${metric.color}40`,
                                    display: 'flex',
                                    flexDirection: 'column',
                                    justifyContent: 'space-between',
                                    height: '100%',
                                }}
                            >
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                    <Typography
                                        variant="caption"
                                        sx={{
                                            color: theme.palette.text.secondary,
                                            fontSize: '11px',
                                            flex: 1,
                                        }}
                                    >
                                        {metric.label}
                                    </Typography>
                                    <InfoIcon
                                        sx={{
                                            fontSize: '14px',
                                            color: metric.color,
                                            opacity: 0.7,
                                        }}
                                    />
                                </Box>
                                <Typography
                                    variant="h5"
                                    sx={{
                                        color: metric.color,
                                        fontWeight: 'bold',
                                        marginTop: '8px',
                                    }}
                                >
                                    {metric.value}
                                </Typography>
                            </Box>
                        </Tooltip>
                    </Grid>
                ))}
            </Grid>
        </Paper>
    );
}
