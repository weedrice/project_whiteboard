import { Storage } from '@/utils/storage'

/**
 * 시각을 어느 지역 기준으로 보여줄지 결정한다.
 *
 * 표시는 사용자 기준으로 하되, 판정은 서버 기준(KST)을 유지한다. 출석의 "오늘",
 * 일일 한도, 예약 발행 시각 같은 값은 기기 timezone을 바꿔 우회할 수 있으면 안 되므로
 * 이 모듈을 쓰지 않는다. 이 모듈은 이미 확정된 순간을 화면에 그리는 용도다.
 *
 * 우선순위:
 * 1. 사용자가 설정에서 명시적으로 고른 지역
 * 2. 브라우저가 알려 주는 지역 (설정이 `AUTO`이거나 없을 때)
 * 3. 서버 기준 지역 (브라우저가 알려 주지 못할 때)
 */

/** 설정에서 "자동"을 뜻하는 값. 저장된 지역 대신 브라우저 판단을 따른다. */
export const AUTO_TIME_ZONE = 'AUTO'

/** 서버 저장 기준. 브라우저가 지역을 알려 주지 못할 때의 최후 기본값이다. */
export const SERVER_TIME_ZONE = 'Asia/Seoul'

/** 저장 키. `theme`과 같은 방식으로 기기에 남겨, 설정을 다시 불러오기 전에도 즉시 반영된다. */
const STORAGE_KEY = 'displayTimeZone'

let resolveUserTimeZone: () => string | null | undefined = () => Storage.getString(STORAGE_KEY)

/**
 * 사용자 설정을 읽어 올 방법을 등록한다.
 * 스토어를 직접 import하면 이 모듈이 스토어 초기화에 묶이므로 주입으로 끊는다.
 */
export function configureUserTimeZoneResolver(resolver: () => string | null | undefined): void {
    resolveUserTimeZone = resolver
    invalidateDisplayTimeZone()
}

/** 테스트에서 등록 상태를 되돌린다. */
export function resetUserTimeZoneResolverForTest(): void {
    resolveUserTimeZone = () => Storage.getString(STORAGE_KEY)
    invalidateDisplayTimeZone()
}

/**
 * 사용자가 고른 지역을 기기에 남긴다.
 * 서버 설정이 정본이지만, 저장 직후와 다음 방문의 첫 렌더에 바로 반영되도록 함께 둔다.
 */
export function rememberUserTimeZone(timeZone: string | null | undefined): void {
    invalidateDisplayTimeZone()

    if (!timeZone || timeZone === AUTO_TIME_ZONE) {
        Storage.remove(STORAGE_KEY)
        return
    }
    if (!isSupportedTimeZone(timeZone)) return
    Storage.setString(STORAGE_KEY, timeZone)
}

/**
 * 세션 경계에서 호출한다. 저장된 지역은 기기가 아니라 계정에 딸린 설정이므로,
 * 남겨 두면 공용 기기에서 다음 사용자가 앞 사용자의 지역으로 시각을 보게 된다.
 */
export function clearUserTimeZone(): void {
    Storage.remove(STORAGE_KEY)
    invalidateDisplayTimeZone()
}

/** 브라우저가 판단한 지역. 알 수 없으면 null. */
export function detectBrowserTimeZone(): string | null {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || null
    } catch {
        return null
    }
}

/**
 * 화면에 쓸 지역을 고른다.
 * `Intl`이 모르는 값이 저장돼 있으면 무시한다. 잘못된 값 하나로 모든 시각 표시가
 * 깨지는 것보다 자동 판단으로 떨어지는 편이 낫다.
 */
let resolvedTimeZone: string | null = null

export function getDisplayTimeZone(): string {
    // 매 호출마다 Intl.DateTimeFormat을 만들면 date.ts의 formatter 캐시가 무의미해진다.
    // 목록 한 화면이 수십 번 포맷하므로 결과를 기억하고, 설정이 바뀔 때만 비운다.
    if (resolvedTimeZone) return resolvedTimeZone

    const configured = resolveUserTimeZone()?.trim()
    resolvedTimeZone = configured && configured !== AUTO_TIME_ZONE && isSupportedTimeZone(configured)
        ? configured
        : detectBrowserTimeZone() ?? SERVER_TIME_ZONE

    return resolvedTimeZone
}

/** 설정이 바뀌었을 때 기억해 둔 지역을 비운다. */
export function invalidateDisplayTimeZone(): void {
    resolvedTimeZone = null
}

export function isSupportedTimeZone(timeZone: string): boolean {
    try {
        new Intl.DateTimeFormat('en', { timeZone })
        return true
    } catch {
        return false
    }
}
