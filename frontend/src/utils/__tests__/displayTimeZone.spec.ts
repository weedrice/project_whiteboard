import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    AUTO_TIME_ZONE,
    SERVER_TIME_ZONE,
    configureUserTimeZoneResolver,
    getDisplayTimeZone,
    isSupportedTimeZone,
    rememberUserTimeZone,
    resetUserTimeZoneResolverForTest,
} from '@/utils/displayTimeZone'
import { Storage } from '@/utils/storage'

describe('getDisplayTimeZone', () => {
    afterEach(() => {
        resetUserTimeZoneResolverForTest()
        vi.restoreAllMocks()
    })

    it('사용자가 고른 지역을 우선한다', () => {
        configureUserTimeZoneResolver(() => 'America/New_York')

        expect(getDisplayTimeZone()).toBe('America/New_York')
    })

    it('AUTO면 브라우저 판단을 따른다', () => {
        configureUserTimeZoneResolver(() => AUTO_TIME_ZONE)

        expect(getDisplayTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
    })

    it('설정이 비어 있어도 브라우저 판단을 따른다', () => {
        configureUserTimeZoneResolver(() => null)

        expect(getDisplayTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
    })

    it('Intl이 모르는 값은 무시하고 자동 판단으로 떨어진다', () => {
        // 잘못된 값 하나로 모든 시각 표시가 깨지면 안 된다.
        configureUserTimeZoneResolver(() => 'Mars/Olympus_Mons')

        expect(getDisplayTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
    })

    it('브라우저가 지역을 알려 주지 못하면 서버 기준으로 떨어진다', () => {
        configureUserTimeZoneResolver(() => AUTO_TIME_ZONE)
        vi.spyOn(Intl, 'DateTimeFormat').mockImplementation((() => {
            throw new Error('unavailable')
        }) as unknown as typeof Intl.DateTimeFormat)

        expect(getDisplayTimeZone()).toBe(SERVER_TIME_ZONE)
    })
})

describe('isSupportedTimeZone', () => {
    it('IANA 지역명을 받아들인다', () => {
        expect(isSupportedTimeZone('Asia/Seoul')).toBe(true)
        expect(isSupportedTimeZone('UTC')).toBe(true)
    })

    it('알 수 없는 값을 거부한다', () => {
        expect(isSupportedTimeZone('Mars/Olympus_Mons')).toBe(false)
        expect(isSupportedTimeZone('')).toBe(false)
    })
})

describe('rememberUserTimeZone', () => {
    afterEach(() => {
        Storage.remove('displayTimeZone')
        resetUserTimeZoneResolverForTest()
    })

    it('고른 지역을 저장하고 이후 표시에 반영한다', () => {
        rememberUserTimeZone('America/New_York')

        expect(getDisplayTimeZone()).toBe('America/New_York')
    })

    it('AUTO는 저장하지 않고 자동 판단으로 되돌린다', () => {
        rememberUserTimeZone('America/New_York')
        rememberUserTimeZone(AUTO_TIME_ZONE)

        expect(getDisplayTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
    })

    it('Intl이 모르는 값은 저장하지 않는다', () => {
        rememberUserTimeZone('Mars/Olympus_Mons')

        expect(Storage.getString('displayTimeZone')).toBeNull()
    })
})
