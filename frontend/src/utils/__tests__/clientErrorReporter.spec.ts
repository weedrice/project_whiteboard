import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { App } from 'vue'

const createClientErrorLog = vi.hoisted(() => vi.fn())

vi.mock('@/api/clientErrorLogApi', () => ({
    clientErrorLogApi: {
        create: createClientErrorLog,
    },
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
        warn: vi.fn(),
    },
}))

import { installClientErrorReporting } from '../clientErrorReporter'

function createAppStub(): App {
    return {
        config: {
            errorHandler: undefined,
        },
    } as unknown as App
}

describe('clientErrorReporter', () => {
    const cleanups: Array<() => void> = []

    beforeEach(() => {
        vi.clearAllMocks()
        createClientErrorLog.mockResolvedValue(undefined)
    })

    afterEach(() => {
        cleanups.splice(0).forEach((cleanup) => cleanup())
    })

    it('reports Vue errors with build and page metadata and invokes the UI callback', async () => {
        const app = createAppStub()
        const onVueError = vi.fn()
        cleanups.push(installClientErrorReporting(app, { onVueError }))
        const error = new TypeError('vue-render-failed')

        app.config.errorHandler?.(error, null, 'render function')

        await vi.waitFor(() => expect(createClientErrorLog).toHaveBeenCalledTimes(1))
        expect(createClientErrorLog).toHaveBeenCalledWith(expect.objectContaining({
            source: 'VUE',
            errorType: 'TypeError',
            message: 'vue-render-failed',
            info: 'render function',
            pageUrl: window.location.pathname,
            commitHash: expect.any(String),
        }))
        expect(onVueError).toHaveBeenCalledWith(error)
    })

    it('reports window errors and suppresses an identical report within the duplicate window', async () => {
        const app = createAppStub()
        cleanups.push(installClientErrorReporting(app))
        const error = new Error('window-event-failed')

        window.dispatchEvent(new ErrorEvent('error', {
            error,
            message: error.message,
            filename: 'app.js',
            lineno: 7,
            colno: 11,
        }))
        window.dispatchEvent(new ErrorEvent('error', {
            error,
            message: error.message,
            filename: 'app.js',
            lineno: 7,
            colno: 11,
        }))

        await vi.waitFor(() => expect(createClientErrorLog).toHaveBeenCalledTimes(1))
        expect(createClientErrorLog).toHaveBeenCalledWith(expect.objectContaining({
            source: 'WINDOW_ERROR',
            message: 'window-event-failed',
            info: 'app.js:7:11',
        }))
    })

    it('reports unhandled promise rejections', async () => {
        const app = createAppStub()
        cleanups.push(installClientErrorReporting(app))
        const event = new Event('unhandledrejection') as PromiseRejectionEvent
        Object.defineProperty(event, 'reason', {
            value: new Error('promise-rejection-failed'),
        })

        window.dispatchEvent(event)

        await vi.waitFor(() => expect(createClientErrorLog).toHaveBeenCalledTimes(1))
        expect(createClientErrorLog).toHaveBeenCalledWith(expect.objectContaining({
            source: 'UNHANDLED_REJECTION',
            message: 'promise-rejection-failed',
        }))
    })

    it('does not rethrow when the collection request fails', async () => {
        const app = createAppStub()
        createClientErrorLog.mockRejectedValueOnce(new Error('collector unavailable'))
        cleanups.push(installClientErrorReporting(app))

        expect(() => app.config.errorHandler?.(new Error('safe-report-failure'), null, 'render'))
            .not.toThrow()
        await vi.waitFor(() => expect(createClientErrorLog).toHaveBeenCalledTimes(1))
    })
})
