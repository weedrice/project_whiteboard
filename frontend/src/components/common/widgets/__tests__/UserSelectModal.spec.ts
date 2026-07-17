import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest'
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
  adminError: false,
  boardError: false,
  refetchAdmin: vi.fn(),
  refetchBoard: vi.fn(),
}))

vi.mock('@/features/admin/useAdmin', () => ({
  useAdmin: () => ({
    useUsers: (params: Ref<Record<string, unknown>>, enabled: Ref<boolean>) => {
      mocks.adminCalls.push({ params, enabled })
      return {
        data: ref({
          content: mocks.adminUsers,
        }),
        isLoading: ref(mocks.adminLoading),
        isError: ref(mocks.adminError),
        refetch: mocks.refetchAdmin,
      }
    },
  }),
}))

vi.mock('@/features/board/useBoard', () => ({
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
        isError: ref(mocks.boardError),
        refetch: mocks.refetchBoard,
      }
    },
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'user.selectModal.selectedCount') return `선택 ${params?.count}명`
      return key
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
      $t: (key: string, params?: Record<string, unknown>) => {
        if (key === 'user.selectModal.selectedCount') return `선택 ${params?.count}명`
        return key
      },
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

const mountAccessibleModal = (attachTo: HTMLElement) => mount(UserSelectModal, {
  props: { isOpen: true },
  attachTo,
  global: {
    mocks: { $t: (key: string) => key },
    stubs: {
      Teleport: true,
      BaseInput: {
        props: ['modelValue', 'id', 'name', 'label'],
        emits: ['update:modelValue'],
        template: '<label :for="id">{{ label }}<input :id="id" :value="modelValue" /></label>',
      },
      BaseSpinner: { template: '<div data-testid="spinner" />' },
    },
  },
})

function getActionButton(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === label)

  if (!button) {
    throw new Error(`Button not found: ${label}`)
  }

  return button
}

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
    mocks.adminError = false
    mocks.boardError = false
    mocks.refetchAdmin.mockReset()
    mocks.refetchBoard.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
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
    expect(wrapper.text()).toContain('user.selectModal.currentManager')
    expect(wrapper.findAll('th').map((header) => header.text())).not.toContain('common.email')
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

  it('updates search params after the search field settles', async () => {
    vi.useFakeTimers()
    const wrapper = mountModal({ isOpen: true })

    await wrapper.get('#user-select-search').setValue('admin')

    expect(mocks.adminCalls.at(-1)?.params.value).toMatchObject({
      page: 0,
      size: 10,
      q: '',
    })

    vi.advanceTimersByTime(300)
    await flushPromises()

    expect(mocks.adminCalls.at(-1)?.params.value).toMatchObject({
      page: 0,
      size: 10,
      q: 'admin',
    })
  })

  it('emits a single selected user on confirm', async () => {
    const wrapper = mountModal({ isOpen: true })
    const saveButton = getActionButton(wrapper, 'common.save')

    expect(saveButton.attributes('disabled')).toBeDefined()
    await wrapper.findAll('tbody tr')[0].trigger('click')
    expect(saveButton.attributes('disabled')).toBeUndefined()
    await saveButton.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      [{ userId: 1, loginId: 'admin-user', displayName: 'Admin User', email: 'admin@test.com' }],
    ]])
  })

  it('supports multiple selection and toggling selected users', async () => {
    const wrapper = mountModal({ isOpen: true, selectionMode: 'multiple' })
    const rows = wrapper.findAll('tbody tr')

    await rows[0].trigger('click')
    await rows[1].trigger('click')
    expect(wrapper.findAll('tbody tr')[0].attributes('aria-selected')).toBe('true')
    expect(wrapper.findAll('tbody tr')[0].attributes('aria-label')).toBe('user.selectModal.deselectUser')
    expect(wrapper.text()).toContain('선택 2명')

    await rows[0].trigger('click')
    expect(wrapper.findAll('tbody tr')[0].attributes('aria-selected')).toBe('false')
    expect(wrapper.findAll('tbody tr')[0].attributes('aria-label')).toBe('user.selectModal.selectUser')
    expect(wrapper.text()).toContain('선택 1명')
    await getActionButton(wrapper, 'common.save').trigger('click')

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

    await getActionButton(wrapper, 'common.save').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[
      [{ userId: 3, loginId: 'second-user', displayName: 'Second User', email: 'second@test.com' }],
    ]])
  })

  it('resets selection and search when source options change while open', async () => {
    vi.useFakeTimers()
    const wrapper = mountModal({ isOpen: true })
    const saveButton = getActionButton(wrapper, 'common.save')

    await wrapper.findAll('tbody tr')[0].trigger('click')
    await wrapper.get('#user-select-search').setValue('admin')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(saveButton.attributes('disabled')).toBeUndefined()
    expect(mocks.adminCalls.at(-1)?.params.value.q).toBe('admin')

    await wrapper.setProps({
      source: 'board-manager-candidates',
      boardUrl: 'free',
      excludeUserIds: [2],
    })
    await flushPromises()
    vi.advanceTimersByTime(300)
    await flushPromises()

    expect((wrapper.get('#user-select-search').element as HTMLInputElement).value).toBe('')
    expect(saveButton.attributes('disabled')).toBeDefined()
    expect(mocks.boardCalls.at(-1)?.enabled.value).toBe(true)
    expect(mocks.boardCalls.at(-1)?.boardUrl.value).toBe('free')
    expect(wrapper.text()).not.toContain('board-user')
  })

  it('renders loading and empty states', () => {
    mocks.adminLoading = true
    const loadingWrapper = mountModal({ isOpen: true })

    expect(loadingWrapper.find('[data-testid="spinner"]').exists()).toBe(true)

    mocks.adminLoading = false
    mocks.adminUsers = []
    const emptyWrapper = mountModal({ isOpen: true })

    expect(emptyWrapper.text()).toContain('common.noData')
  })

  it('shows the selected source error and retries only that source', async () => {
    mocks.adminError = true
    const adminWrapper = mountModal({ isOpen: true })

    expect(adminWrapper.get('[role="alert"]').text()).toContain('common.messages.loadFailed')
    expect(adminWrapper.find('table').exists()).toBe(false)
    await getActionButton(adminWrapper, 'common.error.retry').trigger('click')
    expect(mocks.refetchAdmin).toHaveBeenCalledOnce()
    expect(mocks.refetchBoard).not.toHaveBeenCalled()

    mocks.adminError = true
    mocks.boardError = false
    const boardWrapper = mountModal({
      isOpen: true,
      source: 'board-manager-candidates',
      boardUrl: 'free',
    })

    expect(boardWrapper.find('[role="alert"]').exists()).toBe(false)
    expect(boardWrapper.text()).toContain('board-user')
  })

  it('keeps the compact BaseTable chrome inside the modal', () => {
    const wrapper = mountModal({ isOpen: true, selectionMode: 'multiple' })

    expect(wrapper.get('.nv-base-table').classes()).not.toContain('shadow')
    expect(wrapper.get('.overflow-x-auto').classes()).toEqual(expect.arrayContaining(['max-h-[420px]', 'overflow-y-auto']))
    expect(wrapper.get('th').classes()).toEqual(expect.arrayContaining(['px-2', 'py-2']))
    expect(wrapper.get('td').classes()).toEqual(expect.arrayContaining(['px-2', 'py-1.5']))
  })

  it('traps focus in the real modal, closes on Escape, and restores the opener', async () => {
    const opener = document.createElement('button')
    const host = document.createElement('div')
    document.body.append(opener, host)
    opener.focus()
    const wrapper = mountAccessibleModal(host)
    await flushPromises()

    const dialog = wrapper.get('[role="dialog"]')
    const focusable = Array.from(dialog.element.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ))
    const first = focusable[0]
    const last = focusable.at(-1)!
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(document.activeElement).toBe(first)

    last.focus()
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    document.dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(first)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.setProps({ isOpen: false })
    await flushPromises()
    expect(document.activeElement).toBe(opener)
    wrapper.unmount()
    opener.remove()
    host.remove()
  })
})
