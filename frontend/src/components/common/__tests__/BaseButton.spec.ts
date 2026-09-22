import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from '../ui/BaseButton.vue'

type BaseButtonProps = InstanceType<typeof BaseButton>['$props']

describe('BaseButton', () => {
    it('renders slot content', () => {
        const wrapper = mount(BaseButton, {
            slots: {
                default: 'Click Me'
            }
        })
        expect(wrapper.text()).toBe('Click Me')
    })

    it('emits click event', async () => {
        const wrapper = mount(BaseButton)
        await wrapper.trigger('click')
        expect(wrapper.emitted('click')).toBeTruthy()
    })

    it('applies variant class', () => {
        const wrapper = mount(BaseButton, {
            props: {
                variant: 'danger'
            }
        })
        expect(wrapper.classes()).toContain('btn-danger')
    })

    it('disables button when disabled prop is true', () => {
        const wrapper = mount(BaseButton, {
            props: {
                disabled: true
            }
        })
        expect(wrapper.attributes('disabled')).toBeDefined()
        expect(wrapper.classes()).toContain('opacity-50')
    })

    it('disables and marks the button busy while loading', async () => {
        const wrapper = mount(BaseButton, {
            props: {
                loading: true,
            },
            slots: {
                default: 'Saving',
            },
        })

        expect(wrapper.attributes('disabled')).toBeDefined()
        expect(wrapper.attributes('aria-busy')).toBe('true')
        expect(wrapper.text()).toContain('Saving')
        expect(wrapper.find('[role="status"]').exists()).toBe(true)

        await wrapper.trigger('click')

        expect(wrapper.emitted('click')).toBeUndefined()
    })

    it('applies large size class and full width class', () => {
        const wrapper = mount(BaseButton, {
            props: {
                size: 'lg',
                fullWidth: true,
            },
        })

        expect(wrapper.classes()).toContain('w-full')
        expect(wrapper.classes()).toContain('px-6')
        expect(wrapper.classes()).toContain('py-3')
        expect(wrapper.classes()).toContain('text-base')
    })

    it('delegates the small typography and touch target to the shared button class', () => {
        const wrapper = mount(BaseButton, {
            props: { size: 'sm' },
        })

        expect(wrapper.classes()).toContain('btn-sm')
        expect(wrapper.classes()).not.toContain('text-xs')
    })

    it('renders a router link with the same button styles when to is provided', () => {
        const wrapper = mount(BaseButton, {
            props: {
                to: '/boards',
                variant: 'secondary',
            },
            slots: {
                default: 'Boards',
            },
            global: {
                stubs: {
                    RouterLink: {
                        props: ['to'],
                        template: '<a :href="to"><slot /></a>',
                    },
                },
            },
        })

        expect(wrapper.get('a').attributes('href')).toBe('/boards')
        expect(wrapper.get('a').classes()).toContain('btn-secondary')
    })

    it('renders a hard link with shared styles when href is provided', () => {
        const wrapper = mount(BaseButton, {
            props: {
                href: '/recovery-home',
                variant: 'secondary',
            },
            slots: {
                default: 'Home',
            },
        })

        const link = wrapper.get('a')
        expect(link.attributes()).toMatchObject({ href: '/recovery-home' })
        expect(link.attributes('type')).toBeUndefined()
        expect(link.classes()).toContain('btn-secondary')
    })

    it('prevents disabled hard-link navigation', async () => {
        const wrapper = mount(BaseButton, {
            props: { href: '/', disabled: true },
        })
        const link = wrapper.get('a')

        expect(link.attributes()).toMatchObject({
            'aria-disabled': 'true',
            tabindex: '-1',
        })
        await link.trigger('click')
        expect(wrapper.emitted('click')).toBeUndefined()
    })

    it('falls back to primary variant for unknown variant values', () => {
        const wrapper = mount(BaseButton, {
            props: {
                variant: 'not-supported',
            } as unknown as BaseButtonProps,
        })

        expect(wrapper.classes()).toContain('btn-primary')
    })
})
