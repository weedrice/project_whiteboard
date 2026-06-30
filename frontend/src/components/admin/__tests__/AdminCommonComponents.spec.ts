import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import AdminActionButton from '../AdminActionButton.vue'
import AdminDataPage from '../AdminDataPage.vue'
import AdminDetailModalShell from '../AdminDetailModalShell.vue'
import AdminFilterActions from '../AdminFilterActions.vue'
import AdminFilterField from '../AdminFilterField.vue'
import AdminFormPanel from '../AdminFormPanel.vue'
import AdminInlineForm from '../AdminInlineForm.vue'
import AdminModalActions from '../AdminModalActions.vue'
import AdminMetricCard from '../AdminMetricCard.vue'
import AdminPaginatedTable from '../AdminPaginatedTable.vue'
import AdminStatusBadge from '../AdminStatusBadge.vue'
import AdminTableActions from '../AdminTableActions.vue'
import BooleanBadge from '../BooleanBadge.vue'
import DescriptionGrid from '../detail/DescriptionGrid.vue'
import DescriptionItem from '../detail/DescriptionItem.vue'
import DetailSection from '../detail/DetailSection.vue'
import HttpStatusBadge from '../HttpStatusBadge.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => ({
      'admin.common.search': '검색',
      'admin.common.reset': '초기화',
      'admin.common.loading': '로딩 중...',
      'common.paginationSummary.itemUnit': '건',
      'common.paginationSummary.total': '총 1건',
      'common.previous': '이전',
      'common.next': '다음',
    })[key] ?? key,
  }),
}))

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

const AdminDetailModalBaseStub = defineComponent({
  props: {
    isOpen: Boolean,
    title: String,
  },
  emits: ['close'],
  template: `
    <section v-if="isOpen" data-test="modal">
      <h1>{{ title }}</h1>
      <button type="button" data-test="close" @click="$emit('close')">close</button>
      <slot />
      <slot name="footer" />
    </section>
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

  it('wraps admin detail modal state and forwards close events', async () => {
    const wrapper = mount(AdminDetailModalShell, {
      props: {
        isOpen: true,
        title: 'Detail',
      },
      slots: {
        default: '<p data-test="content">Loaded content</p>',
      },
      global: {
        stubs: {
          BaseModal: AdminDetailModalBaseStub,
        },
      },
    })

    expect(wrapper.get('h1').text()).toBe('Detail')
    expect(wrapper.get('[data-test="content"]').text()).toBe('Loaded content')

    await wrapper.get('[data-test="close"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('renders detail shell loading, error, and empty states before content', () => {
    const mountShell = (props: Record<string, unknown>) => mount(AdminDetailModalShell, {
      props: {
        isOpen: true,
        title: 'Detail',
        ...props,
      },
      slots: {
        default: '<p>content</p>',
      },
      global: {
        stubs: {
          BaseModal: AdminDetailModalBaseStub,
        },
      },
    })

    expect(mountShell({
        loading: true,
        empty: true,
        loadingText: 'Loading detail',
    }).text()).toContain('Loading detail')

    expect(mountShell({
        error: new Error('failed'),
        empty: true,
        errorText: 'Failed detail',
    }).text()).toContain('Failed detail')

    expect(mountShell({
        empty: true,
        emptyText: 'No detail',
    }).text()).toContain('No detail')
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

  it('renders shared admin filter action buttons and emits commands', async () => {
    const wrapper = mount(AdminFilterActions, {
      props: {
        searchLabel: 'Find',
        resetLabel: 'Clear',
      },
    })

    expect(wrapper.get('.btn-search').text()).toContain('Find')
    expect(wrapper.get('.btn-reset').text()).toContain('Clear')

    await wrapper.get('.btn-search').trigger('click')
    await wrapper.get('.btn-reset').trigger('click')

    expect(wrapper.emitted('search')).toHaveLength(1)
    expect(wrapper.emitted('reset')).toHaveLength(1)
  })

  it('renders shared admin modal action layout with configurable spacing', () => {
    const wrapper = mount(AdminModalActions, {
      props: {
        gapClass: 'gap-2',
        className: 'mt-5',
      },
      slots: {
        default: '<button>Cancel</button><button>Save</button>',
      },
    })

    expect(wrapper.classes()).toContain('flex')
    expect(wrapper.classes()).toContain('justify-end')
    expect(wrapper.classes()).toContain('gap-2')
    expect(wrapper.classes()).toContain('mt-5')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['Cancel', 'Save'])
  })

  it('renders shared admin table action layout with configurable alignment', () => {
    const wrapper = mount(AdminTableActions, {
      props: {
        alignClass: 'justify-center',
        gapClass: 'gap-1',
      },
      slots: {
        default: '<button>View</button><button>Delete</button>',
      },
    })

    expect(wrapper.classes()).toContain('flex')
    expect(wrapper.classes()).toContain('justify-center')
    expect(wrapper.classes()).toContain('gap-1')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['View', 'Delete'])
  })

  it('renders admin metric card values with tone classes', () => {
    const wrapper = mount(AdminMetricCard, {
      props: {
        label: 'Open',
        value: 12,
        tone: 'warning',
      },
    })

    expect(wrapper.text()).toContain('Open')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.classes()).toContain('admin-metric-card--warning')
  })

  it('renders admin metric card icon and footer slots', () => {
    const wrapper = mount(AdminMetricCard, {
      props: {
        label: 'Users',
        value: 42,
      },
      slots: {
        icon: '<span data-testid="icon">I</span>',
        footer: '<a href="/admin/users">View detail</a>',
      },
    })

    expect(wrapper.get('[data-testid="icon"]').text()).toBe('I')
    expect(wrapper.get('.admin-metric-card__footer').text()).toContain('View detail')
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

  it('renders generic admin status badges and paginated table sections', async () => {
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
        interactiveRows: true,
        page: 0,
        totalPages: 2,
        summary: 'Total 1',
        footerLoading: true,
        loadingText: 'Refreshing rows',
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
    expect(table.text()).toContain('Refreshing rows')
    expect(table.get('[data-test="description"]').text()).toBe('Rows can be opened.')

    await table.get('tbody tr').trigger('click')

    expect(table.emitted('rowClick')).toEqual([[{ id: 1, name: 'Ada' }]])
    expect(table.emitted('rowDblclick')).toBeUndefined()
  })

  it('can render the admin table wrapper without a pagination footer', () => {
    const table = mount(AdminPaginatedTable, {
      props: {
        columns: [{ key: 'name', label: 'Name' }],
        items: [{ id: 1, name: 'Ada' }],
        showFooter: false,
      },
    })

    expect(table.text()).toContain('Ada')
    expect(table.find('[data-test="pagination"]').exists()).toBe(false)
  })

  it('renders compact detail sections with actions and full-width description items', () => {
    const DetailSectionFixture = defineComponent({
      components: {
        DescriptionGrid,
        DescriptionItem,
        DetailSection,
      },
      template: `
        <DetailSection title="Error info" compact>
          <template #actions>
            <button>Copy</button>
          </template>
            <DescriptionGrid gap-class="gap-2">
              <DescriptionItem label="Message" full>Something failed</DescriptionItem>
            </DescriptionGrid>
        </DetailSection>
      `,
    })
    const wrapper = mount(DetailSectionFixture)

    expect(wrapper.text()).toContain('Error info')
    expect(wrapper.text()).toContain('Copy')
    expect(wrapper.text()).toContain('Something failed')
    expect(wrapper.get('section > div').classes()).toContain('border-b')
    expect(wrapper.get('dl').classes()).toContain('gap-2')
    expect(wrapper.get('dl > div').classes()).toContain('sm:col-span-2')
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
