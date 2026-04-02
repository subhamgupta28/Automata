import React from 'react';
import {
    Card,
    CardContent,
    Typography,
    Box,
    LinearProgress,
    Chip,
    Stack,
    Tooltip,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import InfoIcon from '@mui/icons-material/Info';

export default function AutomationAnalyticsCard({ analytics, highlight = false, warning = false }) {
    const theme = useTheme();

    // Color coding for success rate
    const getSuccessColor = (rate) => {
        if (rate >= 80) return '#42a5f5'; // Blue - Good
        if (rate >= 60) return '#ffc107'; // Amber - Okay
        return '#e53935'; // Red - Poor
    };

    // Format date
    const formatDate = (date) => {
        if (!date) return 'Never';
        const d = new Date(date);
        const now = new Date();
        const diffMs = now - d;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        if (diffHours < 24) return `${diffHours}h ago`;
        if (diffDays < 7) return `${diffDays}d ago`;
        return d.toLocaleDateString();
    };

    const successColor = getSuccessColor(analytics.successRate);
    const cardBgColor = warning
        ? 'rgba(229, 57, 53, 0.1)'
        : highlight
        ? 'rgba(255, 216, 33, 0.1)'
        : 'rgba(255, 255, 255, 0.05)';

    const borderColor = warning
        ? '#e53935'
        : highlight
        ? theme.palette.primary.main
        : 'rgba(255, 216, 33, 0.2)';

    return (
        <Card
            sx={{
                backgroundColor: cardBgColor,
                borderRadius: '12px',
                border: `2px solid ${borderColor}`,
                transition: 'all 0.3s ease',
                cursor: 'pointer',
                '&:hover': {
                    backgroundColor: warning
                        ? 'rgba(229, 57, 53, 0.15)'
                        : highlight
                        ? 'rgba(255, 216, 33, 0.15)'
                        : 'rgba(255, 255, 255, 0.08)',
                    transform: 'translateY(-4px)',
                    boxShadow: `0 8px 24px rgba(255, 216, 33, 0.2)`,
                },
            }}
        >
            <CardContent sx={{ padding: '16px' }}>
                {/* Header */}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                    <Typography
                        variant="h6"
                        sx={{
                            color: theme.palette.text.primary,
                            fontWeight: 'bold',
                            wordBreak: 'break-word',
                            flex: 1,
                        }}
                    >
                        {analytics.automationName}
                    </Typography>
                    {analytics.isCurrentlyActive && (
                        <Chip
                            label="Active"
                            size="small"
                            sx={{
                                backgroundColor: '#4caf50',
                                color: '#fff',
                                fontWeight: 'bold',
                                marginLeft: '8px',
                            }}
                        />
                    )}
                </Box>

                {/* Status Indicators */}
                <Stack direction="row" spacing={1} sx={{ marginBottom: '12px' }}>
                    {warning && (
                        <Tooltip title="Low success rate">
                            <Chip
                                icon={<TrendingDownIcon />}
                                label="Low Success"
                                size="small"
                                sx={{
                                    backgroundColor: 'rgba(229, 57, 53, 0.2)',
                                    color: '#e53935',
                                    fontWeight: 'bold',
                                }}
                            />
                        </Tooltip>
                    )}
                    {highlight && (
                        <Tooltip title="High performance">
                            <Chip
                                icon={<TrendingUpIcon />}
                                label="Top Performer"
                                size="small"
                                sx={{
                                    backgroundColor: `rgba(255, 216, 33, 0.2)`,
                                    color: theme.palette.primary.main,
                                    fontWeight: 'bold',
                                }}
                            />
                        </Tooltip>
                    )}
                </Stack>

                {/* Success Rate */}
                <Box sx={{ marginBottom: '12px' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                        <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
                            Success Rate
                        </Typography>
                        <Typography
                            variant="caption"
                            sx={{
                                color: successColor,
                                fontWeight: 'bold',
                            }}
                        >
                            {analytics.successRate?.toFixed(1)}%
                        </Typography>
                    </Box>
                    <LinearProgress
                        variant="determinate"
                        value={analytics.successRate || 0}
                        sx={{
                            height: '6px',
                            borderRadius: '3px',
                            backgroundColor: 'rgba(255, 255, 255, 0.1)',
                            '& .MuiLinearProgress-bar': {
                                backgroundColor: successColor,
                            },
                        }}
                    />
                </Box>

                {/* Key Metrics */}
                <Stack spacing={1} sx={{ marginBottom: '12px' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
                            Total Evaluations:
                        </Typography>
                        <Typography variant="caption" sx={{ color: theme.palette.text.primary, fontWeight: 'bold' }}>
                            {analytics.totalEvaluations || 0}
                        </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
                            Triggered:
                        </Typography>
                        <Typography variant="caption" sx={{ color: '#42a5f5', fontWeight: 'bold' }}>
                            {analytics.triggeredCount || 0}
                        </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                        <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
                            Condition Pass Rate:
                        </Typography>
                        <Typography variant="caption" sx={{ color: theme.palette.text.primary, fontWeight: 'bold' }}>
                            {analytics.averageConditionsPassed?.toFixed(2) || '0.00'}
                        </Typography>
                    </Box>
                </Stack>

                {/* Divider */}
                <Box sx={{ borderTop: '1px solid rgba(255, 216, 33, 0.1)', marginBottom: '12px' }} />

                {/* Footer */}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
                        Last triggered:
                    </Typography>
                    <Typography variant="caption" sx={{ color: theme.palette.text.primary }}>
                        {formatDate(analytics.lastTriggered)}
                    </Typography>
                </Box>
            </CardContent>
        </Card>
    );
}
