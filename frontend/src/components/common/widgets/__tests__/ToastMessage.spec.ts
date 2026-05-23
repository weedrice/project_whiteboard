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
})
