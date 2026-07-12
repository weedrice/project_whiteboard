import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminManagement from '../AdminManagement.vue'

const superAdminsData = ref([
  {
    userId: 1,
    loginId: 'root',
    displayName: 'Root',
    createdAt: '2026-01-01T00:00:00',
    isSuperAdmin: true,
  },
  {
    userId: 2,
    loginId: 'legacy',
    displayName: 'Legacy',
    createdAt: '2026-01-02T00:00:00',
    superAdmin: false,
    isSuperAdmin: true,
  },
])
const isSuperAdminsLoading = ref(false)
const updateSuperAdminStatus = vi.fn()
const addToast = vi.fn()
const confirm = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm }),
}))

vi.mock('@/composables/useAdmin', () => ({
  useAdmin: () => ({
    useSuperAdmins: () => ({
      data: superAdminsData,
      isLoading: isSuperAdminsLoading,
    }),
    useUpdateSuperAdminStatus: () => ({
      mutateAsync: updateSuperAdminStatus,
    }),
  }),
}))

const BaseInputStub = defineComponent({
  props: {
    modelValue: { type: String, default: '' },
    id: { type: String, default: '' },
    placeholder: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  template: '<input :id="id" :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})

const BaseButtonStub = defineComponent({
  props: {
    type: { type: String, default: 'button' },
  },
  template: '<button :type="type"><slot /></button>',
})

const AdminPaginatedTableStub = defineComponent({
  props: {
    items: { type: Array, default: () => [] },
  },
  template: `
    <div>
      <div v-for="item in items" :key="item.userId" data-testid="admin-row">
        <span data-testid="login-id"><slot name="cell-loginId" :item="item" /></span>
        <span data-testid="display-name"><slot name="cell-displayName" :item="item" /></span>
        <span data-testid="status"><slot name="cell-status" :item="item" /></span>
        <span data-testid="created-at"><slot name="cell-createdAt" :item="item" /></span>
        <span data-testid="actions"><slot name="cell-actions" :item="item" /></span>
      </div>
    </div>
  `,
})

const mountAdminManagement = () => mount(AdminManagement, {
  global: {
    stubs: {
      UserPlus: true,
      BaseInput: BaseInputStub,
      BaseButton: BaseButtonStub,
      AdminActionButton: {
        emits: ['click'],
        template: '<button type="button" data-testid="status-action" @click="$emit(\'click\')"><slot /></button>',
      },
      AdminDataPage: {
        template: '<section><slot /></section>',
      },
      AdminFormPanel: {
        template: '<section><slot /></section>',
      },
      AdminInlineForm: {
        emits: ['submit'],
        template: '<form @submit="$emit(\'submit\', $event)"><slot /></form>',
      },
      AdminPaginatedTable: AdminPaginatedTableStub,
      AdminStatusBadge: {
        props: ['label', 'variant'],
        template: '<span :data-variant="variant">{{ label }}</span>',
      },
    },
  },
})

describe('AdminManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    updateSuperAdminStatus.mockResolvedValue(undefined)
    confirm.mockResolvedValue(true)
    superAdminsData.value = [
      {
        userId: 1,
        loginId: 'root',
        displayName: 'Root',
        createdAt: '2026-01-01T00:00:00',
        isSuperAdmin: true,
      },
      {
        userId: 2,
        loginId: 'legacy',
        displayName: 'Legacy',
        createdAt: '2026-01-02T00:00:00',
        superAdmin: false,
        isSuperAdmin: true,
      },
    ]
  })

  it('maps current and legacy super admin flags into table rows', () => {
    const wrapper = mountAdminManagement()
    const rows = wrapper.findAll('[data-testid="admin-row"]')

    expect(rows[0].text()).toContain('root')
    expect(rows[0].text()).toContain('common.active')
    expect(rows[1].text()).toContain('legacy')
    expect(rows[1].text()).toContain('common.inactive')
  })

  it('requires a login id before creating a super admin', async () => {
    const wrapper = mountAdminManagement()

    await wrapper.get('form').trigger('submit')
    await wrapper.get('#superAdminLoginId').setValue('   ')
    await wrapper.get('form').trigger('submit')

    expect(updateSuperAdminStatus).not.toHaveBeenCalled()
    expect(addToast).toHaveBeenCalledWith('admin.admins.messages.inputLoginId', 'warning')
  })

  it('creates a super admin through the activate action', async () => {
    const wrapper = mountAdminManagement()

    await wrapper.get('#superAdminLoginId').setValue('  new-admin  ')
    await wrapper.get('form').trigger('submit')

    expect(updateSuperAdminStatus).toHaveBeenCalledWith({ loginId: 'new-admin', action: 'activate' })
    expect(addToast).toHaveBeenCalledWith('admin.admins.messages.added', 'success')
    expect((wrapper.get('#superAdminLoginId').element as HTMLInputElement).value).toBe('')
  })

  it('toggles active super admins through the deactivate action', async () => {
    const wrapper = mountAdminManagement()

    await wrapper.findAll('[data-testid="status-action"]')[0].trigger('click')

    expect(updateSuperAdminStatus).toHaveBeenCalledWith({ loginId: 'root', action: 'deactivate' })
    expect(addToast).toHaveBeenCalledWith('admin.admins.messages.statusChanged', 'success')
  })
})
