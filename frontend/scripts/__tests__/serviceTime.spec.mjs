import { describe, expect, it } from 'vitest'
import { parseServiceInstant, SERVICE_TIME_ZONE, withServiceOffset } from '../serviceTime.mjs'
import { withServerOffset } from '../../src/utils/date'
import { SERVER_TIME_ZONE } from '../../src/utils/displayTimeZone'

describe('withServiceOffset', () => {
    it('offset이 없는 date-time에만 서비스 offset을 붙인다', () => {
        expect(withServiceOffset('2026-07-25T10:00:00')).toBe('2026-07-25T10:00:00+09:00')
        expect(withServiceOffset('2026-07-25T10:00:00+09:00')).toBe('2026-07-25T10:00:00+09:00')
        expect(withServiceOffset('2026-07-25T10:00:00Z')).toBe('2026-07-25T10:00:00Z')
        expect(withServiceOffset('2026-07-25T10:00:00-05:00')).toBe('2026-07-25T10:00:00-05:00')
    })

    it('날짜만 있는 값은 건드리지 않는다', () => {
        expect(withServiceOffset('2026-07-25')).toBe('2026-07-25')
    })
})

describe('parseServiceInstant', () => {
    it('offset이 없어도 실행 환경 지역이 아니라 서비스 기준으로 읽는다', () => {
        // vitest는 TZ=UTC로 돌지만 결과는 KST 기준이어야 한다.
        // 그대로 읽었다면 10:00Z가 되어 9시간 어긋난다.
        expect(parseServiceInstant('2026-07-25T10:00:00')?.toISOString())
            .toBe('2026-07-25T01:00:00.000Z')
    })

    it('offset이 붙은 값은 그 값을 그대로 존중한다', () => {
        expect(parseServiceInstant('2026-07-25T10:00:00Z')?.toISOString())
            .toBe('2026-07-25T10:00:00.000Z')
    })

    it('해석할 수 없거나 비어 있으면 null', () => {
        expect(parseServiceInstant('not-a-date')).toBeNull()
        expect(parseServiceInstant('')).toBeNull()
        expect(parseServiceInstant(null)).toBeNull()
        expect(parseServiceInstant(undefined)).toBeNull()
    })
})

// 스크립트는 .mjs라 앱 코드를 그대로 쓸 수 없어 규칙이 두 벌 존재한다.
// 한쪽만 고치면 사이트맵·프리렌더의 시각이 앱과 어긋나므로 여기서 묶어 둔다.
describe('앱 코드와 같은 규칙을 쓴다', () => {
    it('기준 지역이 같다', () => {
        expect(SERVICE_TIME_ZONE).toBe(SERVER_TIME_ZONE)
    })

    it('offset 부여 결과가 같다', () => {
        const samples = [
            '2026-07-25T10:00:00',
            '2026-07-25T10:00:00+09:00',
            '2026-07-25T10:00:00Z',
            '2026-07-25T10:00:00-05:00',
            '2026-07-25',
        ]

        for (const sample of samples) {
            expect(withServiceOffset(sample)).toBe(withServerOffset(sample))
        }
    })
})
