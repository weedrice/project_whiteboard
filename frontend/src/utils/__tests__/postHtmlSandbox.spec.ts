import { afterEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
    buildSandboxedPostHtmlSource,
    containsSandboxedPostHtml,
    decodeSandboxedPostHtml,
    encodeSandboxedPostHtml,
    expandSandboxedPostHtml,
    expandSandboxedPostHtmlForEditing,
    mapSandboxedPostHtmlPayloads,
    requiresPreservedPostHtml,
    requiresSandboxedPostHtml,
    restoreSandboxedPostHtmlAfterEditing,
} from '../postHtmlSandbox'

const heightBridgeSource = readFileSync(
    resolve(process.cwd(), 'public/sandbox-height-bridge.js'),
    'utf8',
)
const embeddingSecurityHeaders = [
    readFileSync(resolve(process.cwd(), 'nginx.conf'), 'utf8'),
    readFileSync(resolve(process.cwd(), '../deploy/nginx/security-headers.conf'), 'utf8'),
]

describe('postHtmlSandbox', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
    })

    it('detects html that needs sandbox execution', () => {
        expect(requiresSandboxedPostHtml('<p>Hello</p>')).toBe(false)
        expect(requiresSandboxedPostHtml('<style>.x{display:grid}</style><div></div>')).toBe(true)
        expect(requiresSandboxedPostHtml('<button onclick="run()">Run</button>')).toBe(true)
        expect(requiresSandboxedPostHtml('<script>alert(1)</script>')).toBe(true)
    })

    it('encodes and decodes sandbox html without losing unicode content', () => {
        const raw = '<style>.cl{display:grid}</style><button onclick="run()">여권</button><script>function run(){}</script>'
        const encoded = encodeSandboxedPostHtml(raw)

        expect(encoded).toContain('noviis-sandboxed-post-html')
        expect(encoded).toContain('data-value=')
        expect(encoded).not.toContain('<script>')
        expect(encoded).not.toContain('onclick=')
        expect(decodeSandboxedPostHtml(encoded)).toBe(raw)
    })

    it('leaves normal editor html unchanged', () => {
        expect(encodeSandboxedPostHtml('<p>Hello</p>')).toBe('<p>Hello</p>')
    })

    it('preserves unsupported editor tags, attributes, styles, and comments', () => {
        const lossyExamples = [
            '<section><p>Section</p></section>',
            '<div class="custom-card"><p>Card</p></div>',
            '<p id="intro">Intro</p>',
            '<p style="letter-spacing:2px">Spaced</p>',
            '<a href="https://noviis.kr" target="_self">Same tab</a>',
            '<ol type="A"><li>Alpha</li></ol>',
            '<custom-widget data-value="1"></custom-widget>',
            '<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="4"></circle></svg>',
            '<p>Before</p><!-- note --><p>After</p>',
        ]

        lossyExamples.forEach((html) => {
            expect(requiresPreservedPostHtml(html)).toBe(true)
            expect(decodeSandboxedPostHtml(encodeSandboxedPostHtml(html))).toBe(html)
        })
        expect(requiresPreservedPostHtml('<p style="text-align:center"><strong>Hello</strong></p>')).toBe(false)
        expect(requiresPreservedPostHtml('<a class="tiptap-link" href="https://noviis.kr" target="_blank" rel="noopener noreferrer">Link</a>')).toBe(false)
        expect(requiresPreservedPostHtml('<a class="tiptap-link" href="https://noviis.kr" target="_blank" rel="noreferrer nofollow noopener">Link</a>')).toBe(false)
        expect(requiresPreservedPostHtml('<pre class="hljs"><code class="language-typescript">const value = 1</code></pre>')).toBe(false)
        expect(requiresPreservedPostHtml('<span class="mention-node" data-type="mention" data-id="7" data-label="Novi" data-mention-suggestion-char="@" data-mention-user-id="7">@Novi</span>')).toBe(false)
        expect(requiresPreservedPostHtml('<iframe src="https://www.youtube-nocookie.com/embed/private-id"></iframe>')).toBe(false)
    })

    it('preserves legacy or inconsistent mentions before the visual editor can lose identity', () => {
        const legacyMentions = [
            '<span data-type="mention" data-mention-user-id="7">@Novi</span>',
            '<span class="mention-node" data-type="mention" data-id="7" data-label="Novi" data-mention-user-id="8" data-mention-suggestion-char="@">@Novi</span>',
            '<span class="mention-node" data-type="mention" data-id="7" data-label="Novi" data-mention-user-id="7" data-mention-suggestion-char="@">@Changed</span>',
        ]

        legacyMentions.forEach((html) => {
            expect(requiresPreservedPostHtml(html)).toBe(true)
            expect(decodeSandboxedPostHtml(encodeSandboxedPostHtml(html))).toBe(html)
        })
    })

    it('decodes only a standalone preserved marker', () => {
        const raw = '<style>.card{display:grid}</style><p>Widget</p>'
        const marker = encodeSandboxedPostHtml(raw)
        const mixed = `${marker}<p>Tail</p>`

        expect(containsSandboxedPostHtml(mixed)).toBe(true)
        expect(decodeSandboxedPostHtml(marker)).toBe(raw)
        expect(decodeSandboxedPostHtml(mixed)).toBeNull()
        expect(expandSandboxedPostHtml(mixed)).toBe(`${raw}<p>Tail</p>`)
    })

    it('round-trips mixed preserved blocks through editable source boundaries', () => {
        const firstRaw = '<style>.first{display:grid}</style><section>첫 번째</section>'
        const secondRaw = '<custom-widget data-value="2"></custom-widget>'
        const mixed = `<p>앞</p>${encodeSandboxedPostHtml(firstRaw)}<p>중간</p>${encodeSandboxedPostHtml(secondRaw)}<p>뒤</p>`

        const editable = expandSandboxedPostHtmlForEditing(mixed)
        expect(editable).toContain(`<!--noviis-preserved-html-block:start-->${firstRaw}<!--noviis-preserved-html-block:end-->`)
        expect(editable).toContain(`<!--noviis-preserved-html-block:start-->${secondRaw}<!--noviis-preserved-html-block:end-->`)

        const restored = restoreSandboxedPostHtmlAfterEditing(editable ?? '')
        expect(expandSandboxedPostHtml(restored)).toBe(`<p>앞</p>${firstRaw}<p>중간</p>${secondRaw}<p>뒤</p>`)
        expect(restored.match(/noviis-sandboxed-post-html/g)).toHaveLength(2)
    })

    it('maps preserved payloads without changing marker attribute syntax or damaged markers', () => {
        const raw = '<section>원본</section>'
        const payload = encodeSandboxedPostHtml(raw).match(/data-value="([^"]+)"/)?.[1]
        const marker = `<div data-value='${payload}' data-extra="keep" class="noviis-sandboxed-post-html"></div>`
        const damagedMarker = '<div data-value="%%%" class="noviis-sandboxed-post-html"></div>'

        const mapped = mapSandboxedPostHtmlPayloads(
            `${marker}${damagedMarker}`,
            (decoded) => decoded.replace('원본', '변경'),
        )

        expect(mapped).toContain("data-value='")
        expect(mapped).toContain('data-extra="keep"')
        expect(mapped).toContain(damagedMarker)
        expect(expandSandboxedPostHtml(mapped)).toBe('<section>변경</section>' + damagedMarker)
    })

    it('falls back without deleting source when an editable boundary is damaged', () => {
        const damaged = '<p>앞</p><!--noviis-preserved-html-block:start--><style>.x{display:grid}</style><p>블록</p>'

        expect(restoreSandboxedPostHtmlAfterEditing(damaged)).toBe(damaged)
        expect(requiresPreservedPostHtml(damaged)).toBe(true)
        expect(decodeSandboxedPostHtml(encodeSandboxedPostHtml(damaged))).toBe(damaged)
    })

    it('adds a restrictive CSP with allowlisted static assets to sandboxed documents', () => {
        const source = buildSandboxedPostHtmlSource('<button onclick="run()">Run</button>', 'frame-1')

        expect(source).toContain('Content-Security-Policy')
        expect(source).toContain("default-src 'none'")
        expect(source).toContain('script-src http://localhost:3000')
        expect(source).not.toContain("script-src 'unsafe-inline'")
        expect(source).toContain('<script src="http://localhost:3000/sandbox-height-bridge.js?v=test-hash" data-frame-id="frame-1"></script>')
        expect(source).toContain("style-src 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com")
        expect(source).toContain('img-src data: blob: http://localhost:3000 https://cdn.noviis.kr')
        expect(source).toContain('font-src data: https://cdn.jsdelivr.net https://fonts.gstatic.com')
        expect(source).toContain('<meta name="referrer" content="no-referrer">')
        expect(source).toContain('media-src data: blob: https:')
        expect(source).not.toContain('img-src blob: https:')
        expect(source).not.toContain('media-src data: blob: https: http:')
        expect(source).toContain("connect-src 'none'")
        expect(source).toContain("form-action 'none'")
    })

    it('keeps the embedding CSP compatible with the external bridge and allowlisted fonts', () => {
        embeddingSecurityHeaders.forEach((headers) => {
            expect(headers).toContain("script-src 'self'")
            expect(headers).toContain("style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com")
            expect(headers).toContain("font-src 'self' data: https://cdn.jsdelivr.net https://fonts.gstatic.com")
        })
    })

    it('normalizes full html documents without nesting their document shell', () => {
        const source = buildSandboxedPostHtmlSource(
            '<!doctype html><html lang="ko" class="theme-dark"><head><meta name="viewport" content="width=320"><title>Card</title><style>.card{display:grid}</style></head><body class="document" style="padding: 1rem"><main class="card">Hello</main></body></html>',
            'frame-1',
        )

        expect(source.match(/<!doctype html>/gi)).toHaveLength(1)
        expect(source).toContain('<html lang="ko" class="theme-dark">')
        expect(source).toContain('<body class="document" style="padding: 1rem">')
        expect(source).toContain('<style>.card{display:grid}</style>')
        expect(source).toContain('<main class="card">Hello</main>')
        expect(source).not.toContain('<title>Card</title>')
        expect(source).not.toContain('content="width=320"')
    })

    it('removes author scripts and event handlers while preserving declarative interactions', () => {
        const source = buildSandboxedPostHtmlSource(
            '<details open><summary onclick="toggle()">More</summary><p onload="run()">Body</p></details><script>window.evil = true</script>',
            'frame-1',
        )

        expect(source).toContain('<details open=""><summary>More</summary><p>Body</p></details>')
        expect(source).not.toContain('onclick=')
        expect(source).not.toContain('onload=')
        expect(source).not.toContain('window.evil')
        expect(source).toContain('<script src="http://localhost:3000/sandbox-height-bridge.js?v=test-hash"')
    })

    it('adds defensive overflow styles for long and narrow static html', () => {
        const authorStyle = '<style>html body { overflow: hidden !important; }</style>'
        const source = buildSandboxedPostHtmlSource(`${authorStyle}<div>content</div>`, 'frame-1')

        expect(source.indexOf('data-noviis-sandbox-guard')).toBeGreaterThan(source.indexOf(authorStyle))
        expect(source).toContain('overflow-x: hidden !important; overflow-y: auto !important')
        expect(source).toContain('overflow-wrap: anywhere')
        expect(source).toContain('pre { max-width: 100%; overflow-x: auto; }')
        expect(source).toContain('table { display: block; max-width: 100%; overflow-x: auto; }')
        expect(source).toContain('body :where(.grid) > * { min-width: 0; }')
        expect(source).toContain('body :where([data-noviis-responsive-stack]) { grid-template-columns: minmax(0, 1fr) !important; }')
        expect(heightBridgeSource).toContain("var responsiveStackAttribute = 'data-noviis-responsive-stack'")
        expect(heightBridgeSource).toContain('hasOverflowingDescendant(grid)')
    })

    it('cleans up sandbox height polling when the frame unloads', () => {
        const source = buildSandboxedPostHtmlSource('<div>height</div>', 'frame-1')

        expect(source).toContain('sandbox-height-bridge.js')
        expect(heightBridgeSource).toContain('window.setInterval(postHeight, 500)')
        expect(heightBridgeSource).toContain("channel: 'noviis-post-html-sandbox'")
        expect(heightBridgeSource).toContain('window.clearInterval(intervalId)')
        expect(heightBridgeSource).toContain("window.addEventListener('pagehide', cleanup, { once: true })")
        expect(heightBridgeSource).toContain('resizeObserver.disconnect()')
    })

    it('measures content independently from the current iframe viewport height', () => {
        expect(heightBridgeSource).toContain("var descendants = body.querySelectorAll('*')")
        expect(heightBridgeSource).toContain('var fixedSubtree = new WeakSet()')
        expect(heightBridgeSource).toContain('Math.max(body.offsetHeight, bottom - bodyRect.top) + bodyMargins + rootInsets')
        expect(heightBridgeSource).not.toContain('doc ? doc.scrollHeight')
        expect(heightBridgeSource).not.toContain('doc ? doc.offsetHeight')
    })

    it('does not authorize user scripts and emits only the external bridge script', () => {
        const source = buildSandboxedPostHtmlSource(
            '<script nonce="noviis-height-bridge">window.evil = true</script>',
            'frame-1',
        )

        expect(source).not.toContain('window.evil')
        expect(source.match(/<script\b/g)).toHaveLength(1)
        expect(source).toContain('<script src="http://localhost:3000/sandbox-height-bridge.js?v=test-hash"')
    })
})
