import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref, type Ref } from 'vue'
import UserSelectModal from '../UserSelectModal.vue'

type SelectableUser = {
  userId: number
  loginId: string
  displayName: string
  email?: string
  currentManager?: boolean
}

const mocks = vi.hoisted(() => ({
  adminCalls: [] as Array<{ params: Ref<Record<string, unknown>>, enabled: Ref<boolean> }>,
  boardCalls: [] as Array<{ boardUrl: Ref<string>, params: Ref<Record<string, unknown>>, enabled: Ref<boolean> }>,
  adminUsers: [
    { userId: 1, loginId: 'admin-user', displayName: 'Admin User', email: 'admin@test.com' },
    { userId: 3, loginId: 'second-user', displayName: 'Second User', email: 'second@test.com' },
  ] as SelectableUser[],
  boardUsers: [
    { userId: 2, loginId: 'board-user', displayName: 'Board User', currentManager: true },
  ] as SelectableUser[],
  adminLoading: false,
  boardLoading: false,
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useUsers: (params: Ref<Record<string, unknown>>, enabled: Ref<boolean>) => {
      mocks.adminCalls.push({ params, enabled })
      return {
        data: ref({
          content: mocks.adminUsers,
        }),
        isLoading: ref(mocks.adminLoading),
      }
    },
  }),
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardManagerCandidates: (
      boardUrl: Ref<string>,
      params: Ref<Record<string, unknown>>,
      enabled: Ref<boolean>
    ) => {
      mocks.boardCalls.push({ boardUrl, params, enabled })
      return {
        data: ref({
          content: mocks.boardUsers,
        }),
        isLoading: ref(mocks.boardLoading),
      }
    },
  }),
}))

const mountModal = (props: {
  isOpen: boolean
  title?: string
  selectionMode?: 'single' | 'multiple'
  source?: 'admin' | 'board-manager-candidates'
  boardUrl?: string
  initialSelectedIds?: number[]
  excludeUserIds?: number[]
}) => mount(UserSelectModal, {
  props,
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseModal: {
        props: ['isOpen'],
        template: '<div v-if="isOpen"><slot /></div>',
      },
      BaseInput: {
        props: ['modelValue', 'id', 'name', 'label', 'autocomplete', 'hideLabel'],
        emits: ['update:modelValue'],
        template: `
          <div>
            <label v-if="label" :for="id" :class="{ 'sr-only': hideLabel }">{{ label }}</label>
            <input
              :id="id"
              :name="name"
              :autocomplete="autocomplete"
              :value="modelValue"
              @input="$emit('update:modelValue', $event.target.value)"
            />
          </div>
        `,
      },
      BaseButton: {
        props: ['disabled'],
        template: '<button :disabled="disabled"><slot /></button>',
      },
      BaseSpinner: {
        template: '<div data-testid="spinner" />',
      },
    },
  },
})

describe('UserSelectModal', () => {
  beforeEach(() => {
    mocks.adminCalls.length = 0
    mocks.boardCalls.length = 0
    mocks.adminUsers = [
      { userId: 1, loginId: 'admin-user', displayName: 'Admin User', email: 'admin@test.com' },
      { userId: 3, loginId: 'second-user', displayName: 'Second User', email: 'second@test.com' },
    ]
    mocks.boardUsers = [
      { userId: 2, loginId: 'board-user', displayName: 'Board User', currentManager: true },
    ]
    mocks.adminLoading = false
    mocks.boardLoading = false
  })

  it('uses admin users by default', () => {
    const wrapper = mountModal({ isOpen: true })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(true)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(false)
    expect(wrapper.get('label[for="user-select-search"]').text()).toBe('admin.users.searchPlaceholder')
    expect(wrapper.get('#user-select-search').attributes()).toMatchObject({
      name: 'userSelectSearch',
      autocomplete: 'off',
    })
    expect(wrapper.text()).toContain('admin-user')
    expect(wrapper.text()).toContain('second-user')
    expect(wrapper.text()).toContain('common.email')
  })

  it('uses board manager candidates without email column', () => {
    const wrapper = mountModal({
      isOpen: true,
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(false)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(true)
    expect(mocks.boardCalls.at(-1)?.boardUrl.value).toBe('free')
    expect(wrapper.text()).toContain('board-user')
    expect(wrapper.text()).toContain('current')
    expect(wrapper.text()).not.toContain('common.email')
  })

  it('disables both queries while closed', () => {
    mountModal({
      isOpen: false,
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    expect(mocks.adminCalls.at(-1)?.enabled.value).toBe(false)
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(false)
  })

  it('updates search params when the search field changes', async () => {
    const wrapper = mountModal({ isOpen: true })

    await wrapper.get('#user-select-search').setValue('admin')

    expect(mocks.adminCalls.at(-1)?.params.value).toMatchObject({
      page: 0,
      size: 10,
      q: 'admin',
    })
  })

  it('emits a single selected user on confirm', async () => {
    const wrapper = mountModal({ isOpen: true })

    await wrapper.findAll('tbody tr')[0].trigger('click')
    await wrapper.get('button:last-child').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      [{ userId: 1, loginId: 'admin-user', displayName: 'Admin User', email: 'admin@test.com' }],
    ]])
  })

  it('supports multiple selection and toggling selected users', async () => {
    const wrapper = mountModal({ isOpen: true, selectionMode: 'multiple' })
    const rows = wrapper.findAll('tbody tr')

    await rows[0].trigger('click')
    await rows[1].trigger('click')
    expect(wrapper.text()).toContain('선택 2명')

    await rows[0].trigger('click')
    await wrapper.get('button:last-child').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      [{ userId: 3, loginId: 'second-user', displayName: 'Second User', email: 'second@test.com' }],
    ]])
  })

  it('preselects initial users and filters excluded users', async () => {
    const wrapper = mountModal({
      isOpen: true,
      selectionMode: 'multiple',
      initialSelectedIds: [3],
      excludeUserIds: [1],
    })

    await flushPromises()

    expect(wrapper.text()).not.toContain('admin-user')
    expect(wrapper.text()).toContain('second-user')
    expect(wrapper.text()).toContain('선택 1명')

    await wrapper.get('button:last-child').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      [{ userId: 3, loginId: 'second-user', displayName: 'Second User', email: 'second@test.com' }],
    ]])
  })

  it('renders loading and empty states', () => {
    mocks.adminLoading = true
    const loadingWrapper = mountModal({ isOpen: true })

    expect(loadingWrapper.get('[data-testid="spinner"]').exists()).toBe(true)

    mocks.adminLoading = false
    mocks.adminUsers = []
    const emptyWrapper = mountModal({ isOpen: true })

    expect(emptyWrapper.text()).toContain('common.noData')
  })
})
