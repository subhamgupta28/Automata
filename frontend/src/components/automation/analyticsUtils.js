/**
 * Analytics Cache and Processing Utilities
 * Reduces redundant API calls and optimizes data processing
 */

let analyticsCache = new Map();
const CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

export const getAnalyticsCacheKey = (days) => `analytics_overview_${days}`;

/**
 * Get cached analytics if available and not expired
 */
export const getCachedAnalytics = (days) => {
    const key = getAnalyticsCacheKey(days);
    const cached = analyticsCache.get(key);
    if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
        return cached.data;
    }
    return null;
};

/**
 * Set analytics in cache
 */
export const setCachedAnalytics = (days, data) => {
    const key = getAnalyticsCacheKey(days);
    analyticsCache.set(key, {
        data,
        timestamp: Date.now(),
    });
};

/**
 * Derive top performing automations from overview data (optimized)
 * Uses comparison directly instead of sorting entire array
 */
export const getTopPerformersFromData = (allAnalytics, limit = 5) => {
    if (!allAnalytics || allAnalytics.length === 0) return [];
    
    // For large datasets, use a min-heap approach instead of full sort
    if (allAnalytics.length > 1000) {
        return getTopN(allAnalytics, limit, (a, b) => b.triggeredCount - a.triggeredCount);
    }
    
    return allAnalytics
        .sort((a, b) => b.triggeredCount - a.triggeredCount)
        .slice(0, limit);
};

/**
 * Derive problematic automations from overview data (optimized)
 * Filters and sorts in one pass
 */
export const getProblematicFromData = (allAnalytics, successThreshold = 70) => {
    if (!allAnalytics || allAnalytics.length === 0) return [];
    
    return allAnalytics
        .filter((a) => a.successRate < successThreshold && a.totalEvaluations > 10)
        .sort((a, b) => a.successRate - b.successRate)
        .slice(0, 20); // Limit to prevent UI overload
};

/**
 * Get top N items efficiently for large datasets
 */
const getTopN = (array, n, compareFn) => {
    const heap = [];
    
    for (let i = 0; i < array.length; i++) {
        if (heap.length < n) {
            heap.push(array[i]);
            if (heap.length === n) {
                heap.sort(compareFn);
            }
        } else if (compareFn(array[i], heap[0]) > 0) {
            heap[0] = array[i];
            heap.sort(compareFn);
        }
    }
    
    return heap;
};

/**
 * Clear analytics cache
 */
export const clearAnalyticsCache = () => {
    analyticsCache.clear();
};

/**
 * Batch process analytics data for better memory management
 */
export const processBatchAnalytics = (allAnalytics, batchSize = 100) => {
    const batches = [];
    for (let i = 0; i < allAnalytics.length; i += batchSize) {
        batches.push(allAnalytics.slice(i, i + batchSize));
    }
    return batches;
};
