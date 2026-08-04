import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
        warn: vi.fn(),
        info: vi.fn(),
        debug: vi.fn(),
        trace: vi.fn(),
    },
}))

import { SessionStorage, Storage as StorageUtil } from '@/utils/storage'
import logger from '@/utils/logger'

describe('Storage', () => {
    beforeEach(() => {
        localStorage.clear()
        sessionStorage.clear()
        vi.restoreAllMocks()
    })

    it('stores and retrieves JSON values', () => {
        StorageUtil.set('user', { id: 1, name: 'alice' })
        expect(StorageUtil.get<{ id: number; name: string }>('user')).toEqual({ id: 1, name: 'alice' })
    })

    it('returns default values when keys are missing', () => {
        expect(StorageUtil.get('missing', 'fallback')).toBe('fallback')
        expect(StorageUtil.getString('missing-token', '')).toBe('')
    })

    it('supports string helpers and key lifecycle APIs', () => {
        StorageUtil.setString('accessToken', 'token-1')
        expect(StorageUtil.getString('accessToken')).toBe('token-1')
        expect(StorageUtil.has('accessToken')).toBe(true)
        expect(StorageUtil.keys()).toContain('accessToken')

        StorageUtil.remove('accessToken')
        expect(StorageUtil.has('accessToken')).toBe(false)

        StorageUtil.set('theme', 'dark')
        StorageUtil.clear()
        expect(StorageUtil.keys()).toEqual([])
    })

    it('fails safely when localStorage read throws', () => {
        const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
        const spy = vi.spyOn(storageProto, 'getItem').mockImplementation(() => {
            throw new Error('read failed')
        })

        expect(StorageUtil.get('broken', 'fallback')).toBe('fallback')
        expect(StorageUtil.getString('broken-token', 'fallback-token')).toBe('fallback-token')

        spy.mockRestore()
    })

    it('fails safely when localStorage write throws', () => {
        const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
        const spy = vi.spyOn(storageProto, 'setItem').mockImplementation(() => {
            throw new Error('write failed')
        })

        expect(() => StorageUtil.set('x', 1)).not.toThrow()
        expect(() => StorageUtil.setString('x', '1')).not.toThrow()

        spy.mockRestore()
    })

    it('logs quota warning when localStorage quota is exceeded', () => {
        const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
        const quotaError = new DOMException('quota', 'QuotaExceededError')
        const spy = vi.spyOn(storageProto, 'setItem').mockImplementation(() => {
            throw quotaError
        })

        expect(StorageUtil.setWithResult('profile', { id: 1 })).toEqual({
            ok: false,
            reason: 'quota-exceeded',
        })
        StorageUtil.setString('token', 'abc')

        expect(logger.warn).toHaveBeenCalledTimes(2)
        spy.mockRestore()
    })

    it('distinguishes unavailable storage writes from quota failures', () => {
        const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
        const spy = vi.spyOn(storageProto, 'setItem').mockImplementation(() => {
            throw new Error('storage disabled')
        })

        expect(StorageUtil.setWithResult('profile', { id: 1 })).toEqual({
            ok: false,
            reason: 'unavailable',
        })
        spy.mockRestore()
    })

    it('fails safely when remove, clear, has and keys operations throw', () => {
        const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
        const removeSpy = vi.spyOn(storageProto, 'removeItem').mockImplementation(() => {
            throw new Error('remove failed')
        })
        const clearSpy = vi.spyOn(storageProto, 'clear').mockImplementation(() => {
            throw new Error('clear failed')
        })
        const getSpy = vi.spyOn(storageProto, 'getItem').mockImplementation(() => {
            throw new Error('has failed')
        })
        const objectKeysSpy = vi.spyOn(Object, 'keys').mockImplementation((obj: object) => {
            if (obj === localStorage) {
                throw new Error('keys failed')
            }
            return []
        })

        expect(StorageUtil.remove('x')).toBe(false)
        expect(() => StorageUtil.clear()).not.toThrow()
        expect(StorageUtil.has('x')).toBe(false)
        expect(StorageUtil.keys()).toEqual([])

        removeSpy.mockRestore()
        clearSpy.mockRestore()
        getSpy.mockRestore()
        objectKeysSpy.mockRestore()
    })

    it('supports session string lifecycle helpers', () => {
        SessionStorage.setString('chunk-reload-attempted', '1')
        expect(SessionStorage.getString('chunk-reload-attempted')).toBe('1')

        SessionStorage.remove('chunk-reload-attempted')

        expect(SessionStorage.getString('chunk-reload-attempted')).toBeNull()
    })

    it('fails safely when sessionStorage operations throw', () => {
        const storageProto = Object.getPrototypeOf(window.sessionStorage) as globalThis.Storage
        const getSpy = vi.spyOn(storageProto, 'getItem').mockImplementation(() => {
            throw new Error('session read failed')
        })
        const setSpy = vi.spyOn(storageProto, 'setItem').mockImplementation(() => {
            throw new Error('session write failed')
        })
        const removeSpy = vi.spyOn(storageProto, 'removeItem').mockImplementation(() => {
            throw new Error('session remove failed')
        })

        expect(SessionStorage.getString('x', 'fallback')).toBe('fallback')
        expect(() => SessionStorage.setString('x', '1')).not.toThrow()
        expect(() => SessionStorage.remove('x')).not.toThrow()

        getSpy.mockRestore()
        setSpy.mockRestore()
        removeSpy.mockRestore()
    })
})
