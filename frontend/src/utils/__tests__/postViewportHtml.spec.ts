import { describe, expect, it } from 'vitest'
import { buildViewportHtmlSource, getViewportImageElements, MAX_VIEWPORT_ELEMENTS, MAX_VIEWPORT_HTML_LENGTH, MAX_VIEWPORT_IMAGE_DATA_LENGTH } from '../postViewportHtml'

function buildDocument(html: string, imageSources: ReadonlyMap<string, string> = new Map()): Document {
    const result = buildViewportHtmlSource(html, imageSources)
    expect(result).not.toBeNull()
    return new DOMParser().parseFromString(result!, 'text/html')
}

describe('buildViewportHtmlSource', () => {
    it.each([
        'background:url(data:image/svg+xml,svg)',
        String.raw`background:u\72 l("data:image/png;base64,YWJj")`,
        'background:u/**/rl(data:image/png;base64,YWJj)',
        String.raw`background:\75\72\6c(data:image/png;base64,YWJj)`,
        'background:image-set("data:image/png;base64,YWJj" 1x)',
        'background:image("data:image/png;base64,YWJj")',
        'background:src("data:image/png;base64,YWJj")',
        'background:-webkit-image-set("image.png" 1x)',
        '@import "https://evil.test/a.css";',
        String.raw`@\69mport "data:text/css,div{}";`,
    ])('rejects resource-bearing CSS including escaped tokens: %s', (css) => {
        expect(buildViewportHtmlSource(`<style>${css}</style>`)).toBeNull()
        expect(buildViewportHtmlSource(`<div style='${css}'></div>`)).toBeNull()
    })

    it.each([
        '.x{--a:"/*";background-image:url("data:image/svg+xml,svg");--b:"*/"}',
        ".x{--a:'/*';background-image:url(data:image/svg+xml,svg);--b:'*/'}",
        String.raw`.x{--a:"\"/*";background:u\72 l(data:image/svg+xml,svg);--b:"*/"}`,
        '.x{--a:"/*\\\n";background:url(data:image/png;base64,YWJj);--b:"*/"}',
        '.x{--a:"broken\n;background:url(data:image/png;base64,YWJj)}',
        '.x{background:url(data:image/png;base64,YWJj)}/* unclosed comment',
        String.raw`.x{--a:"/*";background:\75\72\6c(data:image/svg+xml,svg);--b:"*/"}`,
    ])('does not let quoted comment markers or malformed strings hide a CSS resource: %s', (css) => {
        expect(buildViewportHtmlSource(`<style>${css}</style>`)).toBeNull()
        const element = document.createElement('div')
        element.setAttribute('style', css)
        expect(buildViewportHtmlSource(element.outerHTML)).toBeNull()
    })

    it.each([
        '.x::after{content:"/* url(example) */"}',
        String.raw`.x::after{content:"escaped \" quote and /* marker";color:red}`,
        '.x{color:red}/* " url(data:image/png;base64,YWJj) */.y{color:blue}',
        '.x{color:red}/* unclosed comment url(data:image/png;base64,YWJj)',
        '.x::after{content:"unclosed string /* url(example)',
    ])('preserves comment-like quoted content and ignores actual CSS comments: %s', (css) => {
        const doc = buildDocument(`<style>${css}</style>`)
        expect(doc.body.querySelector('style')?.textContent).toBe(css)
    })

    it('retains decorative layout, Unicode and CSS animations inside the sandbox document', () => {
        const html = '<style>@keyframes fall{to{transform:translateY(100vh)}}.petal{animation:fall 4s infinite}</style><div class="petal" id="flower" style="position:fixed;inset:0">꽃 🌸</div>'
        const doc = buildDocument(html)
        expect(doc.querySelector('#flower')?.textContent).toBe('꽃 🌸')
        expect(doc.querySelector('#flower')?.getAttribute('style')).toBe('position:fixed;inset:0')
        expect(doc.documentElement.textContent).toContain('@keyframes fall')
        expect(doc.body.hasAttribute('inert')).toBe(true)
        expect(doc.body.getAttribute('aria-hidden')).toBe('true')
    })

    it('places a nonrelaxable generated CSP before author styles and rejects author policy/navigation tags', () => {
        const doc = buildDocument('<html onload="run()"><head><meta http-equiv="Content-Security-Policy" content="default-src *"><meta http-equiv="refresh" content="0;url=https://evil.test"><base href="https://evil.test"><link rel="stylesheet" href="https://evil.test/a.css"><style>.x{color:red}</style></head><body style="background:red" onload="run()">test</body></html>')
        const policies = doc.querySelectorAll('meta[http-equiv]')
        expect(policies).toHaveLength(1)
        expect(policies[0]?.getAttribute('content')).toContain("script-src 'none'")
        expect(policies[0]?.getAttribute('content')).toContain("style-src 'unsafe-inline'")
        expect(policies[0]?.getAttribute('content')).toContain('img-src data:')
        expect(policies[0]?.getAttribute('content')).toContain("frame-src 'none'")
        expect(doc.head.children[1]).toBe(policies[0])
        expect(doc.querySelector('base,link,[onload]')).toBeNull()
        expect(doc.body.hasAttribute('style')).toBe(false)
    })

    it('removes executable, interactive, embedded and foreign-namespace subtrees', () => {
        const doc = buildDocument('<script>alert(1)</script><form><input autofocus><button>send</button></form><iframe srcdoc="<script>alert(1)</script>"></iframe><object data="x"></object><embed src="x"><svg><foreignObject><div>foreign</div></foreignObject></svg><math><mi>x</mi></math><template shadowrootmode="open"><script>alert(2)</script></template><video autoplay src="x"></video><audio src="x"></audio><a href="javascript:alert(1)" target="_top"><span>visible</span></a><div onclick="run()" tabindex="0" autofocus contenteditable="true" popover>decoration</div>')
        expect(doc.querySelector('script,form,input,button,iframe,object,embed,svg,math,template,video,audio,a')).toBeNull()
        expect(doc.querySelector('[onclick],[tabindex],[autofocus],[contenteditable],[popover]')).toBeNull()
        expect(doc.body.textContent).toBe('visibledecoration')
    })

    it('only retains host-resolved raster tokens and removes alternative fetch attributes', () => {
        const image = 'data:image/webp;base64,YWJjZA=='
        const doc = buildDocument('<img src="noviis-viewport-image:0" srcset="https://evil.test/a.png 2x" lowsrc="https://evil.test/b.png" onerror="run()" alt="꽃" width="120" height="80"><img src="https://cdn.noviis.kr/upload.png"><img src="/api/v1/files/1"><img src="data:image/png;base64,YWJjZA=="><img src="blob:https://evil.test/id"><img src="noviis-viewport-image:1">', new Map([['noviis-viewport-image:0', image]]))
        const images = doc.querySelectorAll('img')
        expect(images).toHaveLength(1)
        expect(images[0]?.getAttribute('src')).toBe(image)
        expect(images[0]?.getAttribute('alt')).toBe('꽃')
        expect(images[0]?.getAttribute('width')).toBe('120')
        expect(images[0]?.getAttribute('height')).toBe('80')
        expect(images[0]?.hasAttribute('srcset')).toBe(false)
        expect(images[0]?.hasAttribute('lowsrc')).toBe(false)
        expect(images[0]?.hasAttribute('onerror')).toBe(false)
    })

    it('rejects nonraster and non-base64 entries even from a supplied image map', () => {
        const doc = buildDocument('<img src="noviis-viewport-image:0"><img src="noviis-viewport-image:1"><img src="noviis-viewport-image:2">', new Map([
            ['noviis-viewport-image:0', 'data:image/svg+xml;base64,YWJjZA=='],
            ['noviis-viewport-image:1', 'data:image/png,hello'],
            ['noviis-viewport-image:2', 'https://cdn.noviis.kr/upload.png'],
        ]))
        expect(doc.querySelectorAll('img')).toHaveLength(0)
    })

    it('keeps host-resolved image bytes outside the author markup length budget', () => {
        const image = `data:image/png;base64,${'YWFh'.repeat(MAX_VIEWPORT_HTML_LENGTH)}`
        const doc = buildDocument('<img src="noviis-viewport-image:0">', new Map([['noviis-viewport-image:0', image]]))
        expect(doc.querySelector('img')?.getAttribute('src')).toBe(image)
    })

    it('rejects repeated image data before constructing the expanded output document', () => {
        const prefix = 'data:image/png;base64,'
        const image = prefix + 'A'.repeat(Math.floor(MAX_VIEWPORT_IMAGE_DATA_LENGTH / 2) + 1 - prefix.length)
        const sources = new Map([['noviis-viewport-image:0', image]])
        expect(buildViewportHtmlSource('<img src="noviis-viewport-image:0">'.repeat(2), sources)).toBeNull()
    })

    it('rejects a single oversized mapped image but ignores discarded image subtrees', () => {
        const image = `data:image/png;base64,${'A'.repeat(MAX_VIEWPORT_IMAGE_DATA_LENGTH)}`
        const sources = new Map([['noviis-viewport-image:0', image]])
        expect(buildViewportHtmlSource('<img src="noviis-viewport-image:0">', sources)).toBeNull()
        const html = '<form><img src="noviis-viewport-image:0"></form><svg><foreignObject><img src="noviis-viewport-image:0"></foreignObject></svg><template><img src="noviis-viewport-image:0"></template><p>kept</p>'
        const doc = buildDocument(html, sources)
        expect(doc.body.textContent).toBe('kept')
        expect(doc.querySelector('img')).toBeNull()
    })

    it('shares only rendered image candidates with hydration in document order', () => {
        const template = document.createElement('template')
        template.innerHTML = '<div><img id="first"><a><img id="second"></a></div><form><img id="form"></form><svg><foreignObject><img id="foreign"></foreignObject></svg><template><img id="template"></template><custom-wrapper><img id="third"></custom-wrapper>'
        expect(getViewportImageElements(template.content).map(image => image.id)).toEqual(['first', 'second', 'third'])
    })

    it('does not turn encoded markup or namespace mutation payloads into executable HTML', () => {
        const doc = buildDocument('<p>&lt;img src=x onerror=alert(1)&gt;</p><math><mtext><table><mglyph><style><!--</style><img title="--><img src=x onerror=alert(1)>"></table></mtext></math><style></style><script>alert(1)</script>')
        expect(doc.querySelector('script,[onerror],math,svg')).toBeNull()
        expect(doc.querySelector('p')?.textContent).toBe('<img src=x onerror=alert(1)>')
        expect(doc.querySelectorAll('meta[http-equiv="Content-Security-Policy"]')).toHaveLength(1)
    })

    it('rejects excessive source length before parsing', () => {
        expect(buildViewportHtmlSource('x'.repeat(MAX_VIEWPORT_HTML_LENGTH))).not.toBeNull()
        expect(buildViewportHtmlSource('x'.repeat(MAX_VIEWPORT_HTML_LENGTH + 1))).toBeNull()
    })

    it('rejects too many author elements including hidden template descendants', () => {
        expect(buildViewportHtmlSource('<i></i>'.repeat(MAX_VIEWPORT_ELEMENTS))).not.toBeNull()
        expect(buildViewportHtmlSource('<i></i>'.repeat(MAX_VIEWPORT_ELEMENTS + 1))).toBeNull()
        expect(buildViewportHtmlSource(`<template>${'<i></i>'.repeat(MAX_VIEWPORT_ELEMENTS)}</template>`)).toBeNull()
    })
})
