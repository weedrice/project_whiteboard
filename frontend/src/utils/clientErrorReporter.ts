import type { App, ComponentPublicInstance } from 'vue'
import {
    clientErrorLogApi,
    type ClientErrorLogRequest,
    type ClientErrorSource,
} from '@/api/clientErrorLogApi'
import logger from '@/utils/logger'
import { createErrorLogPayload, createVueErrorLogPayload } from '@/utils/vueErrorLog'

const DUPLICATE_WINDOW_MS = 10_000
const MAX_RECENT_REPORTS = 50
const MAX_ERROR_TYPE_LENGTH = 100
const MAX_MESSAGE_LENGTH = 500
const MAX_STACK_TRACE_LENGTH = 2_500
const MAX_COMPONENT_LENGTH = 100
const MAX_INFO_LENGTH = 500
const MAX_PAGE_URL_LENGTH = 500
const MAX_COMMIT_HASH_LENGTH = 64
const recentReports = new Map<string, number>()

interface ErrorLogPayload {
    error: unknown
    info?: string
    component?: string | null
}

interface SerializedErrorDetails {
    name?: unknown
    message?: unknown
    stack?: unknown
}

interface ClientErrorReportingOptions {
    onVueError?: (error: unknown) => void
}

export type DraftOperationalEvent =
    | 'local_storage_write_failed'
    | 'local_storage_rollback_failed'
    | 'autosave_retry_exhausted'
    | 'draft_conflict'
    | 'draft_protected'
    | 'multiple_recovery_candidates'
    | 'scheduled_in_another_tab'
    | 'deleted_in_another_tab'
    | 'local_storage_remove_failed'
    | 'tombstone_write_failed'

function truncate(value: string, maxLength: number): string {
    return value.length > maxLength ? value.slice(0, maxLength) : value
}

function safeStringify(value: unknown): string {
    if (typeof value === 'string') return value

    try {
        const serialized = JSON.stringify(value)
        return serialized ?? String(value)
    } catch {
        return String(value)
    }
}

function createRequest(payload: ErrorLogPayload, source: ClientErrorSource): ClientErrorLogRequest {
    const details = payload.error && typeof payload.error === 'object'
        ? payload.error as SerializedErrorDetails
        : null
    const rawMessage = typeof details?.message === 'string'
        ? details.message
        : safeStringify(payload.error)
    const rawErrorType = typeof details?.name === 'string' ? details.name : 'Error'

    return {
        source,
        errorType: truncate(rawErrorType || 'Error', MAX_ERROR_TYPE_LENGTH),
        message: truncate(rawMessage || 'Unknown client error', MAX_MESSAGE_LENGTH),
        ...(typeof details?.stack === 'string'
            ? { stackTrace: truncate(details.stack, MAX_STACK_TRACE_LENGTH) }
            : {}),
        ...(payload.component
            ? { component: truncate(payload.component, MAX_COMPONENT_LENGTH) }
            : {}),
        ...(payload.info ? { info: truncate(payload.info, MAX_INFO_LENGTH) } : {}),
        pageUrl: truncate(window.location.pathname, MAX_PAGE_URL_LENGTH),
        commitHash: truncate(__COMMIT_HASH__, MAX_COMMIT_HASH_LENGTH),
    }
}

function createFingerprint(request: ClientErrorLogRequest): string {
    return [
        request.errorType,
        request.message,
        request.stackTrace ?? '',
        request.pageUrl,
        request.commitHash,
    ].join('\n')
}

function isDuplicate(request: ClientErrorLogRequest): boolean {
    const now = Date.now()

    for (const [fingerprint, reportedAt] of recentReports) {
        if (now - reportedAt >= DUPLICATE_WINDOW_MS) {
            recentReports.delete(fingerprint)
        }
    }

    const fingerprint = createFingerprint(request)
    const reportedAt = recentReports.get(fingerprint)
    if (reportedAt !== undefined && now - reportedAt < DUPLICATE_WINDOW_MS) {
        return true
    }

    if (recentReports.size >= MAX_RECENT_REPORTS) {
        const oldestFingerprint = recentReports.keys().next().value
        if (oldestFingerprint) recentReports.delete(oldestFingerprint)
    }
    recentReports.set(fingerprint, now)
    return false
}

async function send(payload: ErrorLogPayload, source: ClientErrorSource): Promise<boolean> {
    const request = createRequest(payload, source)
    if (isDuplicate(request)) return true

    logger.error('Client error captured:', request)
    try {
        await clientErrorLogApi.create(request)
        return true
    } catch (reportingError: unknown) {
        logger.warn('Client error report failed.', reportingError)
        return false
    }
}

export function reportVueError(
    error: unknown,
    instance: ComponentPublicInstance | null,
    info: string,
) {
    return send(createVueErrorLogPayload(error, instance, info), 'VUE')
}

export function reportDraftOperationalEvent(
    event: DraftOperationalEvent,
    metadata: Readonly<Record<string, number | boolean | string>> = {},
) {
    return send({
        error: { name: 'DraftAutosaveEvent', message: event },
        component: 'PostDraft',
        info: JSON.stringify(metadata),
    }, 'DRAFT_AUTOSAVE')
}

export function installClientErrorReporting(app: App, options: ClientErrorReportingOptions = {}) {
    const previousErrorHandler = app.config.errorHandler
    const vueErrorHandler = (
        error: unknown,
        instance: ComponentPublicInstance | null,
        info: string,
    ) => {
        void reportVueError(error, instance, info)
        options.onVueError?.(error)
    }
    const windowErrorHandler = (event: ErrorEvent) => {
        const error = event.error ?? new Error(event.message || 'Unknown window error')
        const location = event.filename
            ? `${event.filename}:${event.lineno || 0}:${event.colno || 0}`
            : undefined
        void send({ ...createErrorLogPayload(error), info: location }, 'WINDOW_ERROR')
    }
    const rejectionHandler = (event: PromiseRejectionEvent) => {
        const reason = event.reason ?? new Error('Unhandled promise rejection')
        void send(createErrorLogPayload(reason), 'UNHANDLED_REJECTION')
    }

    app.config.errorHandler = vueErrorHandler
    window.addEventListener('error', windowErrorHandler)
    window.addEventListener('unhandledrejection', rejectionHandler)

    return () => {
        window.removeEventListener('error', windowErrorHandler)
        window.removeEventListener('unhandledrejection', rejectionHandler)
        if (app.config.errorHandler === vueErrorHandler) {
            app.config.errorHandler = previousErrorHandler
        }
    }
}
