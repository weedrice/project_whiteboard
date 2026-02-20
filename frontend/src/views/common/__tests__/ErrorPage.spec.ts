import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ErrorPage from '../ErrorPage.vue'

const mocks = vi.hoisted(() => {
    const route: { query: Record<string, unknown> } = {
        query: {},
    }
    const router = {
        push: vi.fn(),
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
        router,
        t: (key: string) => messages[key] ?? key,
    }
})

vi.mock('vue-router', () => ({
    useRoute: () => mocks.route,
    useRouter: () => mocks.router,
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: mocks.t,
    }),
}))

describe('ErrorPage', () => {
    const mountPage = () => mount(ErrorPage, {
        global: {
            stubs: {
                BaseButton: {
                    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
                },
            },
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

    it('navigates to home when go-home button is clicked', async () => {
        const wrapper = mountPage()

        await wrapper.find('button').trigger('click')

        expect(mocks.router.push).toHaveBeenCalledWith('/')
    })
})
