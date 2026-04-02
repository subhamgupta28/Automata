import React, {useEffect, useRef, useState} from 'react';
import {
    Alert,
    Backdrop,
    Box,
    Button,
    ButtonGroup,
    Chip,
    CircularProgress,
    Grid,
    IconButton,
    LinearProgress,
    Tab,
    Tabs,
    Tooltip,
    Typography,
} from '@mui/material';
import {useTheme} from '@mui/material/styles';
import RefreshIcon from '@mui/icons-material/Refresh';
import {getAutomationAnalyticsOverview,} from '../../services/apis.jsx';
import AutomationAnalyticsCard from './AutomationAnalyticsCard.jsx';
import AutomationAnalyticsDetail from './AutomationAnalyticsDetail.jsx';
import AnalyticsCardSkeleton from './AnalyticsCardSkeleton.jsx';
import {
    clearAnalyticsCache,
    getCachedAnalytics,
    getProblematicFromData,
    getTopPerformersFromData,
    setCachedAnalytics,
} from './analyticsUtils.js';

function CustomTabPanel(props) {
    const {children, value, index, ...other} = props;
    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`analytics-tabpanel-${index}`}
            aria-labelledby={`analytics-tab-${index}`}
            {...other}
        >
            {value === index && <Box sx={{p: 2}}>{children}</Box>}
        </div>
    );
}

export default function AutomationAnalyticsView() {
    const theme = useTheme();
    const [loading, setLoading] = useState(false);
    const [allAnalytics, setAllAnalytics] = useState([]);
    const [topPerforming, setTopPerforming] = useState([]);
    const [problematic, setProblematic] = useState([]);
    const [daysBack, setDaysBack] = useState(7);
    const [tabIndex, setTabIndex] = useState(0);
    const [selectedAutomation, setSelectedAutomation] = useState(null);
    const [error, setError] = useState(null);
    const [loadingProgress, setLoadingProgress] = useState(0);
    const abortControllerRef = useRef(null);

    const fetchAnalytics = async (days) => {
        // Check cache first
        const cached = getCachedAnalytics(days);
        if (cached) {
            setAllAnalytics(cached);
            setTopPerforming(getTopPerformersFromData(cached, 5));
            setProblematic(getProblematicFromData(cached, 70));
            return;
        }

        setLoading(true);
        setError(null);
        setLoadingProgress(0);
        abortControllerRef.current = new AbortController();

        try {
            // Show progress
            setLoadingProgress(20);

            // Only fetch overview once - derive top and problematic from it
            const all = await getAutomationAnalyticsOverview(days);

            setLoadingProgress(100);

            if (!all || all.length === 0) {
                setAllAnalytics([]);
                setTopPerforming([]);
                setProblematic([]);
                return;
            }

            // Cache the data
            setCachedAnalytics(days, all);

            // Derive top and problematic from the fetched data
            setAllAnalytics(all);
            setTopPerforming(getTopPerformersFromData(all, 5));
            setProblematic(getProblematicFromData(all, 70));
        } catch (err) {
            if (err.name !== 'AbortError') {
                console.error('Failed to fetch analytics:', err);
                setError('Failed to load automation analytics. Please try again.');
            }
        } finally {
            setLoading(false);
            setLoadingProgress(0);
            abortControllerRef.current = null;
        }
    };

    useEffect(() => {
        fetchAnalytics(daysBack);

        return () => {
            if (abortControllerRef.current) {
                abortControllerRef.current.abort();
            }
        };
    }, [daysBack]);

    const handleDaysChange = (days) => {
        setDaysBack(days);
        setTabIndex(0);
        setSelectedAutomation(null);
    };

    const handleSelectAutomation = (automation) => {
        setSelectedAutomation(automation);
        setTabIndex(2);
    };

    const handleRefresh = () => {
        clearAnalyticsCache();
        fetchAnalytics(daysBack);
    };

    return (
        <Box
            sx={{
                minHeight: '100vh',
                paddingTop: '60px',
                paddingBottom: '20px',
                color: theme.palette.text.primary,
                background: 'transparent',
            }}
        >
            {/* Header Section */}
            <Box
                sx={{
                    marginBottom: '20px',
                    paddingX: '20px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '20px',
                }}
            >
                <Box>
                    <Typography
                        variant="h2"
                        sx={{
                            color: theme.palette.primary.main,
                            fontWeight: 'bold',
                            marginBottom: '10px',
                        }}
                    >
                        Automation Analytics
                    </Typography>
                    <Typography variant="body2" sx={{color: theme.palette.text.secondary}}>
                        Monitor automation performance and execution metrics
                    </Typography>
                </Box>

                <Box sx={{display: 'flex', gap: '10px', alignItems: 'center'}}>
                    {/* Time Period Buttons */}
                    <ButtonGroup variant="outlined" size="small">
                        {[7, 14, 30].map((days) => (
                            <Button
                                key={days}
                                onClick={() => handleDaysChange(days)}
                                variant={daysBack === days ? 'contained' : 'outlined'}
                                sx={{
                                    backgroundColor:
                                        daysBack === days
                                            ? theme.palette.primary.main
                                            : 'transparent',
                                    color:
                                        daysBack === days
                                            ? '#000'
                                            : theme.palette.primary.main,
                                    borderColor: theme.palette.primary.main,
                                    '&:hover': {
                                        backgroundColor:
                                            daysBack === days
                                                ? theme.palette.primary.main
                                                : 'rgba(255, 216, 33, 0.1)',
                                    },
                                }}
                            >
                                Last {days}d
                            </Button>
                        ))}
                    </ButtonGroup>

                    {/* Refresh Button */}
                    <Tooltip title="Refresh data">
                        <IconButton
                            onClick={handleRefresh}
                            disabled={loading}
                            sx={{
                                color: theme.palette.primary.main,
                                border: `1px solid ${theme.palette.primary.main}`,
                                borderRadius: '4px',
                                '&:hover': {
                                    backgroundColor: 'rgba(255, 216, 33, 0.1)',
                                },
                                animation: loading ? 'spin 1s linear infinite' : 'none',
                                '@keyframes spin': {
                                    '0%': {transform: 'rotate(0deg)'},
                                    '100%': {transform: 'rotate(360deg)'},
                                },
                            }}
                        >
                            <RefreshIcon/>
                        </IconButton>
                    </Tooltip>
                </Box>
            </Box>

            {/* Error Alert */}
            {error && (
                <Alert severity="error" sx={{marginX: '20px', marginBottom: '20px'}}>
                    {error}
                </Alert>
            )}

            {/* Loading Progress */}
            {loading && loadingProgress > 0 && (
                <Box sx={{marginX: '20px', marginBottom: '20px'}}>
                    <LinearProgress
                        variant="determinate"
                        value={loadingProgress}
                        sx={{
                            height: '4px',
                            borderRadius: '2px',
                            backgroundColor: 'rgba(255, 216, 33, 0.1)',
                            '& .MuiLinearProgress-bar': {
                                backgroundColor: theme.palette.primary.main,
                            },
                        }}
                    />
                </Box>
            )}

            {/* Tabs */}
            <Box
                sx={{
                    borderBottom: '1px solid rgba(255, 216, 33, 0.1)',
                    marginBottom: '20px',
                    marginRight: '20px',
                    marginLeft: '20px',
                    background: 'rgba(255, 255, 255, 0.05)',
                    borderRadius: '8px 8px 0 0',
                }}
            >
                <Tabs
                    value={tabIndex}
                    onChange={(e, newValue) => setTabIndex(newValue)}
                    aria-label="analytics tabs"
                >
                    <Tab label={`All Automations (${allAnalytics.length})`}/>
                    <Tab label={`Top Performing (${topPerforming.length})`}/>
                    <Tab label="Problematic Automations"/>
                    {selectedAutomation && <Tab label={`Details: ${selectedAutomation.automationName}`}/>}
                </Tabs>
            </Box>

            {/* Tab Content */}
            <Box sx={{marginX: '20px', overflow: 'auto'}}>
                {/* All Automations Tab */}
                <CustomTabPanel value={tabIndex} index={0}>
                    {loading ? (
                        <Grid container spacing={2}>
                            {[...Array(6)].map((_, i) => (
                                <Grid item xs={12} sm={6} md={4} key={i}>
                                    <AnalyticsCardSkeleton/>
                                </Grid>
                            ))}
                        </Grid>
                    ) : allAnalytics.length > 0 ? (
                        <Grid container spacing={2}>
                            {allAnalytics.map((automation) => (
                                <Grid item xs={12} sm={6} md={4} key={automation.automationId}>
                                    <Box
                                        onClick={() => handleSelectAutomation(automation)}
                                        sx={{cursor: 'pointer'}}
                                    >
                                        <AutomationAnalyticsCard analytics={automation}/>
                                    </Box>
                                </Grid>
                            ))}
                        </Grid>
                    ) : (
                        <Typography variant="body1" sx={{textAlign: 'center', paddingY: '40px'}}>
                            No automation data available for the selected period.
                        </Typography>
                    )}
                </CustomTabPanel>

                {/* Top Performing Tab */}
                <CustomTabPanel value={tabIndex} index={1}>
                    {loading ? (
                        <Grid container spacing={2}>
                            {[...Array(5)].map((_, i) => (
                                <Grid item xs={12} sm={6} md={4} key={i}>
                                    <AnalyticsCardSkeleton/>
                                </Grid>
                            ))}
                        </Grid>
                    ) : topPerforming.length > 0 ? (
                        <Grid container spacing={2}>
                            {topPerforming.map((automation, index) => (
                                <Grid item xs={12} sm={6} md={4} key={automation.automationId}>
                                    <Box
                                        onClick={() => handleSelectAutomation(automation)}
                                        sx={{
                                            cursor: 'pointer',
                                            position: 'relative',
                                        }}
                                    >
                                        <Chip
                                            label={`#${index + 1}`}
                                            sx={{
                                                position: 'absolute',
                                                top: '10px',
                                                right: '10px',
                                                backgroundColor: theme.palette.primary.main,
                                                color: '#000',
                                                fontWeight: 'bold',
                                                zIndex: 10,
                                            }}
                                        />
                                        <AutomationAnalyticsCard analytics={automation} highlight/>
                                    </Box>
                                </Grid>
                            ))}
                        </Grid>
                    ) : (
                        <Typography variant="body1" sx={{textAlign: 'center', paddingY: '40px'}}>
                            No automation data available for the selected period.
                        </Typography>
                    )}
                </CustomTabPanel>

                {/* Problematic Automations Tab */}
                <CustomTabPanel value={tabIndex} index={2}>
                    {loading ? (
                        <Grid container spacing={2}>
                            {[...Array(4)].map((_, i) => (
                                <Grid item xs={12} sm={6} md={4} key={i}>
                                    <AnalyticsCardSkeleton/>
                                </Grid>
                            ))}
                        </Grid>
                    ) : problematic.length > 0 ? (
                        <Grid container spacing={2}>
                            {problematic.map((automation) => (
                                <Grid item xs={12} sm={6} md={4} key={automation.automationId}>
                                    <Box
                                        onClick={() => handleSelectAutomation(automation)}
                                        sx={{cursor: 'pointer'}}
                                    >
                                        <AutomationAnalyticsCard analytics={automation} warning/>
                                    </Box>
                                </Grid>
                            ))}
                        </Grid>
                    ) : (
                        <Typography variant="body1" sx={{textAlign: 'center', paddingY: '40px', color: 'green'}}>
                            All automations are performing well! ✓
                        </Typography>
                    )}
                </CustomTabPanel>

                {/* Detail Tab */}
                {selectedAutomation && (
                    <CustomTabPanel value={tabIndex} index={3}>
                        <AutomationAnalyticsDetail
                            automationId={selectedAutomation.automationId}
                            daysBack={daysBack}
                        />
                    </CustomTabPanel>
                )}
            </Box>

            {/* Backdrop Loading */}
            <Backdrop
                sx={(theme) => ({color: '#fff', zIndex: theme.zIndex.drawer + 1})}
                open={false}
            >
                <CircularProgress color="inherit"/>
            </Backdrop>
        </Box>
    );
}
