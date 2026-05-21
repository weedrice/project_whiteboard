import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Footer from '../Footer.vue'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

const toggleThemePreference = vi.hoisted(() => vi.fn())

vi.mock('@/composables/useThemePreference', () => ({
    useThemePreference: () => ({
        toggleTheme: toggleThemePreference
    })
}))

const router = createRouter({
    history: createMemoryHistory(),
    routes: [
        { path: '/', component: { template: '<div>Home</div>' } },
        { path: '/inquiry', component: { template: '<div>Inquiry</div>' } },
        { path: '/terms', component: { template: '<div>Terms</div>' } },
        { path: '/privacy', component: { template: '<div>Privacy</div>' } }
    ]
})

describe('Footer', () => {
    beforeEach(async () => {
        setActivePinia(createPinia())
        toggleThemePreference.mockClear()
        await router.push('/')
        await router.isReady()
    })

    it('renders current year and app name', async () => {
        const wrapper = mount(Footer, {
            global: {
                plugins: [router],
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
                plugins: [router],
                mocks: {
                    $t: (msg: string) => msg
                }
            }
        })

        const button = wrapper.find('button')
        await button.trigger('click')

        expect(toggleThemePreference).toHaveBeenCalled()
    })
})
