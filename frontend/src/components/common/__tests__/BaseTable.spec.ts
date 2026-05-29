import { defineComponent, h, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseTable from '../ui/BaseTable.vue'

describe('BaseTable', () => {
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
        expect(status.text()).toContain('Loading...')
        expect(status.get('.nv-base-table-spinner').attributes('aria-hidden')).toBe('true')
    })

    it('keeps default table chrome unless compact options are provided', () => {
        const defaultWrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Default row' }],
            },
        })

        expect(defaultWrapper.get('.nv-base-table').classes()).toContain('shadow')
        expect(defaultWrapper.get('th').classes()).toEqual(expect.arrayContaining(['px-3', 'sm:px-6']))
        expect(defaultWrapper.get('td').classes()).toEqual(expect.arrayContaining(['px-3', 'sm:px-6']))

        const compactWrapper = mount(BaseTable, {
            props: {
                columns: [{ key: 'title', label: 'Title' }],
                items: [{ title: 'Compact row' }],
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
})
