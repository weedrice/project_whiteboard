import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref, type Ref } from 'vue'

import type BlockListComponent from '../BlockList.vue'

type BlockList = typeof BlockListComponent
type BlockListParams = { page: number; size: number }

let queryState: {
  data: ReturnType<typeof ref<unknown>>
  isLoading: ReturnType<typeof ref<boolean>>
  isError: ReturnType<typeof ref<boolean>>
  error: ReturnType<typeof ref<unknown>>
  refetch: ReturnType<typeof vi.fn>
}
let BlockList: BlockList
let latestParams: Ref<BlockListParams> | undefined
let loggerError: ReturnType<typeof vi.fn>

const PageSizeSelectorStub = defineComponent({
  name: 'PageSizeSelectorStub',
  props: {
    modelValue: {
      type: Number,
      required: true,
    },
    options: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['update:modelValue', 'change'],
  template: '<button data-test="size-change" @click="$emit(\'update:modelValue\', 50); $emit(\'change\')">size</button>',
})

const PaginationStub = defineComponent({
  name: 'PaginationStub',
  props: {
    currentPage: {
      type: Number,
      required: true,
    },
    totalPages: {
      type: Number,
      required: true,
    },
  },
  emits: ['page-change'],
  template: '<button data-test="page-change" @click="$emit(\'page-change\', 1)">{{ currentPage }}/{{ totalPages }}</button>',
})

const ErrorStateStub = defineComponent({
  name: 'ErrorState',
  props: {
    message: {
      type: String,
      required: true,
    },
    showRetry: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['retry'],
  template: '<button type="button" data-test="error-state" @click="$emit(\'retry\')">{{ message }}</button>',
})

const mountList = () => mount(BlockList, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseSkeleton: true,
      EmptyState: true,
      ErrorState: ErrorStateStub,
      PageSizeSelector: PageSizeSelectorStub,
      Pagination: PaginationStub,
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
    latestParams = undefined
    loggerError = vi.fn()
    queryState = {
      data: ref(null),
      isLoading: ref(false),
      isError: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }
    vi.doMock('@/composables/useUser', () => ({
      useUser: () => ({
        useBlockList: (params?: Ref<BlockListParams>) => {
          latestParams = params
          return queryState
        },
      }),
    }))
    vi.doMock('@/utils/logger', () => ({
      default: { error: loggerError },
    }))
    vi.doMock('vue-i18n', () => ({
      createI18n: () => ({
        global: {
          t: (key: string) => key,
        },
        install: vi.fn(),
      }),
      useI18n: () => ({
        t: (key: string) => key,
      }),
    }))
    BlockList = (await import('../BlockList.vue')).default
  }, 30000)

  it('renders users from the paged block list query cache', () => {
    queryState.data.value = {
      content: [
        { userId: 1, displayName: 'Ada', secondaryText: 'ada-login' },
        { userId: 2, displayName: 'Grace', secondaryText: 'grace-login' },
      ],
      totalElements: 2,
      totalPages: 3,
    }

    const wrapper = mountList()

    expect(wrapper.text()).toContain('Ada')
    expect(wrapper.text()).toContain('Grace')
    expect(wrapper.text()).toContain('ada-login')
    expect(wrapper.text()).toContain('grace-login')
    expect(wrapper.text()).toContain('총 2건')
    expect(wrapper.get('[data-test="page-change"]').text()).toContain('0/3')
    expect(wrapper.findAll('[data-test="block-button"]')).toHaveLength(2)
  })
  it('renders an empty list before block list data is loaded', () => {
    queryState.data.value = null

    const wrapper = mountList()

    expect(wrapper.findAll('[data-test="block-button"]')).toHaveLength(0)
    expect(wrapper.find('[data-test="page-change"]').exists()).toBe(false)
  })

  it('shows an error state and retries through the query refetch', async () => {
    queryState.error.value = new Error('network')
    queryState.isError.value = true
    const wrapper = mountList()

    expect(wrapper.get('[data-test="error-state"]').text()).toBe('common.messages.loadFailed')
    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)

    await wrapper.get('[data-test="error-state"]').trigger('click')

    expect(queryState.refetch).toHaveBeenCalledTimes(1)
  })

  it('updates query params when page or page size changes', async () => {
    queryState.data.value = {
      content: [{ userId: 1, displayName: 'Ada', secondaryText: 'ada-login' }],
      totalElements: 1,
      totalPages: 2,
    }
    const wrapper = mountList()

    expect(latestParams?.value).toEqual({ page: 0, size: 20 })

    await wrapper.get('[data-test="page-change"]').trigger('click')
    expect(latestParams?.value).toEqual({ page: 1, size: 20 })

    await wrapper.get('[data-test="size-change"]').trigger('click')
    expect(latestParams?.value).toEqual({ page: 0, size: 50 })
  })

  it('moves to the previous page after the last visible user is unblocked without manual refetch', async () => {
    queryState.data.value = {
      content: [{ userId: 1, displayName: 'Ada', secondaryText: 'ada-login' }],
      totalElements: 21,
      totalPages: 2,
    }
    const wrapper = mountList()

    await wrapper.get('[data-test="page-change"]').trigger('click')
    expect(latestParams?.value).toEqual({ page: 1, size: 20 })

    await wrapper.get('[data-test="block-button"]').trigger('click')

    expect(latestParams?.value).toEqual({ page: 0, size: 20 })
    expect(queryState.refetch).not.toHaveBeenCalled()
  })

  it('logs query errors from the block list query', async () => {
    const wrapper = mountList()
    const error = new Error('load failed')

    queryState.error.value = error
    await nextTick()

    expect(loggerError).toHaveBeenCalledWith('Failed to fetch blocked users:', error)
    wrapper.unmount()
  })
})
