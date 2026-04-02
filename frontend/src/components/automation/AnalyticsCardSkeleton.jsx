import React from 'react';
import { Grid, Card, Box, Skeleton } from '@mui/material';

export default function AnalyticsCardSkeleton() {
    return (
        <Card
            sx={{
                backgroundColor: 'rgba(255, 255, 255, 0.05)',
                borderRadius: '12px',
                border: '2px solid rgba(255, 216, 33, 0.2)',
                padding: '16px',
            }}
        >
            <Skeleton variant="text" width="80%" height={24} sx={{ marginBottom: '12px' }} />
            <Skeleton variant="rectangular" width="100%" height={6} sx={{ marginBottom: '12px', borderRadius: '3px' }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <Skeleton variant="text" width="40%" height={16} />
                <Skeleton variant="text" width="30%" height={16} />
            </Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <Skeleton variant="text" width="40%" height={16} />
                <Skeleton variant="text" width="30%" height={16} />
            </Box>
            <Box sx={{ borderTop: '1px solid rgba(255, 216, 33, 0.1)', myMargin: '12px' }} />
            <Box sx={{ display: 'flex', justifyContent: 'space-between', marginTop: '12px' }}>
                <Skeleton variant="text" width="35%" height={14} />
                <Skeleton variant="text" width="35%" height={14} />
            </Box>
        </Card>
    );
}
