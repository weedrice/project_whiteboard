import logger from '@/utils/logger'

interface Metric {
    name: string
    value: number
    delta: number
    id: string
    entries: PerformanceEntry[]
}

type ReportHandler = (metric: Metric) => void

export function serializeMetric(metric: Metric) {
    return {
        name: metric.name,
        value: metric.value,
        delta: metric.delta,
        id: metric.id,
    }
}

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
    const endpoint = import.meta.env.VITE_WEB_VITALS_ENDPOINT?.trim()
    const payload = serializeMetric(metric)

    if (endpoint && typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
        navigator.sendBeacon(endpoint, new Blob([JSON.stringify(payload)], { type: 'application/json' }))
        return
    }

    if (import.meta.env.DEV) {
        logger.info(`[Web Vitals] ${metric.name}:`, payload)
    }
}
