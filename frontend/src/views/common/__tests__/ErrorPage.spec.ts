import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ErrorPage from '../ErrorPage.vue'

const mocks = vi.hoisted(() => {
    const route: { query: Record<string, unknown> } = {
        query: {},
    }

    const messages: Record<string, string> = {
        'common.error.defaultMessage': 'Default error message',
        'common.error.notFound': 'Not Found',
        'common.error.forbidden': 'Forbidden',
        'common.error.serverError': 'Server Error',
        'common.error.unknown': 'Unknown Error',
        'common.error.goHome': 'Go Home',
    }

    return {
        route,
        t: (key: string) => messages[key] ?? key,
    }
})

vi.mock('vue-router', () => ({
    useRoute: () => mocks.route,
    RouterLink: {
        props: ['to'],
        template: '<a :href="to"><slot /></a>',
    },
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: mocks.t,
    }),
}))

describe('ErrorPage', () => {
    const mountPage = () => mount(ErrorPage, {
        global: {
            mocks: {
                $t: mocks.t,
            },
        },
    })

    beforeEach(() => {
        vi.clearAllMocks()
        mocks.route.query = {}
    })

    it('renders default status, title and message when query is missing', () => {
        const wrapper = mountPage()

        expect(wrapper.text()).toContain('Error')
        expect(wrapper.text()).toContain('Unknown Error')
        expect(wrapper.text()).toContain('Default error message')
    })

    it('renders status-based titles for 404, 403 and 500', () => {
        const cases = [
            { status: '404', title: 'Not Found' },
            { status: '403', title: 'Forbidden' },
            { status: '500', title: 'Server Error' },
        ]

        for (const testCase of cases) {
            mocks.route.query = { status: testCase.status, message: 'custom' }
            const wrapper = mountPage()

            expect(wrapper.text()).toContain(testCase.status)
            expect(wrapper.text()).toContain(testCase.title)
            expect(wrapper.text()).toContain('custom')
        }
    })

    it('uses the first query value when status and message are arrays', () => {
        mocks.route.query = {
            status: ['404', '500'],
            message: ['first message', 'second message'],
        }

        const wrapper = mountPage()

        expect(wrapper.text()).toContain('404')
        expect(wrapper.text()).toContain('Not Found')
        expect(wrapper.text()).toContain('first message')
        expect(wrapper.text()).not.toContain('second message')
    })

    it('navigates to home when go-home button is clicked', async () => {
        const wrapper = mountPage()

        const homeLink = wrapper.get('a')

        expect(homeLink.attributes('href')).toBe('/')
        expect(homeLink.text()).toBe('Go Home')
    })
})
