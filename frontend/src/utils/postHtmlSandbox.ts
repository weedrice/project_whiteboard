const SANDBOX_TRIGGER_PATTERN = /<(?:!doctype|html|head|body|style|script)\b|<\w+[^>]*\son[a-z]+\s*=/i
const SANDBOX_MARKER_CLASS = 'noviis-sandboxed-post-html'
const SANDBOX_MARKER_SELECTOR = `.${SANDBOX_MARKER_CLASS}[data-value]`
const SANDBOX_MARKER_SELECTOR_PATTERN = /class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["'][^>]*\sdata-value=["'][^"']+["']|data-value=["'][^"']+["'][^>]*class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["']/i
const EDITOR_ELEMENT_ATTRIBUTES: Readonly<Record<string, ReadonlySet<string>>> = {
    p: new Set(['style']),
    h1: new Set(['style']),
    h2: new Set(['style']),
    h3: new Set(['style']),
    h4: new Set(['style']),
    h5: new Set(['style']),
    h6: new Set(['style']),
    blockquote: new Set(),
    ul: new Set(),
    ol: new Set(['start']),
    li: new Set(),
    pre: new Set(['class']),
    code: new Set(['class']),
    strong: new Set(),
    em: new Set(),
    s: new Set(),
    u: new Set(),
    a: new Set(['href', 'target', 'rel', 'class']),
    img: new Set(['src', 'alt', 'title', 'class', 'data-file-id', 'data-server-src']),
    br: new Set(),
    hr: new Set(),
    span: new Set([
        'style',
        'class',
        'data-type',
        'data-id',
        'data-label',
        'data-mention-suggestion-char',
        'data-mention-user-id',
    ]),
    mark: new Set(['style', 'data-color']),
    table: new Set(['style']),
    colgroup: new Set(),
    col: new Set(['span', 'style']),
    thead: new Set(),
    tbody: new Set(),
    tfoot: new Set(),
    tr: new Set(),
    th: new Set(['colspan', 'rowspan', 'colwidth', 'style']),
    td: new Set(['colspan', 'rowspan', 'colwidth', 'style']),
    div: new Set(['class', 'data-video-embed']),
    iframe: new Set(['src', 'frameborder', 'allowfullscreen', 'loading', 'referrerpolicy', 'sandbox', 'allow']),
}
const EDITOR_STYLE_PROPERTIES: Readonly<Record<string, ReadonlySet<string>>> = {
    p: new Set(['text-align']),
    h1: new Set(['text-align']),
    h2: new Set(['text-align']),
    h3: new Set(['text-align']),
    h4: new Set(['text-align']),
    h5: new Set(['text-align']),
    h6: new Set(['text-align']),
    span: new Set(['color', 'font-size', 'line-height']),
    mark: new Set(['background-color', 'color']),
    table: new Set(['min-width']),
    col: new Set(['min-width', 'width']),
    th: new Set(['text-align', 'min-width', 'width']),
    td: new Set(['text-align', 'min-width', 'width']),
}

export const SANDBOXED_POST_HTML_MARKER_CLASS = SANDBOX_MARKER_CLASS

export function requiresSandboxedPostHtml(content: string | null | undefined): boolean {
    return SANDBOX_TRIGGER_PATTERN.test(content ?? '')
}

export function containsSandboxedPostHtml(content: string | null | undefined): boolean {
    if (!content?.includes(SANDBOX_MARKER_CLASS)) return false
    if (typeof DOMParser === 'undefined') return SANDBOX_MARKER_SELECTOR_PATTERN.test(content)

    const doc = new DOMParser().parseFromString(content, 'text/html')
    return doc.body.querySelector(SANDBOX_MARKER_SELECTOR) != null
}

export function isStandaloneSandboxedPostHtml(content: string | null | undefined): boolean {
    return findStandaloneSandboxMarker(content) != null
}

export function requiresPreservedPostHtml(content: string | null | undefined): boolean {
    if (!content) return false
    if (containsSandboxedPostHtml(content)) return true
    if (requiresSandboxedPostHtml(content)) return true
    if (
        typeof DOMParser === 'undefined'
        || typeof document === 'undefined'
        || typeof NodeFilter === 'undefined'
    ) return hasUnsupportedEditorTagWithPattern(content)

    const template = document.createElement('template')
    template.innerHTML = content
    const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_COMMENT)
    let current = walker.nextNode()
    while (current) {
        if (current.nodeType === Node.COMMENT_NODE) return true
        if (current.nodeType === Node.ELEMENT_NODE) {
            if (!(current instanceof HTMLElement) || !isSupportedEditorElement(current)) return true
        }
        current = walker.nextNode()
    }
    return false
}

function isSupportedEditorElement(element: HTMLElement): boolean {
    const tag = element.tagName.toLowerCase()
    const allowedAttributes = EDITOR_ELEMENT_ATTRIBUTES[tag]
    if (!allowedAttributes) return false
    if (Array.from(element.attributes).some((attribute) => !allowedAttributes.has(attribute.name.toLowerCase()))) {
        return false
    }
    if (!hasSupportedEditorClass(element, tag) || !hasSupportedEditorStyle(element, tag)) return false

    if (tag === 'div') {
        return element.classList.length === 1
            && element.classList.contains('tiptap-video-wrapper')
            && element.querySelector(':scope > iframe') != null
    }
    if (tag === 'iframe') {
        const src = element.getAttribute('src') ?? ''
        return /^(?:https?:)?\/\/(?:www\.)?(?:youtube(?:-nocookie)?\.com\/embed\/|player\.vimeo\.com\/video\/)/i.test(src)
            && hasSupportedVideoFrameAttributes(element)
    }
    if (tag === 'a' && !hasSupportedLinkAttributes(element)) return false
    if (tag === 'span') {
        return element.hasAttribute('style')
            || hasCanonicalMentionAttributes(element)
    }
    return true
}

function hasSupportedLinkAttributes(element: HTMLElement): boolean {
    const target = element.getAttribute('target')
    const rel = element.getAttribute('rel')
    return (target == null || target === '_blank')
        && (rel == null || hasSupportedLinkRel(rel))
}

function hasSupportedLinkRel(rel: string): boolean {
    const tokens = new Set(rel.trim().toLowerCase().split(/\s+/).filter(Boolean))
    const isLegacyEditorRel = tokens.size === 2
        && tokens.has('noopener')
        && tokens.has('noreferrer')
    const isServerRel = tokens.size === 3
        && tokens.has('nofollow')
        && tokens.has('noopener')
        && tokens.has('noreferrer')
    return isLegacyEditorRel || isServerRel
}

function hasCanonicalMentionAttributes(element: HTMLElement): boolean {
    if (element.getAttribute('data-type') !== 'mention') return false
    if (element.classList.length !== 1 || !element.classList.contains('mention-node')) return false
    const id = element.getAttribute('data-id')?.trim()
    const label = element.getAttribute('data-label')?.trim()
    if (!id || !label) return false
    return element.getAttribute('data-mention-user-id') === id
        && element.getAttribute('data-mention-suggestion-char') === '@'
        && element.childElementCount === 0
        && element.textContent === `@${label}`
}

function hasSupportedVideoFrameAttributes(element: HTMLElement): boolean {
    const expectedValues: Readonly<Record<string, string>> = {
        frameborder: '0',
        allowfullscreen: 'true',
        loading: 'lazy',
        referrerpolicy: 'strict-origin-when-cross-origin',
        sandbox: 'allow-scripts allow-same-origin allow-presentation',
        allow: 'encrypted-media; picture-in-picture',
    }
    return Object.entries(expectedValues).every(([name, expected]) => {
        const actual = element.getAttribute(name)
        return actual == null || actual === expected
    })
}

function hasSupportedEditorClass(element: HTMLElement, tag: string): boolean {
    if (!element.hasAttribute('class')) return true
    const tokens = Array.from(element.classList)
    if (tag === 'pre') return tokens.every((token) => token === 'hljs')
    if (tag === 'code') return tokens.every((token) => token === 'hljs' || token.startsWith('language-'))
    if (tag === 'a') return tokens.every((token) => token === 'tiptap-link')
    if (tag === 'img') {
        const allowed = new Set(['tiptap-image-inline', 'max-w-full', 'h-auto', 'align-baseline'])
        return tokens.every((token) => allowed.has(token))
    }
    if (tag === 'span') return tokens.every((token) => token === 'mention-node')
    if (tag === 'div') return tokens.every((token) => token === 'tiptap-video-wrapper')
    return false
}

function hasSupportedEditorStyle(element: HTMLElement, tag: string): boolean {
    if (!element.hasAttribute('style')) return true
    const allowedProperties = EDITOR_STYLE_PROPERTIES[tag]
    const properties = Array.from(element.style)
    if (!allowedProperties || properties.length === 0) return false
    return properties.every((property) => allowedProperties.has(property.toLowerCase()))
}

function hasUnsupportedEditorTagWithPattern(content: string): boolean {
    const supportedTags = new Set(Object.keys(EDITOR_ELEMENT_ATTRIBUTES))
    return Array.from(content.matchAll(/<([a-z][\w-]*)\b/gi))
        .some((match) => !supportedTags.has(match[1].toLowerCase()))
}

export function encodeSandboxedPostHtml(content: string): string {
    if (!requiresPreservedPostHtml(content) || containsSandboxedPostHtml(content)) {
        return content
    }

    return `<div class="${SANDBOX_MARKER_CLASS}" data-value="${encodeSandboxedPostHtmlPayload(content)}"></div>`
}

export function encodeSandboxedPostHtmlPayload(content: string): string {
    return encodeUtf8Base64(content)
}

export function decodeSandboxedPostHtmlPayload(value: string | null | undefined): string | null {
    return decodeUtf8Base64(value ?? '')
}

export function decodeSandboxedPostHtml(content: string | null | undefined): string | null {
    const marker = findStandaloneSandboxMarker(content)
    if (typeof marker === 'string') return decodeSandboxedPostHtmlPayload(marker)
    return decodeSandboxedPostHtmlPayload(marker?.getAttribute('data-value'))
}

export function expandSandboxedPostHtml(content: string | null | undefined): string | null {
    if (!containsSandboxedPostHtml(content)) return null
    return (content ?? '').replace(
        /<div\b(?=[^>]*\bclass=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["'])(?=[^>]*\bdata-value=["'][^"']+["'])[^>]*>\s*<\/div>/gi,
        (marker) => {
            const payload = marker.match(/\bdata-value=["']([^"']+)["']/i)?.[1]
            return decodeSandboxedPostHtmlPayload(payload) ?? marker
        },
    )
}

export function mapSandboxedPostHtmlPayloads(
    content: string,
    transform: (payload: string) => string,
): string {
    if (!containsSandboxedPostHtml(content)) return content
    return content.replace(
        /<div\b(?=[^>]*\bclass=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["'])(?=[^>]*\bdata-value=["'][^"']+["'])[^>]*>\s*<\/div>/gi,
        (marker) => {
            const payload = marker.match(/\bdata-value=(["'])([^"']+)\1/i)?.[2]
            const decoded = decodeSandboxedPostHtmlPayload(payload)
            if (decoded == null) return marker
            const transformed = transform(decoded)
            if (transformed === decoded) return marker
            const encoded = encodeSandboxedPostHtmlPayload(transformed)
            return marker.replace(/\bdata-value=(["'])[^"']+\1/i, (_attribute, quote: string) => (
                `data-value=${quote}${encoded}${quote}`
            ))
        },
    )
}

function findStandaloneSandboxMarker(content: string | null | undefined): HTMLElement | string | null {
    if (!content?.includes(SANDBOX_MARKER_CLASS)) return null
    if (typeof DOMParser === 'undefined') {
        const trimmed = content.trim()
        if (!/^<div\b[^>]*>\s*<\/div>$/is.test(trimmed)) return null
        const match = trimmed.match(/class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["'][^>]*\sdata-value=["']([^"']+)["']/i)
            ?? trimmed.match(/data-value=["']([^"']+)["'][^>]*class=["'][^"']*\bnoviis-sandboxed-post-html\b[^"']*["']/i)
        return match?.[1] ?? null
    }

    const doc = new DOMParser().parseFromString(content, 'text/html')
    const meaningfulNodes = Array.from(doc.body.childNodes).filter((node) => (
        node.nodeType !== Node.TEXT_NODE || Boolean(node.textContent?.trim())
    ))
    if (meaningfulNodes.length !== 1) return null
    const marker = meaningfulNodes[0]
    if (!(marker instanceof HTMLElement) || !marker.matches(SANDBOX_MARKER_SELECTOR)) return null
    if (marker.childNodes.length > 0) return null
    return marker
}

export function buildSandboxedPostHtmlSource(content: string, frameId: string, nonce = createSandboxNonce()): string {
    const applicationOrigin = getSandboxApplicationOrigin()
    const documentParts = normalizeSandboxedPostDocument(content)
    return [
        '<!doctype html>',
        `<html${documentParts.htmlAttributes}>`,
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        `<meta http-equiv="Content-Security-Policy" content="${buildSandboxCsp(nonce, applicationOrigin)}">`,
        '<meta name="referrer" content="no-referrer">',
        '<base target="_blank">',
        '<style>',
        getSandboxBaseCss(),
        '</style>',
        documentParts.headHtml,
        '<style data-noviis-sandbox-guard>',
        getSandboxGuardCss(),
        '</style>',
        '</head>',
        `<body${documentParts.bodyAttributes}>`,
        documentParts.bodyHtml,
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

function buildSandboxCsp(nonce: string, applicationOrigin: string): string {
    return [
        "default-src 'none'",
        "style-src 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com",
        `script-src 'nonce-${nonce}'`,
        `img-src data: blob: ${applicationOrigin} https://cdn.noviis.kr`,
        'font-src data: https://cdn.jsdelivr.net https://fonts.gstatic.com',
        'media-src data: blob: https:',
        "connect-src 'none'",
        "form-action 'none'",
        "base-uri 'none'",
    ].join('; ')
}

function getSandboxApplicationOrigin(): string {
    if (typeof window !== 'undefined' && window.location?.origin) {
        try {
            const origin = new URL(window.location.origin).origin
            if (origin.startsWith('http://') || origin.startsWith('https://')) return origin
        } catch {
            // Fall back to the production origin below.
        }
    }
    return 'https://noviis.kr'
}

function normalizeSandboxedPostDocument(content: string): {
    htmlAttributes: string
    headHtml: string
    bodyAttributes: string
    bodyHtml: string
} {
    if (typeof DOMParser === 'undefined') {
        return { htmlAttributes: '', headHtml: '', bodyAttributes: '', bodyHtml: content }
    }

    const doc = new DOMParser().parseFromString(content, 'text/html')

    doc.querySelectorAll('script').forEach((script) => script.remove())
    doc.querySelectorAll<HTMLElement>('*').forEach((element) => {
        Array.from(element.attributes).forEach((attribute) => {
            if (/^on/i.test(attribute.name)) {
                element.removeAttribute(attribute.name)
            }
        })
    })

    const headHtml = Array.from(doc.head.children)
        .filter((element) => element.tagName === 'STYLE' || element.tagName === 'LINK')
        .map((element) => element.outerHTML)
        .join('')

    return {
        htmlAttributes: serializeSandboxAttributes(doc.documentElement),
        headHtml,
        bodyAttributes: serializeSandboxAttributes(doc.body),
        bodyHtml: doc.body.innerHTML,
    }
}

function serializeSandboxAttributes(element: Element): string {
    return Array.from(element.attributes)
        .map((attribute) => ` ${attribute.name}="${escapeSandboxAttribute(attribute.value)}"`)
        .join('')
}

function escapeSandboxAttribute(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
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
html, body { margin: 0; min-height: 0; background: transparent; }
body { color: var(--text-primary); font-family: var(--font-sans); }
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

function getSandboxGuardCss(): string {
    return `
html, body { max-width: 100%; overflow-x: hidden !important; overflow-y: auto !important; }
body { overflow-wrap: anywhere; }
img, video, iframe, svg, canvas { max-width: 100%; }
pre { max-width: 100%; overflow-x: auto; }
table { display: block; max-width: 100%; overflow-x: auto; }
body :where(.grid) > * { min-width: 0; }
body :where([data-noviis-responsive-stack]) { grid-template-columns: minmax(0, 1fr) !important; }
`
}

function getHeightBridgeScript(frameId: string, nonce: string): string {
    return `<script nonce="${nonce}">
(function () {
  var frameId = ${JSON.stringify(frameId)};
  var lastHeight = 0;
  var responsiveStackAttribute = 'data-noviis-responsive-stack';
  function enforceScrollableDocument() {
    var roots = [document.documentElement, document.body];
    for (var index = 0; index < roots.length; index += 1) {
      var root = roots[index];
      if (!root) continue;
      root.style.setProperty('overflow-x', 'hidden', 'important');
      root.style.setProperty('overflow-y', 'auto', 'important');
    }
  }
  function hasHorizontalOverflow(element) {
    return element.clientWidth > 0 && element.scrollWidth > element.clientWidth + 1;
  }
  function hasOverflowingDescendant(element) {
    var descendants = element.querySelectorAll('*');
    for (var index = 0; index < descendants.length; index += 1) {
      if (hasHorizontalOverflow(descendants[index])) return true;
    }
    return false;
  }
  function repairResponsiveGrids() {
    var isNarrow = document.documentElement.clientWidth <= 480;
    var grids = document.querySelectorAll('.grid');
    for (var index = 0; index < grids.length; index += 1) {
      var grid = grids[index];
      if (!isNarrow) {
        grid.removeAttribute(responsiveStackAttribute);
        continue;
      }
      if (grid.hasAttribute(responsiveStackAttribute)) continue;
      var display = window.getComputedStyle(grid).display;
      if (display !== 'grid' && display !== 'inline-grid') continue;
      if (hasHorizontalOverflow(grid) || hasOverflowingDescendant(grid)) {
        grid.setAttribute(responsiveStackAttribute, '');
      }
    }
  }
  function cssPixels(value) {
    return parseFloat(value) || 0;
  }
  function measure() {
    var body = document.body;
    if (!body) return 0;
    var bodyRect = body.getBoundingClientRect();
    var bottom = bodyRect.bottom;
    var descendants = body.querySelectorAll('*');
    var fixedSubtree = new WeakSet();
    for (var index = 0; index < descendants.length; index += 1) {
      var element = descendants[index];
      var parentElement = element.parentElement;
      if (
        (parentElement && fixedSubtree.has(parentElement))
        || window.getComputedStyle(element).position === 'fixed'
      ) {
        fixedSubtree.add(element);
        continue;
      }
      bottom = Math.max(bottom, element.getBoundingClientRect().bottom);
    }
    var bodyStyle = window.getComputedStyle(body);
    var rootStyle = window.getComputedStyle(document.documentElement);
    var bodyMargins = cssPixels(bodyStyle.marginTop) + cssPixels(bodyStyle.marginBottom);
    var rootInsets = cssPixels(rootStyle.paddingTop) + cssPixels(rootStyle.paddingBottom)
      + cssPixels(rootStyle.borderTopWidth) + cssPixels(rootStyle.borderBottomWidth);
    return Math.max(0, Math.ceil(
      Math.max(body.offsetHeight, bottom - bodyRect.top) + bodyMargins + rootInsets
    ));
  }
  function postHeight() {
    enforceScrollableDocument();
    repairResponsiveGrids();
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
