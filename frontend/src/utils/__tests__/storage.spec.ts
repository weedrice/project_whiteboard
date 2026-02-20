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

import { Storage as StorageUtil } from '@/utils/storage'

describe('Storage', () => {
    beforeEach(() => {
        localStorage.clear()
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
})
