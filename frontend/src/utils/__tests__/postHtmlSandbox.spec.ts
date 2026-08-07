import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    buildSandboxedPostHtmlSource,
    containsSandboxedPostHtml,
    decodeSandboxedPostHtml,
    encodeSandboxedPostHtml,
    expandSandboxedPostHtml,
    requiresPreservedPostHtml,
    requiresSandboxedPostHtml,
} from '../postHtmlSandbox'

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
        expect(requiresPreservedPostHtml('<pre class="hljs"><code class="language-typescript">const value = 1</code></pre>')).toBe(false)
        expect(requiresPreservedPostHtml('<span class="mention-node" data-type="mention" data-id="7" data-label="Novi" data-mention-suggestion-char="@" data-mention-user-id="7">@Novi</span>')).toBe(false)
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

    it('adds a restrictive CSP with allowlisted static assets to sandboxed documents', () => {
        const source = buildSandboxedPostHtmlSource('<button onclick="run()">Run</button>', 'frame-1', 'test-nonce')

        expect(source).toContain('Content-Security-Policy')
        expect(source).toContain("default-src 'none'")
        expect(source).toContain("script-src 'nonce-test-nonce'")
        expect(source).not.toContain("script-src 'unsafe-inline'")
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

    it('normalizes full html documents without nesting their document shell', () => {
        const source = buildSandboxedPostHtmlSource(
            '<!doctype html><html lang="ko" class="theme-dark"><head><meta name="viewport" content="width=320"><title>Card</title><style>.card{display:grid}</style></head><body class="document" style="padding: 1rem"><main class="card">Hello</main></body></html>',
            'frame-1',
            'test-nonce',
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
            'test-nonce',
        )

        expect(source).toContain('<details open=""><summary>More</summary><p>Body</p></details>')
        expect(source).not.toContain('onclick=')
        expect(source).not.toContain('onload=')
        expect(source).not.toContain('window.evil')
        expect(source).toContain('<script nonce="test-nonce">')
    })

    it('adds defensive overflow styles for long and narrow static html', () => {
        const authorStyle = '<style>html body { overflow: hidden !important; }</style>'
        const source = buildSandboxedPostHtmlSource(`${authorStyle}<div>content</div>`, 'frame-1', 'test-nonce')

        expect(source.indexOf('data-noviis-sandbox-guard')).toBeGreaterThan(source.indexOf(authorStyle))
        expect(source).toContain('overflow-x: hidden !important; overflow-y: auto !important')
        expect(source).toContain("root.style.setProperty('overflow-y', 'auto', 'important')")
        expect(source).toContain('overflow-wrap: anywhere')
        expect(source).toContain('pre { max-width: 100%; overflow-x: auto; }')
        expect(source).toContain('table { display: block; max-width: 100%; overflow-x: auto; }')
        expect(source).toContain('body :where(.grid) > * { min-width: 0; }')
        expect(source).toContain('body :where([data-noviis-responsive-stack]) { grid-template-columns: minmax(0, 1fr) !important; }')
        expect(source).toContain("var responsiveStackAttribute = 'data-noviis-responsive-stack'")
        expect(source).toContain('hasOverflowingDescendant(grid)')
    })

    it('cleans up sandbox height polling when the frame unloads', () => {
        const source = buildSandboxedPostHtmlSource('<div>height</div>', 'frame-1', 'height-nonce')

        expect(source).toContain('window.setInterval(postHeight, 500)')
        expect(source).toContain('<script nonce="height-nonce">')
        expect(source).toContain("channel: 'noviis-post-html-sandbox'")
        expect(source).toContain('window.clearInterval(intervalId)')
        expect(source).toContain("window.addEventListener('pagehide', cleanup, { once: true })")
        expect(source).toContain('resizeObserver.disconnect()')
    })

    it('does not authorize user html that guesses the old static nonce', () => {
        const source = buildSandboxedPostHtmlSource(
            '<script nonce="noviis-height-bridge">window.evil = true</script>',
            'frame-1',
            'fresh-nonce',
        )

        expect(source).not.toContain('window.evil')
        expect(source).toContain("script-src 'nonce-fresh-nonce'")
        expect(source).toContain('<script nonce="fresh-nonce">')
        expect(source).not.toContain("script-src 'nonce-noviis-height-bridge'")
    })

    it('generates a fresh nonce for each sandbox document by default', () => {
        const first = buildSandboxedPostHtmlSource('<div>first</div>', 'frame-1')
        const second = buildSandboxedPostHtmlSource('<div>second</div>', 'frame-2')
        const noncePattern = /script-src 'nonce-([^']+)'/

        const firstNonce = first.match(noncePattern)?.[1]
        const secondNonce = second.match(noncePattern)?.[1]

        expect(firstNonce).toBeTruthy()
        expect(secondNonce).toBeTruthy()
        expect(firstNonce).not.toBe(secondNonce)
        expect(first).toContain(`<script nonce="${firstNonce}">`)
        expect(second).toContain(`<script nonce="${secondNonce}">`)
    })

    it('uses crypto random values for the default nonce when available', () => {
        const getRandomValues = vi.fn((bytes: Uint8Array) => {
            bytes.set(Array.from({ length: 16 }, (_, index) => index))
            return bytes
        })
        vi.stubGlobal('crypto', { getRandomValues })

        const source = buildSandboxedPostHtmlSource('<div>secure</div>', 'frame-1')

        expect(getRandomValues).toHaveBeenCalledTimes(1)
        expect(source).toContain("script-src 'nonce-AAECAwQFBgcICQoLDA0ODw'")
        expect(source).toContain('<script nonce="AAECAwQFBgcICQoLDA0ODw">')
    })

    it('still generates a nonce when browser crypto is unavailable', () => {
        vi.stubGlobal('crypto', undefined)
        vi.spyOn(Math, 'random').mockReturnValue(0)

        const source = buildSandboxedPostHtmlSource('<div>fallback</div>', 'frame-1')

        expect(source).toContain("script-src 'nonce-AAAAAAAAAAAAAAAAAAAAAA'")
        expect(source).toContain('<script nonce="AAAAAAAAAAAAAAAAAAAAAA">')
    })
})
