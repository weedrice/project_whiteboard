import { describe, expect, it, vi } from 'vitest'
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

    it('keeps plain code content usable when the lazy highlighter fails to load', async () => {
        const loadHighlighter = vi.fn().mockRejectedValue(new Error('chunk unavailable'))
        const wrapper = mount(PostContentView, {
            props: {
                content: '<pre><code>const value = 1</code></pre>',
                codeBlockHighlighterLoader: loadHighlighter,
            },
        })

        await vi.waitFor(() => expect(loadHighlighter).toHaveBeenCalledOnce())
        expect(wrapper.get('pre code').text()).toBe('const value = 1')
    })
})
