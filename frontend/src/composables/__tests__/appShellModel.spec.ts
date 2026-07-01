import { describe, expect, it } from 'vitest'
import { BOARD_DETAIL_SEARCH_INPUT_ID } from '@/composables/useBoardDetailNavigation'
import { getAppSearchInput, shouldNoIndexAppRoute } from '../appShellModel'

const route = (name: string | undefined, meta: Record<string, unknown> = {}) => ({
    name,
    meta,
})

describe('appShellModel', () => {
    it('marks auth, guest-only, role-protected and private utility routes as noindex', () => {
        expect(shouldNoIndexAppRoute(route('home'))).toBe(false)
        expect(shouldNoIndexAppRoute(route('login'))).toBe(true)
        expect(shouldNoIndexAppRoute(route('post-write'))).toBe(true)
        expect(shouldNoIndexAppRoute(route('public-name', { requiresAuth: true }))).toBe(true)
        expect(shouldNoIndexAppRoute(route('public-name', { guestOnly: true }))).toBe(true)
        expect(shouldNoIndexAppRoute(route('public-name', { roles: ['SUPER_ADMIN'] }))).toBe(true)
    })

    it('prefers board scoped search on board detail pages and falls back to global search', () => {
        const doc = document.implementation.createHTMLDocument()
        const globalInput = doc.createElement('input')
        globalInput.id = 'global-search-input'
        doc.body.appendChild(globalInput)

        expect(getAppSearchInput('home', doc)).toBe(globalInput)

        const boardInput = doc.createElement('input')
        boardInput.id = BOARD_DETAIL_SEARCH_INPUT_ID
        doc.body.appendChild(boardInput)

        expect(getAppSearchInput('board-detail', doc)).toBe(boardInput)
        expect(getAppSearchInput('post-detail', doc)).toBe(boardInput)
    })
})
