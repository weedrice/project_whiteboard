import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import RawHtmlBlockView from '../RawHtmlBlockView.vue'

describe('RawHtmlBlockView', () => {
  it('shows a compact notice for viewport-only HTML while retaining the node source', () => {
    const html = '<template data-noviis-layer="viewport"><style>.x{color:red}</style><span class="x">effect</span></template>'
    const updateAttributes = vi.fn()
    const wrapper = mount(RawHtmlBlockView, {
      props: {
        node: { attrs: { html } }, editor: {}, extension: {}, getPos: () => 0,
        decorations: [], innerDecorations: {}, view: {}, selected: false,
        HTMLAttributes: {}, updateAttributes, deleteNode: vi.fn(),
      } as never,
      global: {
        stubs: {
          NodeViewWrapper: defineComponent({
            setup(_, { attrs, slots }) { return () => h('div', attrs, slots.default?.()) },
          }),
        },
      },
    })
    expect(wrapper.find('iframe').exists()).toBe(false)
    expect(wrapper.get('.raw-html-block__notice').text()).toContain('전체 화면 장식')
    expect(wrapper.props('node').attrs.html).toBe(html)
    expect(updateAttributes).not.toHaveBeenCalled()
  })

  it('renders preserved html through the script-free sandbox preview', () => {
    const rawHtml = '<style>.card{display:grid}</style><button onclick="run()">카드</button><script>run()</script>'
    const i18n = createI18n({
      legacy: false,
      locale: 'ko',
      messages: {
        ko: {
          board: {
            writePost: {
              rawHtmlBlock: {
                title: '원본 HTML 블록',
                description: 'HTML 보기에서 수정',
                previewTitle: '원본 HTML 미리보기',
                selectionHint: '블록 선택 안내',
              },
            },
          },
        },
      },
    })
    const NodeViewWrapperStub = defineComponent({
      name: 'NodeViewWrapper',
      setup(_, { attrs, slots }) {
        return () => h('div', attrs, slots.default?.())
      },
    })

    const wrapper = mount(RawHtmlBlockView, {
      props: {
        node: { attrs: { html: rawHtml } },
        editor: {},
        extension: {},
        getPos: () => 0,
        decorations: [],
        innerDecorations: {},
        view: {},
        selected: false,
        HTMLAttributes: {},
        updateAttributes: vi.fn(),
        deleteNode: vi.fn(),
      } as never,
      global: {
        plugins: [i18n],
        stubs: {
          NodeViewWrapper: NodeViewWrapperStub,
        },
      },
    })

    const frame = wrapper.get('iframe')
    const block = wrapper.get('[data-testid="raw-html-block"]')
    const description = wrapper.get('.raw-html-block__header p')
    expect(block.attributes('role')).toBe('group')
    expect(block.attributes('aria-label')).toBe('원본 HTML 블록')
    expect(block.attributes('aria-describedby')).toBe(description.attributes('id'))
    expect(wrapper.get('.raw-html-block__header').attributes()).toHaveProperty('data-drag-handle')
    expect(frame.attributes('loading')).toBe('lazy')
    expect(frame.attributes('srcdoc')).toContain('<style>.card{display:grid}</style>')
    expect(frame.attributes('srcdoc')).toContain('<button>카드</button>')
    expect(frame.attributes('srcdoc')).not.toContain('onclick=')
    expect(frame.attributes('srcdoc')).not.toContain('run()</script>')
  })
})
