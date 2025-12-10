import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Footer from '../Footer.vue'
import { createPinia, setActivePinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'

describe('Footer', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it('renders current year and app name', () => {
        const wrapper = mount(Footer, {
            global: {
                mocks: {
                    $t: (msg: string) => {
                        if (msg === 'common.appName') return 'Test App'
                        if (msg === 'common.footer.rights') return 'All rights reserved.'
                        return msg
                    }
                }
            }
        })
        const currentYear = new Date().getFullYear()
        expect(wrapper.text()).toContain(currentYear.toString())
        expect(wrapper.text()).toContain('Test App')
        expect(wrapper.text()).toContain('All rights reserved.')
    })

    it('toggles theme on button click', async () => {
        const wrapper = mount(Footer, {
            global: {
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })
        const themeStore = useThemeStore()
        const toggleSpy = vi.spyOn(themeStore, 'toggleTheme')

        const button = wrapper.find('button')
        await button.trigger('click')

        expect(toggleSpy).toHaveBeenCalled()
    })
})
