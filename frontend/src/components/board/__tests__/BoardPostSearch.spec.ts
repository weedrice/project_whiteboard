import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import BoardPostSearch from '../BoardPostSearch.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
    }),
  }
})

function mountSearch(props = {}) {
  return mount(BoardPostSearch, {
    props: {
      searchQuery: '',
      searchType: 'TITLE_CONTENT',
      searchInputElementId: 'board-search-input',
      isSearching: false,
      canWrite: true,
      boardUrl: 'free',
      ...props,
    },
    global: {
      stubs: {
        RouterLink: RouterLinkStub,
        Search: true,
        X: true,
      },
    },
  })
}

describe('BoardPostSearch', () => {
  it('renders search type options and write link', () => {
    const wrapper = mountSearch()
    const options = wrapper.findAll('option')

    expect(options.map((option) => option.attributes('value'))).toEqual([
      'TITLE_CONTENT',
      'TITLE',
      'CONTENT',
      'AUTHOR',
    ])
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/board/free/write')
  })

  it('emits search, clear, and v-model updates', async () => {
    const wrapper = mountSearch({ searchQuery: 'vue', searchType: 'TITLE' })

    await wrapper.get('select').setValue('AUTHOR')
    await wrapper.get('input').setValue('pinia')
    await wrapper.get('input').trigger('keyup.enter')
    await wrapper.get('button[aria-label="board.detail.clearSearch"]').trigger('click')

    expect(wrapper.emitted('update:searchType')).toEqual([['AUTHOR']])
    expect(wrapper.emitted('update:searchQuery')).toEqual([['pinia']])
    expect(wrapper.emitted('search')).toHaveLength(1)
    expect(wrapper.emitted('clear')).toHaveLength(1)
  })

  it('does not emit search while IME composition is active', async () => {
    const wrapper = mountSearch()
    const input = wrapper.get('input')

    await input.trigger('keyup.enter', { isComposing: true })
    expect(wrapper.emitted('search')).toBeUndefined()

    await input.trigger('keyup.enter')
    expect(wrapper.emitted('search')).toHaveLength(1)
  })
})
