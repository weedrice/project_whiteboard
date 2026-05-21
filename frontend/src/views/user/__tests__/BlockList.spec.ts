import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import type BlockListComponent from '../BlockList.vue'

type BlockList = typeof BlockListComponent

let queryState: {
  data: ReturnType<typeof ref<unknown>>
  isLoading: ReturnType<typeof ref<boolean>>
  error: ReturnType<typeof ref<unknown>>
  refetch: ReturnType<typeof vi.fn>
}
let BlockList: BlockList

const mountList = () => mount(BlockList, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseSkeleton: true,
      EmptyState: true,
      BlockButton: {
        emits: ['block-change'],
        template: '<button data-test="block-button" @click="$emit(\'block-change\', false)">unblock</button>',
      },
      UserX: true,
    },
  },
})

describe('BlockList', () => {
  beforeEach(async () => {
    vi.resetModules()
    queryState = {
      data: ref(null),
      isLoading: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }
    vi.doMock('@/composables/useUser', () => ({
      useUser: () => ({
        useBlockList: () => queryState,
      }),
    }))
    vi.doMock('@/utils/logger', () => ({
      default: { error: vi.fn() },
    }))
    BlockList = (await import('../BlockList.vue')).default
  })

  it('renders users from the block list query cache', () => {
    queryState.data.value = {
      content: [
        { userId: 1, displayName: 'Ada', email: 'ada@example.com' },
        { userId: 2, displayName: 'Grace', email: 'grace@example.com' },
      ],
    }

    const wrapper = mountList()

    expect(wrapper.text()).toContain('Ada')
    expect(wrapper.text()).toContain('Grace')
    expect(wrapper.findAll('[data-test="block-button"]')).toHaveLength(2)
  })

  it('does not manually refetch when a child block-change event is emitted', async () => {
    queryState.data.value = [
      { userId: 1, displayName: 'Ada', email: 'ada@example.com' },
    ]
    const wrapper = mountList()

    await wrapper.get('[data-test="block-button"]').trigger('click')

    expect(queryState.refetch).not.toHaveBeenCalled()
  })
})
