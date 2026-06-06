import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'
import AdminActionButton from '../AdminActionButton.vue'
import AdminDataPage from '../AdminDataPage.vue'
import AdminFilterField from '../AdminFilterField.vue'
import AdminFormPanel from '../AdminFormPanel.vue'
import AdminInlineForm from '../AdminInlineForm.vue'
import AdminPaginatedTable from '../AdminPaginatedTable.vue'
import AdminStatusBadge from '../AdminStatusBadge.vue'
import BooleanBadge from '../BooleanBadge.vue'
import HttpStatusBadge from '../HttpStatusBadge.vue'

const AdminPageHeaderStub = defineComponent({
  props: {
    title: String,
    description: String,
  },
  template: `
    <header>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
      <slot name="actions" />
    </header>
  `,
})

describe('admin common components', () => {
  it('renders AdminDataPage slots around the shared page header', () => {
    const wrapper = mount(AdminDataPage, {
      props: {
        title: 'Users',
        description: 'Manage users',
      },
      slots: {
        actions: '<button>Add</button>',
        filters: '<div data-test="filters">Filters</div>',
        toolbar: '<div data-test="toolbar">Toolbar</div>',
        default: '<main>Table</main>',
        footer: '<footer>Footer</footer>',
      },
      global: {
        stubs: {
          AdminPageHeader: AdminPageHeaderStub,
        },
      },
    })

    expect(wrapper.get('h1').text()).toBe('Users')
    expect(wrapper.text()).toContain('Manage users')
    expect(wrapper.text()).toContain('Add')
    expect(wrapper.get('[data-test="filters"]').text()).toBe('Filters')
    expect(wrapper.get('[data-test="toolbar"]').text()).toBe('Toolbar')
    expect(wrapper.get('main').text()).toBe('Table')
    expect(wrapper.get('footer').text()).toBe('Footer')
  })

  it('connects AdminFilterField label and sizing class', () => {
    const wrapper = mount(AdminFilterField, {
      props: {
        label: 'Status',
        forId: 'status',
        widthClass: 'w-40',
      },
      slots: {
        default: '<select id="status" />',
      },
    })

    expect(wrapper.classes()).toContain('filter-item')
    expect(wrapper.classes()).toContain('w-40')
    expect(wrapper.get('label').attributes('for')).toBe('status')
    expect(wrapper.get('label').text()).toBe('Status')
  })

  it('renders form panel and inline form as reusable admin form layout', async () => {
    const panel = mount(AdminFormPanel, {
      props: {
        title: 'Block IP',
        description: 'Add a blocked IP address.',
        maxWidthClass: 'max-w-xl',
      },
      slots: {
        default: '<input aria-label="IP" />',
      },
      global: {
        stubs: {
          AdminPanel: {
            props: ['maxWidthClass'],
            template: '<section :data-max-width="maxWidthClass"><slot /></section>',
          },
        },
      },
    })
    const form = mount(AdminInlineForm, {
      slots: {
        default: '<button type="submit">Save</button>',
      },
    })

    await form.get('form').trigger('submit.prevent')

    expect(panel.text()).toContain('Block IP')
    expect(panel.text()).toContain('Add a blocked IP address.')
    expect(panel.get('section').attributes('data-max-width')).toBe('max-w-xl')
    expect(form.get('form').classes()).toContain('admin-inline-form')
  })

  it('renders boolean and HTTP status badges with expected variants and classes', () => {
    const booleanBadge = mount(BooleanBadge, {
      props: {
        value: false,
        trueLabel: 'Active',
        falseLabel: 'Inactive',
        falseVariant: 'danger',
      },
    })
    const httpBadge = mount(HttpStatusBadge, {
      props: {
        status: 500,
      },
    })

    expect(booleanBadge.text()).toBe('Inactive')
    expect(booleanBadge.get('span').classes()).toContain('nv-status-danger')
    expect(httpBadge.text()).toBe('500')
    expect(httpBadge.get('.http-status-badge').classes()).toContain('status-500')
  })

  it('renders generic admin status badges and paginated table sections', () => {
    const statusBadge = mount(AdminStatusBadge, {
      props: {
        label: 'Pending',
        variant: 'warning',
        statusClass: 'pending-class',
      },
    })
    const table = mount(AdminPaginatedTable, {
      props: {
        columns: [{ key: 'name', label: 'Name' }],
        items: [{ id: 1, name: 'Ada' }],
        rowKey: (item: object) => (item as { id: number }).id,
        page: 0,
        totalPages: 2,
        summary: 'Total 1',
      },
      slots: {
        'cell-name': '<template #default="{ item }"><strong>{{ item.name }}</strong></template>',
        'footer-description': '<span data-test="description">Rows can be opened.</span>',
      },
      global: {
        stubs: {
          Pagination: {
            template: '<button data-test="pagination">page</button>',
          },
        },
      },
    })

    expect(statusBadge.get('.admin-status-badge').classes()).toContain('pending-class')
    expect(table.text()).toContain('Ada')
    expect(table.text()).toContain('Total 1')
    expect(table.get('[data-test="description"]').text()).toBe('Rows can be opened.')
  })

  it('normalizes admin action button labels for icon-only controls', async () => {
    const wrapper = mount(AdminActionButton, {
      props: {
        label: 'Save',
        tone: 'accent',
        iconOnly: true,
      },
      slots: {
        default: '<span aria-hidden="true">icon</span>',
      },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.get('button').attributes('type')).toBe('button')
    expect(wrapper.get('button').attributes('aria-label')).toBe('Save')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
