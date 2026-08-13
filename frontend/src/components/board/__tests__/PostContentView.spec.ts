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

    it('renders encoded static html inside a sandboxed iframe without author scripts', () => {
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
        expect(frame.attributes('srcdoc')).toContain('<button>여권</button>')
        expect(frame.attributes('srcdoc')).not.toContain('onclick="toggle()"')
        expect(frame.attributes('srcdoc')).not.toContain('function toggle(){}')
    })

    it('forwards preview sizing and eager loading to the sandboxed frame', () => {
        const wrapper = mount(PostContentView, {
            props: {
                content: encodeSandboxedPostHtml('<style>body{min-height:600px}</style><p>Preview</p>'),
                sandboxMinHeight: 420,
                sandboxLoading: 'eager',
            },
        })

        const frame = wrapper.get('iframe')
        expect(frame.attributes('style')).toContain('height: 420px')
        expect(frame.attributes('loading')).toBe('eager')
    })

    it('does not discard normal content surrounding a preserved marker', () => {
        const marker = encodeSandboxedPostHtml('<style>.card{display:grid}</style><p>Widget</p>')
        const wrapper = mount(PostContentView, {
            props: {
                content: `<p>Before remains editable</p>${marker}<p>Tail remains visible</p>`,
            },
            attrs: { class: 'nv-rich-content' },
        })

        const source = wrapper.get('iframe').attributes('srcdoc')
        expect(source).toContain('<p>Widget</p>')
        expect(source).not.toContain('Before remains editable')
        expect(source).not.toContain('Tail remains visible')
        expect(wrapper.findAll('p').map((paragraph) => paragraph.text()))
            .toEqual(['Before remains editable', 'Tail remains visible'])
    })

    it('renders text after a preserved full document outside its iframe', () => {
        const document = '<!doctype html><html><body><main>Document body</main></body></html>'
        const wrapper = mount(PostContentView, {
            props: {
                content: encodeSandboxedPostHtml(`${document}\n\n블록 밖 내용`),
            },
        })

        expect(wrapper.get('iframe').attributes('srcdoc')).toContain('Document body')
        expect(wrapper.get('iframe').attributes('srcdoc')).not.toContain('블록 밖 내용')
        expect(wrapper.text()).toContain('블록 밖 내용')
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
