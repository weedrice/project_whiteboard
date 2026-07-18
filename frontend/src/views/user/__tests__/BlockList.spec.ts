import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref, type Ref } from 'vue'

import BlockList from '../BlockList.vue'

type BlockListParams = { page: number; size: number }

type BlockListQueryState = {
  data: Ref<unknown>
  isLoading: Ref<boolean>
  isError: Ref<boolean>
  error: Ref<unknown>
  refetch: ReturnType<typeof vi.fn>
}

const blockListMock = vi.hoisted(() => ({
  latestParams: undefined as Ref<BlockListParams> | undefined,
  queryState: undefined as unknown as BlockListQueryState,
  loggerError: vi.fn(),
}))

const translate = (key: string, params?: Record<string, unknown>) => {
  if (key === 'common.paginationSummary.itemUnit') return 'items'
  if (key === 'common.paginationSummary.total') return `Total ${params?.count} ${params?.unit}`
  if (key === 'common.messages.loadFailed') return 'Load failed'
  if (key === 'user.blockList.title') return 'Blocked users'
  if (key === 'user.blockList.empty') return 'No blocked users'
  return key
}

vi.mock('@/features/user/useUser', () => ({
  useUser: () => ({
    useBlockList: (params?: Ref<BlockListParams>) => {
      blockListMock.latestParams = params
      return blockListMock.queryState
    },
  }),
}))

vi.mock('@/utils/logger', () => ({
  default: { error: blockListMock.loggerError },
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: translate,
    },
    install: vi.fn(),
  }),
  useI18n: () => ({
    t: translate,
  }),
}))

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
      $t: translate,
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
  beforeEach(() => {
    blockListMock.latestParams = undefined
    blockListMock.loggerError.mockReset()
    blockListMock.queryState = {
      data: ref(null),
      isLoading: ref(false),
      isError: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }
  })

  it('renders users from the paged block list query cache', () => {
    blockListMock.queryState.data.value = {
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
    expect(wrapper.text()).toContain('Total 2 items')
    expect(wrapper.get('[data-test="page-change"]').text()).toContain('0/3')
    expect(wrapper.findAll('[data-test="block-button"]')).toHaveLength(2)
  })
  it('renders an empty list before block list data is loaded', () => {
    blockListMock.queryState.data.value = null

    const wrapper = mountList()

    expect(wrapper.findAll('[data-test="block-button"]')).toHaveLength(0)
    expect(wrapper.find('[data-test="page-change"]').exists()).toBe(false)
  })

  it('shows an error state and retries through the query refetch', async () => {
    blockListMock.queryState.error.value = new Error('network')
    blockListMock.queryState.isError.value = true
    const wrapper = mountList()

    expect(wrapper.get('[data-test="error-state"]').text()).toBe('Load failed')
    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)

    await wrapper.get('[data-test="error-state"]').trigger('click')

    expect(blockListMock.queryState.refetch).toHaveBeenCalledTimes(1)
  })

  it('updates query params when page or page size changes', async () => {
    blockListMock.queryState.data.value = {
      content: [{ userId: 1, displayName: 'Ada', secondaryText: 'ada-login' }],
      totalElements: 1,
      totalPages: 2,
    }
    const wrapper = mountList()

    expect(blockListMock.latestParams?.value).toEqual({ page: 0, size: 20 })

    await wrapper.get('[data-test="page-change"]').trigger('click')
    expect(blockListMock.latestParams?.value).toEqual({ page: 1, size: 20 })

    await wrapper.get('[data-test="size-change"]').trigger('click')
    expect(blockListMock.latestParams?.value).toEqual({ page: 0, size: 50 })
  })

  it('moves to the previous page after the last visible user is unblocked without manual refetch', async () => {
    blockListMock.queryState.data.value = {
      content: [{ userId: 1, displayName: 'Ada', secondaryText: 'ada-login' }],
      totalElements: 21,
      totalPages: 2,
    }
    const wrapper = mountList()

    await wrapper.get('[data-test="page-change"]').trigger('click')
    expect(blockListMock.latestParams?.value).toEqual({ page: 1, size: 20 })

    await wrapper.get('[data-test="block-button"]').trigger('click')

    expect(blockListMock.latestParams?.value).toEqual({ page: 0, size: 20 })
    expect(blockListMock.queryState.refetch).not.toHaveBeenCalled()
  })

  it('logs query errors from the block list query', async () => {
    const wrapper = mountList()
    const error = new Error('load failed')

    blockListMock.queryState.error.value = error
    await nextTick()

    expect(blockListMock.loggerError).toHaveBeenCalledWith('Failed to fetch blocked users:', error)
    wrapper.unmount()
  })
})
