import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import ErrorBoundary from '../ErrorBoundary.vue'
import { reportVueError } from '@/utils/clientErrorReporter'

vi.mock('vue-router', () => ({
    useRouter: () => ({
        currentRoute: { value: { fullPath: '/current' } },
        replace: vi.fn(),
    }),
}))

vi.mock('vue-i18n', () => ({
    useI18n: () => ({
        t: (key: string) => ({
            'common.error.unknown': 'Something went wrong',
            'common.error.defaultMessage': 'Please try again later.',
            'common.error.retry': 'Retry',
            'common.error.goHome': 'Go home',
            'common.error.showDetails': 'Show details',
        }[key] ?? key),
    }),
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn(),
        warn: vi.fn(),
    },
}))

vi.mock('@/utils/clientErrorReporter', () => ({
    reportVueError: vi.fn(),
}))

const ThrowingChild = defineComponent({
    render() {
        const error = new Error('internal database token leaked')
        if (error) {
            throw error
        }
        return null
    }
})

describe('ErrorBoundary', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('does not expose raw internal error messages in the visible fallback copy', async () => {
        const wrapper = mount(ErrorBoundary, {
            slots: {
                default: () => h(ThrowingChild),
            },
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    BaseButton: {
                        template: '<button type="button"><slot /></button>',
                    },
                },
            },
        })
        await nextTick()

        expect(wrapper.text()).toContain('Something went wrong')
        expect(wrapper.text()).toContain('Please try again later.')
        expect(wrapper.text()).not.toContain('internal database token leaked')
        expect(reportVueError).toHaveBeenCalledWith(
            expect.objectContaining({
                name: 'Error',
                message: 'internal database token leaked',
            }),
            expect.anything(),
            expect.any(String),
        )
    })

    it('keeps raw details behind the explicit details option', async () => {
        const wrapper = mount(ErrorBoundary, {
            props: {
                showDetails: true,
            },
            slots: {
                default: () => h(ThrowingChild),
            },
            global: {
                mocks: {
                    $t: (key: string) => key,
                },
                stubs: {
                    BaseButton: {
                        template: '<button type="button"><slot /></button>',
                    },
                },
            },
        })
        await nextTick()

        expect(wrapper.find('details').exists()).toBe(true)
        expect(wrapper.find('pre').text()).toContain('internal database token leaked')
    })
})
