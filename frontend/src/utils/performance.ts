import logger from '@/utils/logger'

interface Metric {
    name: string
    value: number
    delta: number
    id: string
    entries: PerformanceEntry[]
}

type ReportHandler = (metric: Metric) => void

export function reportWebVitals(onPerfEntry?: ReportHandler) {
    if (onPerfEntry && onPerfEntry instanceof Function) {
        import('web-vitals').then(({ onCLS, onFCP, onLCP, onTTFB, onINP }) => {
            onCLS(onPerfEntry)
            onFCP(onPerfEntry)
            onLCP(onPerfEntry)
            onTTFB(onPerfEntry)
            onINP(onPerfEntry)
        }).catch(() => {
            // Ignore if web-vitals is unavailable.
        })
    }
}

export function logMetric(metric: Metric) {
    logger.info(`[Web Vitals] ${metric.name}:`, {
        value: metric.value,
        delta: metric.delta,
        id: metric.id
    })
}

export function measureApiResponse(url: string, startTime: number) {
    const duration = performance.now() - startTime
    if (duration > 1000) {
        logger.warn(`[Performance] Slow API response: ${url} took ${duration.toFixed(2)}ms`)
    }
    return duration
}

export function measurePageLoad() {
    if (typeof window !== 'undefined' && window.performance) {
        const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming
        if (navigation) {
            return {
                domContentLoaded: navigation.domContentLoadedEventEnd - navigation.domContentLoadedEventStart,
                loadComplete: navigation.loadEventEnd - navigation.loadEventStart,
                total: navigation.loadEventEnd - navigation.fetchStart
            }
        }
    }
    return null
}
