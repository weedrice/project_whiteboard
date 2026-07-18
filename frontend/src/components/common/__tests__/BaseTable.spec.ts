import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseTable from '../ui/BaseTable.vue'
import i18n from '@/i18n'

const loggerMock = vi.hoisted(() => ({
    warn: vi.fn(),
}))

const commonSortOrder = () => String(i18n.global.t('common.sortOrder'))

vi.mock('@/utils/logger', () => ({
    default: loggerMock,
}))

describe('BaseTable', () => {
    beforeEach(() => {
        loggerMock.warn.mockClear()
    })

    it('renders sortable header indicator before the label', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [
                    {
                        key: 'title',
                        label: 'Title',
                        sortable: true
                    }
                ],
                items: [],
                currentSortKey: 'title',
                currentSortDirection: 'asc'
            }
        })

        const spans = wrapper.find('.nv-base-table-header-button').findAll('span')

        expect(spans).toHaveLength(2)
        expect(spans[0].text()).toBe('^')
        expect(spans[1].text()).toBe('Title')
        expect(wrapper.get('.nv-base-table-header-button').attributes('aria-label')).toBe(`${commonSortOrder()} Title: ascending`)
    })

    it('does not show a visual placeholder for inactive sortable headers', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [
                    {
                        key: 'title',
                        label: 'Title',
                        sortable: true
                    }
                ],
                items: [],
                currentSortKey: 'createdAt',
                currentSortDirection: 'desc'
            }
        })

        const spans = wrapper.find('.nv-base-table-header-button').findAll('span')

        expect(wrapper.get('th').attributes('aria-sort')).toBe('none')
        expect(wrapper.get('.nv-base-table-header-button').attributes('aria-label')).toBe(`${commonSortOrder()} Title`)
        expect(spans[0].text()).toBe('')
        expect(spans[1].text()).toBe('Title')
    })

    it('announces the default loading state to assistive technologies', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [],
                loading: true,
            },
        })

        const status = wrapper.get('[role="status"]')

        expect(status.attributes('aria-live')).toBe('polite')
        expect(status.text()).toContain('로딩 중...')
        expect(status.get('.nv-base-table-spinner').attributes('aria-hidden')).toBe('true')
    })

    it('keeps default table chrome unless compact options are provided', () => {
        const defaultWrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Default row' }],
                rowKey: (item: object) => (item as { title: string }).title,
            },
        })

        expect(defaultWrapper.get('.nv-base-table').classes()).toContain('shadow')
        expect(defaultWrapper.get('th').classes()).toEqual(expect.arrayContaining(['px-3', 'sm:px-6']))
        expect(defaultWrapper.get('td').classes()).toEqual(expect.arrayContaining(['px-3', 'sm:px-6']))

        const compactWrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Compact row' }],
                rowKey: (item: object) => (item as { title: string }).title,
                density: 'compact',
                shadow: false,
                maxHeightClass: 'max-h-[420px]',
            },
        })

        expect(compactWrapper.get('.nv-base-table').classes()).not.toContain('shadow')
        expect(compactWrapper.get('.overflow-x-auto').classes()).toEqual(expect.arrayContaining(['max-h-[420px]', 'overflow-y-auto']))
        expect(compactWrapper.get('th').classes()).toEqual(expect.arrayContaining(['px-2', 'py-2']))
        expect(compactWrapper.get('td').classes()).toEqual(expect.arrayContaining(['px-2', 'py-1.5']))
    })

    it('keeps table chrome on nv token classes', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Token row' }],
            },
        })

        expect(wrapper.get('.nv-base-table').classes()).toContain('nv-elevated-surface')
        expect(wrapper.find('.nv-base-table-head').exists()).toBe(true)
        expect(wrapper.find('.nv-base-table-row').exists()).toBe(true)
        expect(wrapper.find('.nv-base-table-cell').exists()).toBe(true)
    })

    it('supports keyboard activation when rows opt into interactive behavior', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Accessible row' }],
                interactiveRows: true,
                rowActionLabel: (item: object) => `${(item as { title: string }).title} open`,
            },
        })

        const row = wrapper.get('tbody tr')

        expect(row.attributes('role')).toBeUndefined()
        expect(row.attributes('tabindex')).toBe('0')
        expect(row.attributes('aria-label')).toBe('Accessible row open')
        expect(row.attributes('aria-keyshortcuts')).toBe('Enter Space')

        await row.trigger('keydown', { key: 'Enter' })
        await row.trigger('keydown', { key: ' ' })

        expect(wrapper.emitted('row-click')).toHaveLength(2)
        expect(wrapper.emitted('row-click')?.[0]).toEqual([{ id: 1, title: 'Accessible row' }])
    })

    it('does not emit row activation events when rows are not interactive', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Static row' }],
            },
        })

        const row = wrapper.get('tbody tr')
        await row.trigger('click')
        await row.trigger('dblclick')
        await row.trigger('keydown', { key: 'Enter' })

        expect(row.attributes('role')).toBeUndefined()
        expect(row.attributes('tabindex')).toBeUndefined()
        expect(wrapper.emitted('row-click')).toBeUndefined()
        expect(wrapper.emitted('row-dblclick')).toBeUndefined()
    })

    it('only emits the configured pointer activation event for interactive rows', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Detail row' }],
                interactiveRows: true,
                rowActivationEvent: 'row-dblclick',
            },
        })

        const row = wrapper.get('tbody tr')
        await row.trigger('click')
        await row.trigger('dblclick')

        expect(wrapper.emitted('row-click')).toBeUndefined()
        expect(wrapper.emitted('row-dblclick')).toHaveLength(1)
    })

    it('routes keyboard activation to double click event when configured', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Detail row' }],
                interactiveRows: true,
                rowActionLabel: 'Open detail',
                rowActivationEvent: 'row-dblclick',
            },
        })

        await wrapper.get('tbody tr').trigger('keydown', { key: 'Enter' })

        expect(wrapper.emitted('row-dblclick')).toHaveLength(1)
        expect(wrapper.emitted('row-click')).toBeUndefined()
    })

    it('uses stable fallback row keys before falling back to index', async () => {
        const StatefulCell = defineComponent({
            props: {
                name: {
                    type: String,
                    required: true,
                },
            },
            setup(props) {
                const initialName = ref(props.name)
                return () => h('span', `${initialName.value}:${props.name}`)
            },
        })
        const wrapper = mount(BaseTable, {
            props: {
                columns: [
                    { key: 'name', label: 'Name' }
                ],
                items: [
                    { key: 'site.name', name: 'Site name' },
                    { userId: 11, name: 'User' },
                    { reportId: 22, name: 'Report' },
                    { ipAddress: '127.0.0.1', name: 'IP block' },
                    { adminId: 33, name: 'Admin' },
                    { errorLogId: 44, name: 'Error log' },
                ],
            },
            slots: {
                'cell-name': ({ item }: { item: object }) => h(StatefulCell, { name: (item as { name: string }).name }),
            },
        })

        await wrapper.setProps({
            items: [
                { errorLogId: 44, name: 'Error log' },
                { adminId: 33, name: 'Admin' },
                { ipAddress: '127.0.0.1', name: 'IP block' },
                { reportId: 22, name: 'Report' },
                { userId: 11, name: 'User' },
                { key: 'site.name', name: 'Site name' },
            ],
        })

        expect(wrapper.findAll('tbody tr').map((row) => row.text())).toEqual([
            'Error log:Error log',
            'Admin:Admin',
            'IP block:IP block',
            'Report:Report',
            'User:User',
            'Site name:Site name',
        ])
    })

    it('logs a development warning before falling back to index row keys', () => {
        const row = { title: 'Missing stable key' }

        mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [row],
            },
        })

        expect(loggerMock.warn).toHaveBeenCalledWith(
            '[BaseTable] Falling back to index row key. Provide rowKey for stable list rendering.',
            row,
        )
    })

    it('exposes selected state for interactive rows when configured', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ id: 1, title: 'Selected' }, { id: 2, title: 'Available' }],
                interactiveRows: true,
                rowSelected: (item: object) => (item as { id: number }).id === 1,
            },
        })

        expect(wrapper.findAll('tbody tr').map((row) => row.attributes('aria-selected'))).toEqual(['true', 'false'])
    })

    it('labels the table region without adding a redundant tab stop for a caption alone', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Row' }],
                caption: 'Search results',
            },
        })

        expect(wrapper.get('caption').text()).toBe('Search results')
        expect(wrapper.get('caption').classes()).toContain('sr-only')
        expect(wrapper.get('[role="region"]').attributes()).toMatchObject({
            'aria-label': 'Search results',
        })
        expect(wrapper.get('[role="region"]').attributes('tabindex')).toBeUndefined()
    })

    it('makes an explicitly labelled horizontal scroll region keyboard focusable', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Row' }],
                scrollLabel: 'Scrollable search results',
            },
        })

        const region = wrapper.get('[role="region"]')
        Object.defineProperties(region.element, {
            scrollWidth: { configurable: true, value: 600 },
            clientWidth: { configurable: true, value: 300 },
        })
        window.dispatchEvent(new Event('resize'))
        await nextTick()

        expect(region.attributes()).toMatchObject({
            'aria-label': 'Scrollable search results',
            tabindex: '0',
        })
    })

    it('makes a caption-labelled region focusable when its table overflows', async () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Row' }],
                caption: 'Search results',
            },
        })
        const region = wrapper.get('[role="region"]')
        Object.defineProperties(region.element, {
            scrollWidth: { configurable: true, value: 600 },
            clientWidth: { configurable: true, value: 300 },
        })

        window.dispatchEvent(new Event('resize'))
        await nextTick()

        expect(region.attributes('tabindex')).toBe('0')
    })

    it('announces an empty result through the shared status contract', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [],
                emptyText: 'Nothing found',
            },
        })

        const status = wrapper.get('[role="status"]')
        expect(status.attributes('aria-live')).toBe('polite')
        expect(status.text()).toBe('Nothing found')
    })

    it('applies responsive visibility classes and a horizontal overflow width contract', () => {
        const wrapper = mount(BaseTable, {
            props: {
                columns: [
                    { key: 'name', label: 'Name' },
                    { key: 'email', label: 'Email', hideBelow: 'sm' },
                    { key: 'createdAt', label: 'Created', hideBelow: 'lg' },
                ],
                items: [{ name: 'Noviis', email: 'n@example.com', createdAt: 'today' }],
            },
        })

        expect(wrapper.get('table').classes()).toEqual(expect.arrayContaining(['min-w-full', 'sm:min-w-[48rem]']))
        expect(wrapper.findAll('th')[1].classes()).toEqual(expect.arrayContaining(['hidden', 'sm:table-cell']))
        expect(wrapper.findAll('td')[2].classes()).toEqual(expect.arrayContaining(['hidden', 'lg:table-cell']))
    })
})
