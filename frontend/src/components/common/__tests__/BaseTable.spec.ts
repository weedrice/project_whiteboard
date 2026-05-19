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
