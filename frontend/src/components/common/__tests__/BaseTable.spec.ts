import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseTable from '../ui/BaseTable.vue'

const loggerMock = vi.hoisted(() => ({
    warn: vi.fn(),
}))

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
        expect(wrapper.get('.nv-base-table-header-button').attributes('aria-label')).toBe('Sort by Title, currently ascending')
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
        expect(wrapper.get('.nv-base-table-header-button').attributes('aria-label')).toBe('Sort by Title')
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

        expect(wrapper.find('.nv-base-table').exists()).toBe(true)
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

        expect(row.attributes('role')).toBe('button')
        expect(row.attributes('tabindex')).toBe('0')
        expect(row.attributes('aria-label')).toBe('Accessible row open')

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
})
