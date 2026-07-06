const SANDBOX_TRIGGER_PATTERN = /<(?:!doctype|html|head|body|style|script)\b|<\w+[^>]*\son[a-z]+\s*=/i
const SANDBOX_MARKER_CLASS = 'noviis-sandboxed-post-html'
const SANDBOX_MARKER_SELECTOR = `.${SANDBOX_MARKER_CLASS}[data-value]`

export function requiresSandboxedPostHtml(content: string | null | undefined): boolean {
    return SANDBOX_TRIGGER_PATTERN.test(content ?? '')
}

export function encodeSandboxedPostHtml(content: string): string {
    if (!requiresSandboxedPostHtml(content) || decodeSandboxedPostHtml(content) != null) {
        return content
    }

    return `<div class="${SANDBOX_MARKER_CLASS}" data-value="${encodeUtf8Base64(content)}"></div>`
}

export function decodeSandboxedPostHtml(content: string | null | undefined): string | null {
    if (!content || !content.includes(SANDBOX_MARKER_CLASS)) {
        return null
    }

    if (typeof DOMParser === 'undefined') {
        return decodeSandboxedPostHtmlWithPattern(content)
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')
    const marker = doc.body.querySelector<HTMLElement>(SANDBOX_MARKER_SELECTOR)
    return decodeUtf8Base64(marker?.getAttribute('data-value') ?? '')
}

export function buildSandboxedPostHtmlSource(content: string, frameId: string, nonce = createSandboxNonce()): string {
    return [
        '<!doctype html>',
        '<html>',
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        `<meta http-equiv="Content-Security-Policy" content="${buildSandboxCsp(nonce)}">`,
        '<base target="_blank">',
        '<style>',
        getSandboxBaseCss(),
        '</style>',
        '</head>',
        '<body>',
        content,
        getHeightBridgeScript(frameId, nonce),
        '</body>',
        '</html>',
    ].join('')
}

function createSandboxNonce(): string {
    const bytes = new Uint8Array(16)
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
        crypto.getRandomValues(bytes)
    } else {
        for (let i = 0; i < bytes.length; i += 1) {
            bytes[i] = Math.floor(Math.random() * 256)
        }
    }
    return btoa(String.fromCharCode(...bytes))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/g, '')
}

function buildSandboxCsp(nonce: string): string {
    return [
        "default-src 'none'",
        "style-src 'unsafe-inline'",
        `script-src 'nonce-${nonce}'`,
        'img-src data: blob: https:',
        'font-src data: https:',
        'media-src data: blob: https:',
        "connect-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
    ].join('; ')
}

function decodeSandboxedPostHtmlWithPattern(content: string): string | null {
    const match = content.match(/class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["'][^>]*\sdata-value=["']([^"']+)["']/i)
        ?? content.match(/data-value=["']([^"']+)["'][^>]*class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["']/i)
    return decodeUtf8Base64(match?.[1] ?? '')
}

function encodeUtf8Base64(value: string): string {
    const bytes = new TextEncoder().encode(value)
    let binary = ''
    bytes.forEach((byte) => {
        binary += String.fromCharCode(byte)
    })
    return btoa(binary)
}

function decodeUtf8Base64(value: string): string | null {
    if (!value) return null

    try {
        const binary = atob(value)
        const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
        return new TextDecoder().decode(bytes)
    } catch {
        return null
    }
}

function getSandboxBaseCss(): string {
    return `
:root {
  color-scheme: light;
  --font-sans: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --surface-0: #f3f4f6;
  --surface-1: #ffffff;
  --bg-success: #ecfdf5;
  --bg-danger: #fef2f2;
  --fill-accent: #2563eb;
  --fill-success: #16a34a;
  --on-accent: #ffffff;
  --text-primary: #111827;
  --text-secondary: #4b5563;
  --text-muted: #6b7280;
  --text-danger: #b91c1c;
  --border: #e5e7eb;
  --border-strong: #9ca3af;
  --border-success: #86efac;
  --border-danger: #fecaca;
  --radius: 8px;
}
* { box-sizing: border-box; }
html, body { margin: 0; min-height: 0; overflow: hidden; background: transparent; }
body { color: var(--text-primary); font-family: var(--font-sans); }
img, video, iframe { max-width: 100%; }
button, input, textarea, select { font: inherit; }
.ti-check::before { content: "\\2713"; }
@media (prefers-color-scheme: dark) {
  :root {
    color-scheme: dark;
    --surface-0: #111827;
    --surface-1: #1f2937;
    --bg-success: rgba(22, 163, 74, 0.18);
    --bg-danger: rgba(239, 68, 68, 0.16);
    --fill-accent: #60a5fa;
    --fill-success: #22c55e;
    --on-accent: #0f172a;
    --text-primary: #f9fafb;
    --text-secondary: #d1d5db;
    --text-muted: #9ca3af;
    --text-danger: #fca5a5;
    --border: #374151;
    --border-strong: #6b7280;
    --border-success: rgba(34, 197, 94, 0.55);
    --border-danger: rgba(248, 113, 113, 0.55);
  }
}
`
}

function getHeightBridgeScript(frameId: string, nonce: string): string {
    return `<script nonce="${nonce}">
(function () {
  var frameId = ${JSON.stringify(frameId)};
  var lastHeight = 0;
  function measure() {
    var body = document.body;
    var doc = document.documentElement;
    return Math.max(
      body ? body.scrollHeight : 0,
      body ? body.offsetHeight : 0,
      doc ? doc.scrollHeight : 0,
      doc ? doc.offsetHeight : 0
    );
  }
  function postHeight() {
    var height = measure();
    if (Math.abs(height - lastHeight) < 2) return;
    lastHeight = height;
    parent.postMessage({ type: 'noviis-post-html-height', channel: 'noviis-post-html-sandbox', id: frameId, height: height }, '*');
  }
  window.addEventListener('load', postHeight);
  window.addEventListener('resize', postHeight);
  var resizeObserver = null;
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(postHeight);
    resizeObserver.observe(document.body);
  }
  var intervalId = window.setInterval(postHeight, 500);
  function cleanup() {
    if (intervalId !== null) {
      window.clearInterval(intervalId);
      intervalId = null;
    }
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
  }
  window.addEventListener('pagehide', cleanup, { once: true });
  window.addEventListener('beforeunload', cleanup, { once: true });
  postHeight();
}());
</script>`
}
