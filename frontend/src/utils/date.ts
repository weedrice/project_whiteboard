import type { SupportedLocale } from '@/locales/types'
import { SERVER_TIME_ZONE, getDisplayTimeZone } from '@/utils/displayTimeZone'

type DateFormatterKind = 'dateTime' | 'dateOnly' | 'longDateOnly' | 'timeOnly'

const formatterCache = new Map<string, Intl.DateTimeFormat>()

function localeCode(locale: SupportedLocale = 'ko') {
    return locale === 'en' ? 'en-US' : 'ko-KR'
}

/**
 * 표시 지역까지 캐시 키에 넣는다. 사용자가 설정에서 시간대를 바꾸면 같은 종류·언어라도
 * 다른 formatter가 필요하므로, 지역을 빼면 바꾼 뒤에도 예전 지역으로 계속 그려진다.
 */
function formatter(kind: DateFormatterKind, locale: SupportedLocale = 'ko') {
    const timeZone = getDisplayTimeZone()
    const key = `${kind}:${locale}:${timeZone}`
    const cached = formatterCache.get(key)
    if (cached) return cached

    const intlLocale = localeCode(locale)
    const nextFormatter = new Intl.DateTimeFormat(intlLocale, { ...formatterOptions[kind], timeZone })
    formatterCache.set(key, nextFormatter)
    return nextFormatter
}

const formatterOptions: Record<DateFormatterKind, Intl.DateTimeFormatOptions> = {
    dateTime: {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    },
    dateOnly: {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    },
    longDateOnly: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
    },
    timeOnly: {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    },
}

type DateInput = string | number[] | null | undefined

/** 서버 시각의 기준 지역. 백엔드 `DateTimeUtils.KST_ZONE_ID`와 같은 값이다. */
const SERVER_UTC_OFFSET = '+09:00'

/** offset이나 Z가 이미 붙어 있는지 본다. 날짜만 있는 값(`2026-07-25`)은 대상이 아니다. */
const HAS_EXPLICIT_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/i

/**
 * 서버가 준 시각 문자열을 Date로 바꾼다.
 *
 * 백엔드는 `LocalDateTime`을 KST offset이 붙은 형식으로 내보낸다. 다만 아직 offset 없이
 * 도착하는 값이 있을 수 있어(캐시된 응답, 예전 클라이언트가 저장한 값) 그런 경우에도
 * 서버 기준인 KST로 해석한다. 브라우저 로컬로 해석하면 KST 밖 사용자에게 어긋난다.
 *
 * 배열 형태는 Jackson이 timestamp 배열로 직렬화하던 시절의 형식이다. 현재 백엔드는
 * 항상 offset이 붙은 문자열을 내보내므로 도달하지 않지만, 캐시된 예전 응답을 위해 남긴다.
 */
function toDate(dateString: DateInput): Date | null {
    if (!dateString) return null

    if (Array.isArray(dateString)) {
        const [year, month, day, hour, minute, second] = dateString
        return new Date(Date.UTC(year, month - 1, day, hour || 0, minute || 0, second || 0))
    }

    const date = new Date(withServerOffset(dateString))
    return Number.isNaN(date.getTime()) ? null : date
}

/**
 * offset이 없는 date-time 문자열에 서버 기준 offset을 붙인다.
 * 해석 규칙을 직접 검증할 수 있도록 내보낸다.
 * ECMAScript 규격상 offset 없는 date-time은 브라우저 로컬로 해석되므로, 그대로 두면
 * 같은 값이 사용자 지역마다 다른 순간을 가리킨다. 날짜만 있는 값은 건드리지 않는다.
 */
export function withServerOffset(value: string): string {
    if (!value.includes('T')) return value
    if (HAS_EXPLICIT_OFFSET.test(value)) return value
    return `${value}${SERVER_UTC_OFFSET}`
}

/**
 * Formats a date string to a locale string.
 * Example: "2023-10-27T10:00:00" -> "2023. 10. 27. 10:00:00" (Korean locale)
 * Uses Intl.DateTimeFormat for better performance and consistency.
 */
export function formatDate(dateString: string | number[], locale: SupportedLocale = 'ko'): string {
    const date = toDate(dateString)
    if (!date) return ''

    return formatter('dateTime', locale).format(date)
}

export function formatDateTimeOrDash(dateString: DateInput, locale: SupportedLocale = 'ko'): string {
    const date = toDate(dateString)
    return date ? formatter('dateTime', locale).format(date) : '-'
}

export function formatDateOnlyLongOrDash(dateString: DateInput, locale: SupportedLocale = 'ko'): string {
    const date = toDate(dateString)
    return date ? formatter('longDateOnly', locale).format(date) : '-'
}

/**
 * Formats a date string with special handling for "today".
 * If the date is today, returns the time (e.g., "14:30").
 * Otherwise, returns the date (e.g., "2023. 10. 27.").
 */

/**
 * 표시 지역 기준의 달력 필드를 뽑는다.
 *
 * `Date`의 `getFullYear`/`getDate` 같은 접근자는 항상 기기 지역을 쓴다. 그 값으로 "오늘"을
 * 판정한 뒤 표시 지역으로 그리면 둘이 어긋나, 표시 지역에서는 어제인 글이 시각만 찍혀 나온다.
 * 판정과 표시를 같은 지역에서 하도록 여기서 한 번에 뽑는다.
 */
function displayZoneParts(date: Date) {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: getDisplayTimeZone(),
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).formatToParts(date)

    const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? ''
    const hour = get('hour') === '24' ? '00' : get('hour')

    return { year: get('year'), month: get('month'), day: get('day'), hour, minute: get('minute') }
}

export function formatRelativeDate(dateString: string, locale: SupportedLocale = 'ko'): string {
    const date = toDate(dateString)
    if (!date) return ''

    // "오늘"인지는 표시 지역 기준으로 판정해야 화면에 그려지는 날짜와 어긋나지 않는다.
    const target = displayZoneParts(date)
    const today = displayZoneParts(new Date())
    const isToday = target.year === today.year && target.month === today.month && target.day === today.day

    if (isToday) {
        return formatter('timeOnly', locale).format(date)
    }
    return formatter('dateOnly', locale).format(date)
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

    // 같은 화면의 다른 시각 표시와 지역이 어긋나지 않도록 표시 지역을 따른다.
    const { year, month, day, hour, minute } = displayZoneParts(date)
    return `${year.slice(-2)}.${month}.${day} ${hour}:${minute}`
}

export function formatDateOnly(dateString: string | number[], locale: SupportedLocale = 'ko'): string {
    const date = toDate(dateString)
    if (!date) return ''

    return formatter('dateOnly', locale).format(date)
}

export function formatTimeAgo(dateString: string | number[], t: (key: string, values?: Record<string, unknown>) => string): string {
    const date = toDate(dateString)
    if (!date) return ''

    const now = new Date()
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000)

    if (seconds < 60) return t('common.time.justNow')
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return t('common.time.minutesAgo', { count: minutes })
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return t('common.time.hoursAgo', { count: hours })
    const days = Math.floor(hours / 24)
    if (days < 7) return t('common.time.daysAgo', { count: days })

    return formatter('dateOnly', 'ko').format(date)
}

/**
 * 서버 시각을 `<input type="datetime-local">`이 받아들이는 형식으로 바꾼다.
 *
 * HTML 규격상 offset이 붙은 값은 유효한 local date-time이 아니어서 브라우저가 빈 문자열로
 * 지워 버린다. 서버가 offset을 붙여 보내기 시작한 뒤로 예약 발행 시각과 투표 마감 시각이
 * 수정 화면에서 빈칸으로 보이던 원인이다.
 *
 * 서버 기준(KST) 벽시계로 그린다. 이 입력이 만든 값은 그대로 서버로 가고 서버가 KST로
 * 읽으므로, 표시와 전송이 같은 기준이어야 왕복이 어긋나지 않는다. 사용자의 표시 지역을
 * 쓰지 않는 이유가 여기에 있다.
 */
export function toDateTimeLocalInputValue(value: string | null | undefined): string {
    const date = toDate(value)
    if (!date) return ''

    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: SERVER_TIME_ZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).formatToParts(date)

    const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? ''
    const hour = get('hour') === '24' ? '00' : get('hour')

    return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}`
}
