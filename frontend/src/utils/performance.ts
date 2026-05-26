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
