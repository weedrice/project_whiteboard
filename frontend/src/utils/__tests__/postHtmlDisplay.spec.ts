import { describe, expect, it } from 'vitest'
import { preparePostHtmlDisplay } from '../postHtmlDisplay'

const declaration = '<template data-noviis-layer="viewport"><style>.x{color:red}</style><span class="x">effect</span></template>'

describe('preparePostHtmlDisplay', () => {
  it('removes a declaration-only document without mutating its source', () => {
    const source = declaration
    expect(preparePostHtmlDisplay(source)).toEqual({ html: '', hasViewport: true, empty: true })
    expect(source).toBe(declaration)
  })

  it('retains normal content around the declaration', () => {
    const result = preparePostHtmlDisplay(`<p>Before</p>${declaration}<p>After</p>`)
    expect(result.empty).toBe(false)
    expect(result.html).toContain('<p>Before</p>')
    expect(result.html).toContain('<p>After</p>')
    expect(result.html).not.toContain('data-noviis-layer')
    expect(result.html).not.toContain('.x{color:red}')
  })

  it('handles full documents and uppercase attribute names', () => {
    const source = `<!doctype html><html lang="ko"><head><title>Document</title></head><body dir="ltr" title="Body" data-page="post">${declaration.replace('data-noviis-layer', 'DATA-NOVIIS-LAYER')}</body></html>`
    expect(preparePostHtmlDisplay(source).empty).toBe(true)
  })

  it.each([
    `<style>.box{height:30px;background:red}</style><div class="box"></div>${declaration}`,
    `<style>body::before{content:'Body'}</style>${declaration}`,
    `<html><body style="background:red">${declaration}</body></html>`,
  ])('retains layout or CSS that can paint without text', (source) => {
    expect(preparePostHtmlDisplay(source).empty).toBe(false)
  })

  it.each([
    '<style>.x{color:red}</style><div class="x"></div>',
    '<!-- keep this --><p title="data-noviis-layer">normal</p>',
    '<pre>&lt;template data-noviis-layer="viewport"&gt;</pre>',
    '<template data-noviis-layer="other"><p>Other template</p></template>',
  ])('leaves unrelated HTML byte-for-byte unchanged', (source) => {
    expect(preparePostHtmlDisplay(source)).toEqual({ html: source, hasViewport: false, empty: false })
  })
})
