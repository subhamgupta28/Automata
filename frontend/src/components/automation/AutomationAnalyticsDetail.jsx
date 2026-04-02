import React, { useEffect, useState } from 'react';
import {
    Box,
    Grid,
    Typography,
    CircularProgress,
    Alert,
    Paper,
    Stack,
    Chip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { getAutomationAnalytics } from '../../services/apis.jsx';
import TriggerTrendChart from './analytics/TriggerTrendChart.jsx';
import SuccessRateGauge from './analytics/SuccessRateGauge.jsx';
import TriggerStatusBreakdown from './analytics/TriggerStatusBreakdown.jsx';
import HourlyDistributionChart from './analytics/HourlyDistributionChart.jsx';
import FailureReasonsChart from './analytics/FailureReasonsChart.jsx';
import DeviceImpactChart from './analytics/DeviceImpactChart.jsx';
import AnalyticsMetricsPanel from './analytics/AnalyticsMetricsPanel.jsx';

export default function AutomationAnalyticsDetail({ automationId, daysBack = 7 }) {
    const theme = useTheme();
    const [analytics, setAnalytics] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchAnalytics = async () => {
            setLoading(true);
            setError(null);
            try {
                const data = await getAutomationAnalytics(automationId, daysBack);
                setAnalytics(data);
            } catch (err) {
                console.error('Failed to fetch automation analytics:', err);
                setError('Failed to load detailed analytics for this automation.');
            } finally {
                setLoading(false);
            }
        };

        if (automationId) {
            fetchAnalytics();
        }
    }, [automationId, daysBack]);

    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '400px' }}>
                <CircularProgress />
            </Box>
        );
    }

    if (error || !analytics) {
        return <Alert severity="error">{error || 'No data available'}</Alert>;
    }

    return (
        <Box sx={{ width: '100%' }}>
            {/* Automation Header */}
            <Paper
                sx={{
                    backgroundColor: 'rgba(255, 255, 255, 0.05)',
                    borderRadius: '12px',
                    padding: '20px',
                    marginBottom: '20px',
                    border: `1px solid rgba(255, 216, 33, 0.2)`,
                }}
            >
                <Stack spacing={2}>
                    <Box>
                        <Typography variant="h5" sx={{ color: theme.palette.primary.main, fontWeight: 'bold' }}>
                            {analytics.automationName}
                        </Typography>
                        <Typography variant="body2" sx={{ color: theme.palette.text.secondary, marginTop: '4px' }}>
                            ID: {analytics.automationId}
                        </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                        {analytics.isCurrentlyActive && (
                            <Chip label="Currently Active" sx={{ backgroundColor: '#4caf50', color: '#fff' }} />
                        )}
                        <Chip label={`Last ${daysBack} days`} variant="outlined" />
                        <Chip label={`${analytics.totalEvaluations} evaluations`} variant="outlined" />
                    </Box>
                </Stack>
            </Paper>

            {/* Key Metrics */}
            <AnalyticsMetricsPanel analytics={analytics} />

            {/* Charts Grid */}
            <Grid container spacing={3} sx={{ marginTop: '10px' }}>
                {/* Success Rate Gauge */}
                <Grid item xs={12} sm={6} md={4}>
                    <SuccessRateGauge successRate={analytics.successRate} />
                </Grid>

                {/* Trigger Status Breakdown */}
                <Grid item xs={12} sm={6} md={4}>
                    <TriggerStatusBreakdown
                        triggered={analytics.triggeredCount}
                        restored={analytics.restoredCount}
                        skipped={analytics.skippedCount}
                        notMet={analytics.notMetCount}
                        userOverride={analytics.userOverrideCount}
                    />
                </Grid>

                {/* Trigger Count */}
                <Grid item xs={12} sm={6} md={4}>
                    <Paper
                        sx={{
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                            borderRadius: '12px',
                            padding: '20px',
                            border: `1px solid rgba(255, 216, 33, 0.2)`,
                            height: '100%',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'center',
                        }}
                    >
                        <Typography
                            variant="caption"
                            sx={{
                                color: theme.palette.text.secondary,
                                textTransform: 'uppercase',
                                letterSpacing: '0.5px',
                            }}
                        >
                            Total Triggered
                        </Typography>
                        <Typography
                            variant="h3"
                            sx={{
                                color: theme.palette.primary.main,
                                fontWeight: 'bold',
                                marginTop: '8px',
                            }}
                        >
                            {analytics.triggeredCount}
                        </Typography>
                        <Typography variant="body2" sx={{ color: theme.palette.text.secondary, marginTop: '8px' }}>
                            Successful executions
                        </Typography>
                    </Paper>
                </Grid>

                {/* Trigger Trend Chart */}
                <Grid item xs={12} md={6}>
                    <TriggerTrendChart triggersByDay={analytics.triggersByDay} />
                </Grid>

                {/* Most Common Times */}
                <Grid item xs={12} md={6}>
                    <HourlyDistributionChart mostCommonTimes={analytics.mostCommonTriggerTimes} />
                </Grid>

                {/* Failure Reasons */}
                {analytics.failureReasons && analytics.failureReasons.length > 0 && (
                    <Grid item xs={12} md={6}>
                        <FailureReasonsChart failureReasons={analytics.failureReasons} />
                    </Grid>
                )}

                {/* Affected Devices */}
                {analytics.affectedDevices && analytics.affectedDevices.length > 0 && (
                    <Grid item xs={12} md={6}>
                        <DeviceImpactChart affectedDevices={analytics.affectedDevices} />
                    </Grid>
                )}
            </Grid>
        </Box>
    );
}
