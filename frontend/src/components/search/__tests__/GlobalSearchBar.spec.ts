import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
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

vi.mock('vue-router', () => ({
    useRouter: () => ({
        push: routerPush,
    }),
}))

vi.mock('@/composables/useBoard', () => ({
    useBoard: () => ({
        useBoards: () => ({
            data: ref(boards),
        }),
    }),
}))

vi.mock('@/composables/useDebounce', () => ({
    useDebounce: <T>(value: T) => value,
}))

const mountSearchBar = () => mount(GlobalSearchBar, {
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
        expect(option.attributes('role')).toBe('option')
        expect(option.attributes('aria-selected')).toBe('false')

        await option.trigger('mouseenter')

        expect(wrapper.get('input').attributes('aria-activedescendant')).toBe('global-search-board-results-vue')
        expect(wrapper.get('#global-search-board-results-vue').attributes('aria-selected')).toBe('true')
    })
})
