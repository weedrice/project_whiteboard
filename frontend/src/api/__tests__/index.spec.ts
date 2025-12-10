import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import api from '../index'
import axios from 'axios'

vi.mock('axios', async (importOriginal) => {
    const actual = await importOriginal<typeof import('axios')>()
    return {
        ...actual,
        create: vi.fn(() => ({
            interceptors: {
                request: { use: vi.fn() },
                response: { use: vi.fn() }
            },
            defaults: { baseURL: '/api/v1' }
        })),
        post: vi.fn()
    }
})

vi.mock('@/i18n', () => ({
    default: {
        global: {
            t: (key: string) => key
        }
    }
}))

vi.mock('@/router', () => ({
    default: {
        push: vi.fn()
    }
}))

describe('API Interceptors', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        localStorage.clear()
    })

    it('should be defined', () => {
        expect(api).toBeDefined()
    })

    // Note: Testing interceptors fully requires more complex mocking of the axios instance created by create()
    // For now, we just verify the instance is created.
})
