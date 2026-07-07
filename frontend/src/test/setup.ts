// Test setup file for jsdom environment
import { config, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, vi } from 'vitest'
import i18n from '@/i18n'

enableAutoUnmount(afterEach)

config.global.plugins = [
    ...(config.global.plugins ?? []),
    i18n,
]

config.global.mocks = {
    ...(config.global.mocks ?? {}),
    $t: i18n.global.t,
}

// Mock window.matchMedia for jsdom
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(), // deprecated
        removeListener: vi.fn(), // deprecated
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
})

Object.defineProperty(window, 'scrollTo', {
    writable: true,
    value: vi.fn(),
})

class MockResizeObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
}

class MockIntersectionObserver {
    readonly root = null
    readonly rootMargin = ''
    readonly thresholds = []

    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
    takeRecords = vi.fn(() => [])
}

Object.defineProperty(globalThis, 'ResizeObserver', {
    configurable: true,
    writable: true,
    value: MockResizeObserver,
})

Object.defineProperty(globalThis, 'IntersectionObserver', {
    configurable: true,
    writable: true,
    value: MockIntersectionObserver,
})

if (typeof URL.createObjectURL !== 'function') {
    Object.defineProperty(URL, 'createObjectURL', {
        configurable: true,
        writable: true,
        value: vi.fn((blob: Blob) => `blob:${blob instanceof File ? blob.name : 'preview'}`),
    })
}

if (typeof URL.revokeObjectURL !== 'function') {
    Object.defineProperty(URL, 'revokeObjectURL', {
        configurable: true,
        writable: true,
        value: vi.fn(),
    })
}
