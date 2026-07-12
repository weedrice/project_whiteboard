import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserNavigation from '../UserNavigation.vue'
import type { UserNavigationTab } from '@/types/userNavigation'

const tabs: UserNavigationTab[] = [
    { nameKey: 'common.myPage', href: '/mypage' },
    { nameKey: 'common.notifications', href: '/mypage/notifications' },
    { nameKey: 'common.mailbox', href: '/mypage/messages' },
]

function mountNavigation(activePath = '/mypage') {
    return mount(UserNavigation, {
        props: {
            activePath,
            tabs,
        },
        global: {
            mocks: {
                $t: (key: string) => key,
            },
        },
    })
}

describe('UserNavigation', () => {
    beforeEach(() => {
        Element.prototype.scrollTo = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('marks the root mypage tab active only on the exact root path', async () => {
        const wrapper = mountNavigation('/mypage')

        expect(wrapper.findAll('a')[0].attributes('aria-current')).toBe('page')
        expect(wrapper.get('nav').attributes('role')).toBeUndefined()
        expect(wrapper.findAll('a')[1].attributes('tabindex')).toBeUndefined()
        expect(wrapper.findAll('a')[1].classes()).toContain('nv-focus-ring')

        await wrapper.setProps({ activePath: '/mypage/settings' })

        expect(wrapper.findAll('a').some(link => link.attributes('aria-current') === 'page')).toBe(false)
    })

    it('emits navigate for arrow and boundary keyboard navigation', async () => {
        const wrapper = mountNavigation('/mypage/notifications')
        const links = wrapper.findAll('a')

        await links[1].trigger('keydown', { key: 'ArrowRight' })
        await links[1].trigger('keydown', { key: 'ArrowLeft' })
        await links[1].trigger('keydown', { key: 'Home' })
        await links[1].trigger('keydown', { key: 'End' })

        expect(wrapper.emitted('navigate')).toEqual([
            ['/mypage/messages'],
            ['/mypage'],
            ['/mypage'],
            ['/mypage/messages'],
        ])
    })

    it('emits navigate on normal clicks while keeping hrefs available', async () => {
        const wrapper = mountNavigation('/mypage')
        const links = wrapper.findAll('a')

        await links[1].trigger('click', { button: 0 })

        expect(wrapper.emitted('navigate')).toEqual([
            ['/mypage/notifications'],
        ])
        expect(links[1].attributes('href')).toBe('/mypage/notifications')
    })

    it('emits navigate for document bracket shortcuts and ignores modifiers', async () => {
        const wrapper = mountNavigation('/mypage/notifications')

        document.dispatchEvent(new KeyboardEvent('keydown', { key: ']', bubbles: true }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: '[', bubbles: true }))
        document.dispatchEvent(new KeyboardEvent('keydown', { key: ']', ctrlKey: true, bubbles: true }))

        expect(wrapper.emitted('navigate')).toEqual([
            ['/mypage/messages'],
            ['/mypage'],
        ])
    })
})
