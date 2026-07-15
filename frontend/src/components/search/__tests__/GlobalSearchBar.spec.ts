import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import GlobalSearchBar from '../GlobalSearchBar.vue'
import type { BoardListItem } from '@/types'

const boards: BoardListItem[] = [
    {
        boardId: 1,
        boardName: 'Vue',
        boardUrl: 'vue',
        description: 'Vue board',
        sortOrder: 1,
        subscriberCount: 3,
        postCount: 10,
        isSubscribed: false,
        isActive: true,
        isPublic: true,
        subscriptionAccessible: true,
    },
]

const routerPush = vi.fn()
const invalidateQueries = vi.fn()
const mobileViewport = ref(false)

vi.mock('vue-router', () => ({
    useRoute: () => ({
        name: 'home',
        query: {},
    }),
    useRouter: () => ({
        push: routerPush,
    }),
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        invalidateQueries,
    }),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string, params?: Record<string, string>) => (
            key === 'search.doSearch' ? `검색: ${params?.query}` : key
        ),
    }),
}))

vi.mock('@/features/board/useBoard', () => ({
    useBoard: () => ({
        useBoards: () => ({
            data: ref(boards),
        }),
    }),
}))

vi.mock('@/composables/useDebounce', () => ({
    useDebounce: <T>(value: T) => value,
}))

vi.mock('@/composables/useMediaQuery', () => ({
    useMobileViewport: () => mobileViewport,
}))

const mountSearchBar = () => mount(GlobalSearchBar, {
    attachTo: document.body,
    global: {
        mocks: {
            $t: (key: string, params?: Record<string, string>) => (
                key === 'search.doSearch' ? `검색: ${params?.query}` : key
            ),
        },
        stubs: {
            Search: true,
            X: true,
        },
    },
})

describe('GlobalSearchBar', () => {
    beforeEach(() => {
        routerPush.mockClear()
        invalidateQueries.mockClear()
        mobileViewport.value = false
    })

    afterEach(() => {
        document.body.innerHTML = ''
    })

    it('reserves a 44px collapsed search target on mobile', () => {
        mobileViewport.value = true
        const wrapper = mountSearchBar()

        expect(wrapper.get('.nv-global-search-toggle').classes()).toEqual(
            expect.arrayContaining(['h-11', 'w-11', 'flex-shrink-0']),
        )
        expect(Array.from(wrapper.get('.nv-global-search-toggle').element.parentElement?.classList ?? [])).toEqual(
            expect.arrayContaining(['w-11', 'sm:w-10']),
        )
    })

    it('connects search input combobox attributes to board results', async () => {
        const wrapper = mountSearchBar()
        const input = wrapper.get('input')

        expect(input.attributes()).toMatchObject({
            id: 'global-search-input',
            role: 'combobox',
            'aria-expanded': 'false',
            'aria-controls': 'global-search-board-results',
            autocomplete: 'off',
        })
        expect(wrapper.get('label').classes()).toContain('sr-only')
        expect(wrapper.get('label').text()).toBe('search.placeholder')

        await input.setValue('Vue')
        await input.trigger('focus')

        const listbox = wrapper.get('#global-search-board-results')
        const option = wrapper.get('#global-search-board-results-vue')

        expect(input.attributes('aria-expanded')).toBe('true')
        expect(listbox.attributes('role')).toBe('listbox')
        expect(listbox.attributes('aria-label')).toBe('search.boardResultsLabel')
        expect(option.attributes('role')).toBe('option')
        expect(option.attributes('aria-selected')).toBe('false')

        await option.trigger('mouseenter')

        expect(wrapper.get('input').attributes('aria-activedescendant')).toBe('global-search-board-results-vue')
        expect(wrapper.get('#global-search-board-results-vue').attributes('aria-selected')).toBe('true')
    })

    it('renders mobile results through teleport after expanding search', async () => {
        mobileViewport.value = true
        const wrapper = mountSearchBar()

        await wrapper.get('button[aria-label="search.placeholder"]').trigger('click')
        await nextTick()

        const input = wrapper.get('input')
        await input.setValue('Vue')
        await input.trigger('focus')

        const dropdown = document.body.querySelector('.nv-global-search-dropdown')
        const listbox = document.body.querySelector('#global-search-board-results')

        expect(dropdown).not.toBeNull()
        expect(dropdown?.classList.contains('fixed')).toBe(true)
        expect(dropdown?.classList.contains('nv-global-search-mobile-results')).toBe(true)
        expect(listbox?.getAttribute('role')).toBe('listbox')
        expect(document.body.querySelector('#global-search-board-results-vue')?.getAttribute('role')).toBe('option')
    })

    it('restores focus to the mobile search trigger when Escape collapses search', async () => {
        mobileViewport.value = true
        const wrapper = mountSearchBar()

        await wrapper.get('button[aria-label="search.placeholder"]').trigger('click')
        await nextTick()

        await wrapper.get('input').trigger('keydown', { key: 'Escape' })
        await nextTick()

        const searchTrigger = wrapper.get('button[aria-label="search.placeholder"]')
        expect(document.activeElement).toBe(searchTrigger.element)
    })

    it('collapses expanded mobile search on outside click while input remains focused', async () => {
        mobileViewport.value = true
        const wrapper = mountSearchBar()

        await wrapper.get('button[aria-label="search.placeholder"]').trigger('click')
        await nextTick()

        const input = wrapper.get('input')
        await input.setValue('Vue')
        await input.trigger('focus')
        ;(input.element as HTMLInputElement).focus()

        expect(document.body.querySelector('.nv-global-search-dropdown')).not.toBeNull()

        document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }))
        await nextTick()

        expect(document.body.querySelector('.nv-global-search-dropdown')).toBeNull()
        expect(wrapper.find('input').exists()).toBe(false)
        expect(wrapper.find('button[aria-label="search.placeholder"]').exists()).toBe(true)
    })

    it('submits search with Enter when no board option is selected', async () => {
        const wrapper = mountSearchBar()
        const input = wrapper.get('input')

        await input.setValue('Vue')
        await input.trigger('focus')
        await input.trigger('keydown', { key: 'Enter' })

        expect(routerPush).toHaveBeenCalledWith({
            name: 'search',
            query: { q: 'Vue' },
        })
    })
})
