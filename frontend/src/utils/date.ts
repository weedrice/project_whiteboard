// Reusable date formatters for better performance
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
})

const dateOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
})

const longDateOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
})

const timeOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
})

type DateInput = string | number[] | null | undefined

function toDate(dateString: DateInput): Date | null {
    if (!dateString) return null

    if (Array.isArray(dateString)) {
        const [year, month, day, hour, minute, second] = dateString
        return new Date(Date.UTC(year, month - 1, day, hour || 0, minute || 0, second || 0))
    }

    const date = new Date(dateString)
    return Number.isNaN(date.getTime()) ? null : date
}

/**
 * Formats a date string to a locale string.
 * Example: "2023-10-27T10:00:00" -> "2023. 10. 27. 10:00:00" (Korean locale)
 * Uses Intl.DateTimeFormat for better performance and consistency.
 */
export function formatDate(dateString: string | number[]): string {
    const date = toDate(dateString)
    if (!date) return ''

    return dateTimeFormatter.format(date)
}

export function formatDateTimeOrDash(dateString: DateInput): string {
    const date = toDate(dateString)
    return date ? dateTimeFormatter.format(date) : '-'
}

export function formatDateOnlyLongOrDash(dateString: DateInput): string {
    const date = toDate(dateString)
    return date ? longDateOnlyFormatter.format(date) : '-'
}

/**
 * Formats a date string with special handling for "today".
 * If the date is today, returns the time (e.g., "14:30").
 * Otherwise, returns the date (e.g., "2023. 10. 27.").
 */
export function formatRelativeDate(dateString: string): string {
    if (!dateString) return ''

    const date = new Date(dateString)
    const today = new Date()

    const isToday = date.getDate() === today.getDate() &&
        date.getMonth() === today.getMonth() &&
        date.getFullYear() === today.getFullYear()

    if (isToday) {
        return timeOnlyFormatter.format(date)
    }
    return dateOnlyFormatter.format(date)
}

/**
 * Formats a date string to show only the date part.
 * Example: "2023-10-27T10:00:00" -> "2023. 10. 27."
 * Uses Intl.DateTimeFormat for better performance and consistency.
 */
/**
 * 짧은 날짜+시간 (모바일용): YY.MM.DD HH:MI
 * Example: "2024-01-29T14:30:00" -> "24.01.29 14:30"
 */
export function formatDateShort(dateString: string | number[]): string {
    const date = toDate(dateString)
    if (!date) return ''

    const yy = String(date.getFullYear()).slice(-2)
    const mm = String(date.getMonth() + 1).padStart(2, '0')
    const dd = String(date.getDate()).padStart(2, '0')
    const hh = String(date.getHours()).padStart(2, '0')
    const mi = String(date.getMinutes()).padStart(2, '0')
    return `${yy}.${mm}.${dd} ${hh}:${mi}`
}

export function formatDateOnly(dateString: string | number[]): string {
    const date = toDate(dateString)
    if (!date) return ''

    return dateOnlyFormatter.format(date)
}

export function formatTimeAgo(dateString: string | number[], t: (key: string, values?: Record<string, unknown>) => string): string {
    if (!dateString) return ''
    const date = Array.isArray(dateString)
        ? new Date(Date.UTC(dateString[0], dateString[1] - 1, dateString[2], dateString[3], dateString[4], dateString[5] || 0))
        : new Date(dateString)

    const now = new Date()
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000)

    if (seconds < 60) return t('common.time.justNow')
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return t('common.time.minutesAgo', { count: minutes })
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return t('common.time.hoursAgo', { count: hours })
    const days = Math.floor(hours / 24)
    if (days < 7) return t('common.time.daysAgo', { count: days })

    return date.toLocaleDateString()
}
