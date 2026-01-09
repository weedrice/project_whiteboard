/**
 * Web Vitals 및 성능 모니터링 유틸리티
 */

interface Metric {
    name: string
    value: number
    delta: number
    id: string
    entries: PerformanceEntry[]
}

type ReportHandler = (metric: Metric) => void

/**
 * Web Vitals 메트릭을 수집하고 보고하는 함수
 */
export function reportWebVitals(onPerfEntry?: ReportHandler) {
    if (onPerfEntry && onPerfEntry instanceof Function) {
        import('web-vitals').then(({ onCLS, onFID, onFCP, onLCP, onTTFB, onINP }) => {
            onCLS(onPerfEntry)
            onFID(onPerfEntry)
            onFCP(onPerfEntry)
            onLCP(onPerfEntry)
            onTTFB(onPerfEntry)
            onINP(onPerfEntry)
        }).catch(() => {
            // web-vitals가 없는 경우 무시 (개발 환경 등)
        })
    }
}

/**
 * 성능 메트릭을 로깅하는 함수
 */
export function logMetric(metric: Metric) {
    // logger를 동적으로 import하여 순환 참조 방지
    import('@/utils/logger').then(({ default: logger }) => {
        logger.info(`[Web Vitals] ${metric.name}:`, {
            value: metric.value,
            delta: metric.delta,
            id: metric.id
        })
    }).catch(() => {
        // logger를 사용할 수 없는 경우 console 사용
        console.log(`[Web Vitals] ${metric.name}:`, {
            value: metric.value,
            delta: metric.delta,
            id: metric.id
        })
    })
}

/**
 * API 응답 시간을 측정하는 헬퍼
 */
export function measureApiResponse(url: string, startTime: number) {
    const duration = performance.now() - startTime
    if (duration > 1000) { // 1초 이상 걸린 경우만 로깅
        console.warn(`[Performance] Slow API response: ${url} took ${duration.toFixed(2)}ms`)
    }
    return duration
}

/**
 * 페이지 로드 시간 측정
 */
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
