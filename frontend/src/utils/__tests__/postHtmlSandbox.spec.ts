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
        const source = buildSandboxedPostHtmlSource('<button onclick="run()">Run</button>', 'frame-1')

        expect(source).toContain('Content-Security-Policy')
        expect(source).toContain("default-src 'none'")
        expect(source).toContain("connect-src 'none'")
        expect(source).toContain("form-action 'none'")
    })

    it('cleans up sandbox height polling when the frame unloads', () => {
        const source = buildSandboxedPostHtmlSource('<div>height</div>', 'frame-1')

        expect(source).toContain('window.setInterval(postHeight, 500)')
        expect(source).toContain("channel: 'noviis-post-html-sandbox'")
        expect(source).toContain('window.clearInterval(intervalId)')
        expect(source).toContain("window.addEventListener('pagehide', cleanup, { once: true })")
        expect(source).toContain('resizeObserver.disconnect()')
    })
})
