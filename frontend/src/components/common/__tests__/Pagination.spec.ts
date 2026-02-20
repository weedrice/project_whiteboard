import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Pagination from '../ui/Pagination.vue'

describe('Pagination', () => {
    it('renders correct number of pages', () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 0,
                totalPages: 5
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })
        // 1, 2, 3, 4, 5 -> 5 buttons + prev + next = 7 buttons
        // But implementation might use span for dots or specific logic.
        // Let's check text content of buttons.
        const buttons = wrapper.findAll('button')
        // prev, 1, 2, 3, 4, 5, next
        expect(buttons.length).toBe(7)
    })

    it('emits page-change event on click', async () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 0,
                totalPages: 5
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })
        const buttons = wrapper.findAll('button')
        // Click page 2 (index 2 in buttons array: prev, 1, 2...)
        await buttons[2].trigger('click')
        expect(wrapper.emitted('page-change')?.[0]).toEqual([1])
    })

    it('disables previous button on first page', () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 0,
                totalPages: 5
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })
        const prevButton = wrapper.findAll('button')[0]
        expect(prevButton.attributes('disabled')).toBeDefined()
    })

    it('disables next button on last page', () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 4,
                totalPages: 5
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })
        const buttons = wrapper.findAll('button')
        const nextButton = buttons[buttons.length - 1]
        expect(nextButton.attributes('disabled')).toBeDefined()
    })

    it('emits previous and next page changes when navigation buttons are enabled', async () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 2,
                totalPages: 5,
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg,
                },
            },
        })

        const buttons = wrapper.findAll('button')
        await buttons[0].trigger('click')
        await buttons[buttons.length - 1].trigger('click')

        expect(wrapper.emitted('page-change')?.[0]).toEqual([1])
        expect(wrapper.emitted('page-change')?.[1]).toEqual([3])
    })

    it('renders ellipsis for large page ranges and hides nav when totalPages is zero', () => {
        const wrapper = mount(Pagination, {
            props: {
                currentPage: 9,
                totalPages: 20,
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg,
                },
            },
        })

        expect(wrapper.text()).toContain('...')

        const hiddenWrapper = mount(Pagination, {
            props: {
                currentPage: 0,
                totalPages: 0,
            },
            global: {
                mocks: {
                    $t: (msg: string) => msg,
                },
            },
        })
        expect(hiddenWrapper.find('nav').exists()).toBe(false)
    })
})
