import { describe, expect, it } from 'vitest'
import {
    buildSandboxedPostHtmlSource,
    decodeSandboxedPostHtml,
    encodeSandboxedPostHtml,
    requiresSandboxedPostHtml,
} from '../postHtmlSandbox'

describe('postHtmlSandbox', () => {
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

    it('adds a restrictive CSP to sandboxed documents', () => {
        const source = buildSandboxedPostHtmlSource('<button onclick="run()">Run</button>', 'frame-1', 'test-nonce')

        expect(source).toContain('Content-Security-Policy')
        expect(source).toContain("default-src 'none'")
        expect(source).toContain("script-src 'nonce-test-nonce'")
        expect(source).not.toContain("script-src 'unsafe-inline'")
        expect(source).toContain('img-src data: blob: https:')
        expect(source).toContain('media-src data: blob: https:')
        expect(source).not.toContain('img-src data: blob: https: http:')
        expect(source).not.toContain('media-src data: blob: https: http:')
        expect(source).toContain("connect-src 'none'")
        expect(source).toContain("form-action 'none'")
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

        expect(source).toContain('<script nonce="noviis-height-bridge">window.evil = true</script>')
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
})
