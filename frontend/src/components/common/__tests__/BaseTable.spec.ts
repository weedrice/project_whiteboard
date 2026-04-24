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
})
