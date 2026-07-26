/**
 * 빌드 스크립트에서 서버 시각 문자열을 다루는 규칙.
 *
 * `src/utils/date.ts`의 `withServerOffset`과 같은 규칙이다. 스크립트는 `.mjs`라 TS 모듈을
 * 그대로 쓸 수 없어 여기에 둔다. 한쪽을 고치면 다른 쪽도 함께 봐야 하며,
 * `__tests__/serviceTime.spec.mjs`가 두 곳의 동작이 어긋나지 않는지 확인한다.
 */

/** 서비스 기준 지역. 백엔드 `DateTimeUtils.KST_ZONE_ID`와 같은 값이다. */
export const SERVICE_TIME_ZONE = 'Asia/Seoul'

const SERVICE_UTC_OFFSET = '+09:00'

/** offset이나 Z가 이미 붙어 있는지 본다. 날짜만 있는 값(`2026-07-25`)은 대상이 아니다. */
const HAS_EXPLICIT_OFFSET = /(?:Z|[+-]\d{2}:?\d{2})$/i

/**
 * offset이 없는 date-time 문자열에 서비스 기준 offset을 붙인다.
 *
 * 백엔드는 현재 offset을 붙여 보내지만, 캐시된 예전 응답이나 스냅샷 파일에는 offset 없는
 * 값이 남아 있을 수 있다. 그대로 `new Date`에 넘기면 ECMAScript 규격상 **실행 환경 지역**
 * (CI 컨테이너는 대개 UTC)으로 해석되어, KST 새벽에 작성된 글이 하루 이르게 기록된다.
 */
export function withServiceOffset(value) {
    if (typeof value !== 'string') return value
    if (!value.includes('T')) return value
    if (HAS_EXPLICIT_OFFSET.test(value)) return value
    return `${value}${SERVICE_UTC_OFFSET}`
}

/**
 * 서버 시각 문자열을 `Date`로 바꾼다. 해석할 수 없으면 null.
 * 호출부가 `Number.isNaN(date.valueOf())`을 각자 확인하지 않도록 여기서 걸러 준다.
 */
export function parseServiceInstant(value) {
    if (!value) return null
    const date = new Date(withServiceOffset(value))
    return Number.isNaN(date.valueOf()) ? null : date
}
