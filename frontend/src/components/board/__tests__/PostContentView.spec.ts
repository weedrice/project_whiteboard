import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PostContentView from '../PostContentView.vue'
import { encodeSandboxedPostHtml } from '@/utils/postHtmlSandbox'

describe('PostContentView', () => {
    it('renders normal editor html through the sanitized rich content path', () => {
        const wrapper = mount(PostContentView, {
            props: {
                content: '<p>Hello</p><a href="javascript:alert(1)">bad</a>',
            },
            attrs: {
                class: 'nv-rich-content',
            },
        })

        expect(wrapper.find('.nv-rich-content p').text()).toBe('Hello')
        expect(wrapper.html()).not.toContain('javascript:')
        expect(wrapper.find('iframe').exists()).toBe(false)
    })

    it('renders encoded widget html inside a sandboxed iframe', () => {
        const rawWidget = '<style>.cl{display:grid}</style><button onclick="toggle()">여권</button><script>function toggle(){}</script>'
        const wrapper = mount(PostContentView, {
            props: {
                content: encodeSandboxedPostHtml(rawWidget),
                sandboxTitle: 'Checklist',
            },
        })

        const frame = wrapper.get('iframe')
        expect(frame.attributes('sandbox')).toBe('allow-scripts')
        expect(frame.attributes('title')).toBe('Checklist')
        expect(frame.attributes('srcdoc')).toContain('<style>.cl{display:grid}</style>')
        expect(frame.attributes('srcdoc')).toContain('onclick="toggle()"')
        expect(frame.attributes('srcdoc')).toContain('function toggle(){}')
    })
})
