export const MAX_VIEWPORT_HTML_LENGTH = 64 * 1024
export const MAX_VIEWPORT_ELEMENTS = 300
export const MAX_VIEWPORT_IMAGE_DATA_LENGTH = 12 * 1024 * 1024

export type ViewportHtmlValidationReason = 'htmlTooLarge' | 'tooManyElements' | 'cssResource'

const HTML_NAMESPACE = 'http://www.w3.org/1999/xhtml'
const ALLOWED_TAGS = new Set([
    'div', 'span', 'p', 'br', 'hr', 'section', 'article', 'aside', 'header', 'footer', 'main',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'dl', 'dt', 'dd',
    'figure', 'figcaption', 'img', 'b', 'strong', 'i', 'em', 'u', 's', 'small', 'sub', 'sup',
    'code', 'pre', 'blockquote', 'style', 'table', 'caption', 'colgroup', 'col',
    'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
])
const REMOVED_SUBTREES = new Set([
    'script', 'noscript', 'meta', 'base', 'link', 'form', 'input', 'textarea', 'button',
    'select', 'option', 'optgroup', 'datalist', 'iframe', 'frame', 'frameset',
    'object', 'embed', 'svg', 'math', 'template', 'video', 'audio', 'source', 'track',
    'portal', 'fencedframe', 'canvas',
])
const ALLOWED_ATTRIBUTES = new Set(['class', 'id', 'style', 'lang', 'dir'])
const CSP = [
    "default-src 'none'",
    "script-src 'none'",
    "style-src 'unsafe-inline'",
    'img-src data:',
    "connect-src 'none'",
    "font-src 'none'",
    "media-src 'none'",
    "frame-src 'none'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
].join('; ')

/**
 * Builds decorative HTML for a host-controlled, noninteractive sandbox="" iframe.
 * The host resolves approved uploads to opaque tokens and supplies their raster
 * data URLs separately. Author-supplied URLs never become image requests.
 */
export function buildViewportHtmlSource(html: string, imageSources: ReadonlyMap<string, string> = new Map()): string | null {
    if (html.length > MAX_VIEWPORT_HTML_LENGTH || typeof document === 'undefined') return null

    // Template contents stay inert, including network-bearing elements, while
    // inspecting the untrusted fragment. Never attach this source to the page.
    const source = document.createElement('template')
    source.innerHTML = html
    if (getViewportHtmlValidationReason(html, source.content)) return null
    const resolvedImages = resolveImagesWithinBudget(source.content, imageSources)
    if (resolvedImages === null) return null

    const output = document.implementation.createHTMLDocument('')
    output.head.replaceChildren()
    output.body.replaceChildren()

    const charset = output.createElement('meta')
    charset.setAttribute('charset', 'utf-8')
    const policy = output.createElement('meta')
    policy.setAttribute('http-equiv', 'Content-Security-Policy')
    policy.setAttribute('content', CSP)
    const referrer = output.createElement('meta')
    referrer.setAttribute('name', 'referrer')
    referrer.setAttribute('content', 'no-referrer')
    output.head.append(charset, policy, referrer)

    const baseStyle = output.createElement('style')
    baseStyle.textContent = 'html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden}*{box-sizing:border-box}'
    output.head.append(baseStyle)
    output.body.setAttribute('inert', '')
    output.body.setAttribute('aria-hidden', 'true')

    for (const child of Array.from(source.content.childNodes)) appendDecorativeNode(child, output.body, output, resolvedImages)

    return `<!doctype html>${output.documentElement.outerHTML}`
}

/** Share the authoring preflight and final renderer's validation policy. */
export function getViewportHtmlValidationReason(html: string, content: ParentNode): ViewportHtmlValidationReason | null {
    if (html.length > MAX_VIEWPORT_HTML_LENGTH) return 'htmlTooLarge'
    // Count before removing unsafe elements so sanitization cannot bypass the cap.
    if (countElements(content) > MAX_VIEWPORT_ELEMENTS) return 'tooManyElements'
    // Image resources are supplied only by the host. CSP alone cannot distinguish
    // an approved raster data URL from author CSS containing a data SVG/image-set.
    for (const element of Array.from(content.querySelectorAll('style, [style]'))) {
        if (hasCssResource(element.getAttribute('style') ?? '')
            || (element.localName === 'style' && hasCssResource(element.textContent ?? ''))) return 'cssResource'
    }
    return null
}

function hasCssResource(css: string): boolean {
    // Inspect resource-bearing tokens outside CSS strings and comments. Comment
    // markers inside a quoted value must never hide a following declaration.
    let decoded = ''
    let quote = ''
    let cursor = 0
    while (cursor < css.length) {
        const character = css[cursor]!
        if (quote) {
            if (character === '\\') {
                cursor = readCssEscape(css, cursor).end
                continue
            }
            cursor += 1
            // An unescaped newline ends a bad CSS string; later tokens are live.
            if (character === quote || /[\n\r\f]/.test(character)) quote = ''
            continue
        }
        if (character === '"' || character === "'") {
            quote = character
            decoded += ' '
            cursor += 1
        } else if (css.startsWith('/*', cursor)) {
            const end = css.indexOf('*/', cursor + 2)
            cursor = end < 0 ? css.length : end + 2
        } else if (character === '\\') {
            const escape = readCssEscape(css, cursor)
            decoded += escape.value
            cursor = escape.end
        } else {
            decoded += character
            cursor += 1
        }
    }
    return /(?:url|src|image(?:-set)?)\s*\(|@\s*import\b/i.test(decoded)
}

function readCssEscape(css: string, start: number): { value: string; end: number } {
    const cursor = start + 1
    if (cursor === css.length) return { value: '\ufffd', end: cursor }
    const character = css[cursor]!
    if (/[\n\r\f]/.test(character)) {
        return { value: '', end: cursor + (css.startsWith('\r\n', cursor) ? 2 : 1) }
    }
    const hex = css.slice(cursor).match(/^[0-9a-f]{1,6}/i)?.[0]
    if (!hex) return { value: character, end: cursor + 1 }
    let end = cursor + hex.length
    if (css.startsWith('\r\n', end)) end += 2
    else if (/[\t\n\r\f ]/.test(css[end] ?? '')) end += 1
    const code = Number.parseInt(hex, 16)
    return {
        value: code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff)
            ? String.fromCodePoint(code) : '\ufffd',
        end,
    }
}

/** Images that survive decorative rendering, shared with the authenticated loader. */
export function getViewportImageElements(source: ParentNode): HTMLImageElement[] {
    const images: HTMLImageElement[] = []
    const pending = Array.from(source.children)
    while (pending.length > 0) {
        const element = pending.pop()!
        if (element.namespaceURI !== HTML_NAMESPACE || REMOVED_SUBTREES.has(element.localName)) continue
        if (element.localName === 'img') images.push(element as HTMLImageElement)
        else pending.push(...Array.from(element.children))
    }
    // Preserve document order for predictable downloads and token assignment.
    return images.reverse()
}

function resolveImagesWithinBudget(source: ParentNode, imageSources: ReadonlyMap<string, string>): Map<Element, string> | null {
    const resolved = new Map<Element, string>()
    const validated = new Map<string, string | null>()
    let length = 0
    for (const element of getViewportImageElements(source)) {
        const token = element.getAttribute('src') ?? ''
        if (!/^noviis-viewport-image:\d+$/.test(token)) continue
        // Reject before validating or assigning a huge data URL to a DOM node.
        const candidate = imageSources.get(token)
        if (candidate && candidate.length > MAX_VIEWPORT_IMAGE_DATA_LENGTH - length) return null
        if (!validated.has(token)) validated.set(token, getRasterImageSource(token, imageSources))
        const image = validated.get(token)
        if (image) {
            length += image.length
            resolved.set(element, image)
        }
    }
    return resolved
}

function countElements(source: ParentNode): number {
    let count = 0
    const pending: ParentNode[] = [source]
    while (pending.length > 0) {
        const parent = pending.pop()!
        for (const child of Array.from(parent.children)) {
            count += 1
            if (count > MAX_VIEWPORT_ELEMENTS) return count
            pending.push(child)
            if (child instanceof HTMLTemplateElement) pending.push(child.content)
        }
    }
    return count
}

function appendDecorativeNode(source: Node, target: Node, output: Document, resolvedImages: ReadonlyMap<Element, string>): void {
    if (source.nodeType === Node.TEXT_NODE) {
        target.appendChild(output.createTextNode(source.textContent ?? ''))
        return
    }
    if (!(source instanceof Element) || source.namespaceURI !== HTML_NAMESPACE) return

    const tag = source.localName
    if (REMOVED_SUBTREES.has(tag)) return
    if (!ALLOWED_TAGS.has(tag)) {
        // Links and unknown wrappers become plain decorative content.
        for (const child of Array.from(source.childNodes)) appendDecorativeNode(child, target, output, resolvedImages)
        return
    }

    const imageSource = tag === 'img' ? resolvedImages.get(source) : null
    if (tag === 'img' && !imageSource) return

    const element = output.createElement(tag)
    for (const attribute of Array.from(source.attributes)) {
        if (ALLOWED_ATTRIBUTES.has(attribute.name)) element.setAttribute(attribute.name, attribute.value)
    }
    if (tag === 'img') {
        element.setAttribute('src', imageSource!)
        element.setAttribute('alt', source.getAttribute('alt') ?? '')
        element.setAttribute('referrerpolicy', 'no-referrer')
        for (const dimension of ['width', 'height']) {
            const value = source.getAttribute(dimension)
            if (value && /^\d{1,5}$/.test(value) && Number(value) <= 32768) element.setAttribute(dimension, value)
        }
    }
    for (const child of Array.from(source.childNodes)) appendDecorativeNode(child, element, output, resolvedImages)
    target.appendChild(element)
}

function getRasterImageSource(value: string | null, imageSources: ReadonlyMap<string, string>): string | null {
    if (!value || !/^noviis-viewport-image:\d+$/.test(value)) return null
    const image = imageSources.get(value)
    return image && /^data:image\/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(image) ? image : null
}
