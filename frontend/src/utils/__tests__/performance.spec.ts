import { afterEach, describe, expect, it, vi } from 'vitest'
import { logMetric, serializeMetric } from '../performance'

const metric = {
    name: 'LCP',
    value: 1234,
    delta: 100,
    id: 'metric-1',
    entries: [] as PerformanceEntry[],
}

describe('performance metrics', () => {
    afterEach(() => {
        vi.unstubAllEnvs()
        vi.unstubAllGlobals()
    })

    it('serializes web vitals without performance entries', () => {
        expect(serializeMetric(metric)).toEqual({
            name: 'LCP',
            value: 1234,
            delta: 100,
            id: 'metric-1',
        })
    })

    it('sends metrics to the configured endpoint with sendBeacon', () => {
        const sendBeacon = vi.fn<(url: string, data?: BodyInit | null) => boolean>(() => true)
        vi.stubEnv('VITE_WEB_VITALS_ENDPOINT', '/metrics/web-vitals')
        vi.stubGlobal('navigator', { sendBeacon })

        logMetric(metric)

        expect(sendBeacon).toHaveBeenCalledTimes(1)
        expect(sendBeacon.mock.calls[0][0]).toBe('/metrics/web-vitals')
        expect(sendBeacon.mock.calls[0][1]).toBeInstanceOf(Blob)
    })
})
