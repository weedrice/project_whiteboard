import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ToastMessage from '../ToastMessage.vue'

describe('ToastMessage', () => {
    it('uses localized close labels for assistive text', () => {
        const wrapper = mount(ToastMessage, {
            props: {
                toast: {
                    id: 1,
                    message: 'Saved',
                    type: 'success',
                    duration: 3000,
                    position: 'top-center'
                }
            },
            global: {
                mocks: {
                    $t: (key: string) => key
                }
            }
        })

        const closeButton = wrapper.get('button')

        expect(closeButton.attributes('aria-label')).toBe('common.close')
        expect(closeButton.text()).toContain('common.close')
    })

    it('uses alert semantics for errors and pauses while being interacted with', async () => {
        const wrapper = mount(ToastMessage, {
            props: {
                toast: {
                    id: 2,
                    message: 'Failed',
                    type: 'error',
                    duration: 3000,
                    position: 'top-center',
                },
            },
            global: { mocks: { $t: (key: string) => key } },
        })

        expect(wrapper.attributes('role')).toBe('alert')
        expect(wrapper.attributes('aria-live')).toBeUndefined()

        await wrapper.trigger('mouseenter')
        await wrapper.get('button').trigger('focusin')
        await wrapper.get('button').trigger('focusout', { relatedTarget: document.body })
        await wrapper.trigger('mouseleave')

        expect(wrapper.emitted('pause')).toHaveLength(1)
        expect(wrapper.emitted('resume')).toHaveLength(1)
    })
})
