import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

const pageResponse = (content = [{
  userId: 1,
  displayName: 'Blocked User',
  email: 'blocked@example.com',
}]) => ({
  success: true,
  data: {
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 2 : 0,
    size: 20,
    number: 0,
    first: true,
    last: false,
    empty: content.length === 0,
  },
})

const mocks = vi.hoisted(() => ({
  getBlockList: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getBlockList: mocks.getBlockList,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
  },
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      t: (key: string) => key,
    },
  }),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

import BlockList from '../BlockList.vue'

const BlockButtonStub = defineComponent({
  name: 'BlockButtonStub',
  props: {
    userId: {
      type: Number,
      required: true,
    },
    initialBlocked: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['block-change'],
  template: '<button data-testid="block-button" @click="$emit(\'block-change\')">unblock</button>',
})

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
  template: '<button data-testid="size-change" @click="$emit(\'update:modelValue\', 50); $emit(\'change\')">size</button>',
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
  template: '<button data-testid="page-change" @click="$emit(\'page-change\', 1)">{{ currentPage }}/{{ totalPages }}</button>',
})

const mountBlockList = () => mount(BlockList, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BlockButton: BlockButtonStub,
      PageSizeSelector: PageSizeSelectorStub,
      Pagination: PaginationStub,
      BaseSkeleton: true,
      EmptyState: true,
      UserX: true,
    },
  },
})

describe('BlockList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getBlockList.mockResolvedValue({ data: pageResponse() })
  })

  it('fetches the first page with page and size params', async () => {
    mountBlockList()
    await flushPromises()

    expect(mocks.getBlockList).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('updates page and resets page when page size changes', async () => {
    const wrapper = mountBlockList()
    await flushPromises()

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    await flushPromises()
    expect(mocks.getBlockList).toHaveBeenLastCalledWith({ page: 1, size: 20 })

    await wrapper.get('[data-testid="size-change"]').trigger('click')
    await flushPromises()
    expect(mocks.getBlockList).toHaveBeenLastCalledWith({ page: 0, size: 50 })
  })

  it('renders PageResponse content and supports legacy array payloads', async () => {
    const wrapper = mountBlockList()
    await flushPromises()

    expect(wrapper.text()).toContain('Blocked User')

    mocks.getBlockList.mockResolvedValueOnce({
      data: {
        success: true,
        data: [{
          userId: 2,
          displayName: 'Legacy User',
          email: 'legacy@example.com',
        }],
      },
    })

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Legacy User')
  })

  it('moves to the previous page before refreshing the last item on a page', async () => {
    const wrapper = mountBlockList()
    await flushPromises()

    await wrapper.get('[data-testid="page-change"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="block-button"]').trigger('click')
    await flushPromises()

    expect(mocks.getBlockList).toHaveBeenLastCalledWith({ page: 0, size: 20 })
  })
})
