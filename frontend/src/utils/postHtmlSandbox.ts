const SANDBOX_TRIGGER_PATTERN = /<(?:!doctype|html|head|body|style|script)\b|<\w+[^>]*\son[a-z]+\s*=/i
const SANDBOX_MARKER_CLASS = 'noviis-sandboxed-post-html'
const SANDBOX_MARKER_SELECTOR = `.${SANDBOX_MARKER_CLASS}[data-value]`
const SANDBOX_DOCUMENT_STRUCTURE_PATTERN = /<(?:!doctype|html|head|body)\b/i
const EDITABLE_SANDBOX_BLOCK_START_PATTERN = /<!--noviis-preserved-html-block:start:([a-z0-9-]+)-->/gi
const HTML_RAW_TEXT_ELEMENTS = new Set(['script', 'style', 'textarea', 'title', 'xmp', 'iframe', 'noembed', 'noframes', 'plaintext'])

type HtmlTagToken = {
    start: number
    end: number
    name: string
    closing: boolean
    selfClosing: boolean
    source: string
}

type SandboxMarkerRange = {
    start: number
    end: number
    marker: string
    payload: string
    payloadStart: number
    payloadEnd: number
}

export type SandboxedPostHtmlSegment =
    | { type: 'content'; html: string }
    | { type: 'sandbox'; html: string }

type HtmlAttributeToken = {
    name: string
    value: string
    valueStart: number
    valueEnd: number
}

type EditableSandboxBlockRange = {
    start: number
    end: number
    rawHtml: string
}
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
    img: new Set(['src', 'alt', 'title', 'class', 'loading', 'data-file-id', 'data-server-src']),
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
    if (typeof DOMParser === 'undefined') return findSandboxMarkerRanges(content).length > 0

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
    if (tag === 'img' && !hasSupportedImageAttributes(element)) return false
    if (tag === 'a' && !hasSupportedLinkAttributes(element)) return false
    if (tag === 'span') {
        return element.hasAttribute('style')
            || hasCanonicalMentionAttributes(element)
    }
    return true
}

function hasSupportedImageAttributes(element: HTMLElement): boolean {
    const loading = element.getAttribute('loading')
    return loading == null || loading === 'lazy' || loading === 'eager'
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
    if (!requiresPreservedPostHtml(content)) return content
    if (containsSandboxedPostHtml(content)) return preserveUnsupportedHtmlAroundSandboxMarkers(content)

    return buildSandboxMarker(content)
}

function buildSandboxMarker(content: string): string {
    return `<div class="${SANDBOX_MARKER_CLASS}" data-value="${encodeSandboxedPostHtmlPayload(content)}"></div>`
}

function preserveUnsupportedHtmlAroundSandboxMarkers(content: string): string {
    const markers = findSandboxMarkerRanges(content)
    if (markers.length === 0) return buildFlattenedSandboxMarker(content)
    if (!hasOnlyTopLevelSandboxMarkers(content, markers.length)) return buildFlattenedSandboxMarker(content)

    const parts: string[] = []
    let segmentStart = 0
    markers.forEach((marker) => {
        const segment = content.slice(segmentStart, marker.start)
        parts.push(requiresPreservedPostHtml(segment) ? buildSandboxMarker(segment) : segment)
        parts.push(marker.marker)
        segmentStart = marker.end
    })

    const trailingSegment = content.slice(segmentStart)
    parts.push(requiresPreservedPostHtml(trailingSegment) ? buildSandboxMarker(trailingSegment) : trailingSegment)
    return parts.join('')
}

function buildFlattenedSandboxMarker(content: string): string {
    return buildSandboxMarker(expandSandboxedPostHtml(content) ?? content)
}

function hasOnlyTopLevelSandboxMarkers(content: string, expectedMarkerCount: number): boolean {
    if (SANDBOX_DOCUMENT_STRUCTURE_PATTERN.test(content) || typeof DOMParser === 'undefined') return false

    const document = new DOMParser().parseFromString(content, 'text/html')
    const markers = Array.from(document.body.querySelectorAll(SANDBOX_MARKER_SELECTOR))
    return markers.length === expectedMarkerCount
        && markers.every((marker) => marker.parentElement === document.body)
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
    return mapDecodedSandboxMarkers(content ?? '', (decoded) => decoded)
}

export function splitSandboxedPostHtmlSegments(
    content: string | null | undefined,
): SandboxedPostHtmlSegment[] | null {
    if (!content?.includes(SANDBOX_MARKER_CLASS)) return null

    const ranges = findSandboxMarkerRanges(content)
    if (ranges.length === 0) return null

    const segments: SandboxedPostHtmlSegment[] = []
    let segmentStart = 0
    ranges.forEach((range) => {
        const normalHtml = content.slice(segmentStart, range.start)
        appendSandboxedPostHtmlSegment(segments, 'content', normalHtml)

        const decoded = decodeSandboxedPostHtmlPayload(range.payload)
        if (decoded == null) {
            appendSandboxedPostHtmlSegment(segments, 'sandbox', range.marker)
        } else {
            const documentParts = splitTrailingContentFromFullHtmlDocument(decoded)
            appendSandboxedPostHtmlSegment(segments, 'sandbox', documentParts.documentHtml)
            if (documentParts.trailingHtml) {
                appendSandboxedPostHtmlSegment(
                    segments,
                    requiresPreservedPostHtml(documentParts.trailingHtml) ? 'sandbox' : 'content',
                    documentParts.trailingHtml,
                )
            }
        }
        segmentStart = range.end
    })

    const trailingHtml = content.slice(segmentStart)
    appendSandboxedPostHtmlSegment(segments, 'content', trailingHtml)
    return segments
}

function appendSandboxedPostHtmlSegment(
    segments: SandboxedPostHtmlSegment[],
    type: SandboxedPostHtmlSegment['type'],
    html: string,
): void {
    if (!html.trim()) return
    const previous = segments.at(-1)
    if (type === 'content' && previous?.type === 'content') {
        previous.html += html
        return
    }
    segments.push({ type, html })
}

export function expandSandboxedPostHtmlForEditing(content: string | null | undefined): string | null {
    if (!containsSandboxedPostHtml(content)) return null
    return mapDecodedSandboxMarkers(content ?? '', buildEditableSandboxBlock)
}

export function restoreSandboxedPostHtmlAfterEditing(content: string): string {
    const restored = restoreVersionedEditableSandboxBlocks(content)
    return restored.replace(
        /<!--noviis-preserved-html-block:start-->([\s\S]*?)<!--noviis-preserved-html-block:end-->/gi,
        (_segment, rawHtml: string) => restoreEditableSandboxRawHtml(rawHtml),
    )
}

function restoreVersionedEditableSandboxBlocks(content: string): string {
    const ranges = findEditableSandboxBlockRanges(content)
    if (ranges.length === 0) return content

    const parts: string[] = []
    let segmentStart = 0
    ranges.forEach((range) => {
        parts.push(content.slice(segmentStart, range.start))
        parts.push(restoreEditableSandboxRawHtml(range.rawHtml))
        segmentStart = range.end
    })
    parts.push(content.slice(segmentStart))
    return parts.join('')
}

function restoreEditableSandboxRawHtml(rawHtml: string): string {
    const documentParts = splitTrailingContentFromFullHtmlDocument(rawHtml)
    const restoredDocument = buildSandboxMarker(documentParts.documentHtml)
    if (!documentParts.trailingHtml) return restoredDocument
    return restoredDocument + (
        requiresPreservedPostHtml(documentParts.trailingHtml)
            ? buildSandboxMarker(documentParts.trailingHtml)
            : documentParts.trailingHtml
    )
}

function findEditableSandboxBlockRanges(content: string): EditableSandboxBlockRange[] {
    const ranges: EditableSandboxBlockRange[] = []
    const startPattern = new RegExp(
        EDITABLE_SANDBOX_BLOCK_START_PATTERN.source,
        EDITABLE_SANDBOX_BLOCK_START_PATTERN.flags,
    )
    let searchStart = 0

    while (searchStart < content.length) {
        startPattern.lastIndex = searchStart
        const startMatch = startPattern.exec(content)
        if (!startMatch || startMatch.index == null) break

        const boundaryId = startMatch[1]
        const rawStart = startMatch.index + startMatch[0].length
        const endToken = `<!--noviis-preserved-html-block:end:${boundaryId}-->`
        const firstEnd = content.indexOf(endToken, rawStart)
        if (firstEnd < 0) {
            searchStart = rawStart
            continue
        }

        startPattern.lastIndex = firstEnd + endToken.length
        const nextStart = startPattern.exec(content)
        const boundaryLimit = nextStart?.index ?? content.length
        const endStart = content.lastIndexOf(endToken, Math.max(rawStart, boundaryLimit - 1))
        if (endStart < rawStart) {
            searchStart = rawStart
            continue
        }

        ranges.push({
            start: startMatch.index,
            end: endStart + endToken.length,
            rawHtml: content.slice(rawStart, endStart),
        })
        searchStart = endStart + endToken.length
    }

    return ranges
}

function buildEditableSandboxBlock(rawHtml: string): string {
    const boundaryId = createEditableSandboxBoundaryId(rawHtml)
    return `<!--noviis-preserved-html-block:start:${boundaryId}-->${rawHtml}<!--noviis-preserved-html-block:end:${boundaryId}-->`
}

function createEditableSandboxBoundaryId(rawHtml: string): string {
    let hash = 2166136261
    for (let index = 0; index < rawHtml.length; index += 1) {
        hash ^= rawHtml.charCodeAt(index)
        hash = Math.imul(hash, 16777619)
    }

    const baseId = (hash >>> 0).toString(36)
    const normalizedRawHtml = rawHtml.toLowerCase()
    let boundaryId = baseId
    let suffix = 0
    while (normalizedRawHtml.includes(`<!--noviis-preserved-html-block:end:${boundaryId}-->`)) {
        suffix += 1
        boundaryId = `${baseId}-${suffix}`
    }
    return boundaryId
}

export function mapSandboxedPostHtmlPayloads(
    content: string,
    transform: (payload: string) => string,
): string {
    if (!containsSandboxedPostHtml(content)) return content
    return mapDecodedSandboxMarkers(content, (decoded, marker, range) => {
        const transformed = transform(decoded)
        if (transformed === decoded) return marker
        const encoded = encodeSandboxedPostHtmlPayload(transformed)
        const payloadStart = range.payloadStart - range.start
        const payloadEnd = range.payloadEnd - range.start
        return `${marker.slice(0, payloadStart)}${encoded}${marker.slice(payloadEnd)}`
    })
}

function mapDecodedSandboxMarkers(
    content: string,
    transform: (decoded: string, marker: string, range: SandboxMarkerRange) => string,
): string {
    const parts: string[] = []
    let segmentStart = 0
    findSandboxMarkerRanges(content).forEach((range) => {
        parts.push(content.slice(segmentStart, range.start))
        const decoded = decodeSandboxedPostHtmlPayload(range.payload)
        parts.push(decoded == null ? range.marker : transform(decoded, range.marker, range))
        segmentStart = range.end
    })
    parts.push(content.slice(segmentStart))
    return parts.join('')
}

function findStandaloneSandboxMarker(content: string | null | undefined): HTMLElement | string | null {
    if (!content?.includes(SANDBOX_MARKER_CLASS)) return null
    if (typeof DOMParser === 'undefined') {
        const trimmed = content.trim()
        const markers = findSandboxMarkerRanges(trimmed)
        if (markers.length !== 1 || markers[0].start !== 0 || markers[0].end !== trimmed.length) return null
        return markers[0].payload
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

function findSandboxMarkerRanges(content: string): SandboxMarkerRange[] {
    const ranges: SandboxMarkerRange[] = []
    let cursor = 0

    while (cursor < content.length) {
        const tagStart = content.indexOf('<', cursor)
        if (tagStart < 0) break

        if (content.startsWith('<!--', tagStart)) {
            const commentEnd = content.indexOf('-->', tagStart + 4)
            cursor = commentEnd < 0 ? content.length : commentEnd + 3
            continue
        }
        if (content.startsWith('<![CDATA[', tagStart)) {
            const cdataEnd = content.indexOf(']]>', tagStart + 9)
            cursor = cdataEnd < 0 ? content.length : cdataEnd + 3
            continue
        }
        if (content.startsWith('<!', tagStart) || content.startsWith('<?', tagStart)) {
            const declarationEnd = content.indexOf('>', tagStart + 2)
            cursor = declarationEnd < 0 ? content.length : declarationEnd + 1
            continue
        }

        const token = parseHtmlTagAt(content, tagStart)
        if (!token) {
            cursor = tagStart + 1
            continue
        }

        if (!token.closing && HTML_RAW_TEXT_ELEMENTS.has(token.name)) {
            cursor = findRawTextElementEnd(content, token)
            continue
        }

        const dataValue = !token.closing && token.name === 'div'
            ? getSandboxMarkerDataValue(token)
            : null
        if (!token.selfClosing && dataValue) {
            const closingStart = skipHtmlWhitespace(content, token.end)
            const closingToken = parseHtmlTagAt(content, closingStart)
            if (closingToken?.closing && closingToken.name === 'div') {
                ranges.push({
                    start: token.start,
                    end: closingToken.end,
                    marker: content.slice(token.start, closingToken.end),
                    payload: dataValue.value,
                    payloadStart: token.start + dataValue.valueStart,
                    payloadEnd: token.start + dataValue.valueEnd,
                })
                cursor = closingToken.end
                continue
            }
        }

        cursor = token.end
    }

    return ranges
}

function splitTrailingContentFromFullHtmlDocument(content: string): {
    documentHtml: string
    trailingHtml: string
} {
    const closingHtmlEnd = findFullHtmlDocumentEnd(content)
    if (closingHtmlEnd == null) return { documentHtml: content, trailingHtml: '' }

    const trailingHtml = content.slice(closingHtmlEnd)
    if (!trailingHtml.trim()) return { documentHtml: content, trailingHtml: '' }
    return {
        documentHtml: content.slice(0, closingHtmlEnd),
        trailingHtml,
    }
}

function findFullHtmlDocumentEnd(content: string): number | null {
    let cursor = 0
    let hasOpeningHtmlTag = false

    while (cursor < content.length) {
        const tagStart = content.indexOf('<', cursor)
        if (tagStart < 0) break

        if (content.startsWith('<!--', tagStart)) {
            const commentEnd = content.indexOf('-->', tagStart + 4)
            cursor = commentEnd < 0 ? content.length : commentEnd + 3
            continue
        }
        if (content.startsWith('<![CDATA[', tagStart)) {
            const cdataEnd = content.indexOf(']]>', tagStart + 9)
            cursor = cdataEnd < 0 ? content.length : cdataEnd + 3
            continue
        }
        if (content.startsWith('<!', tagStart) || content.startsWith('<?', tagStart)) {
            const declarationEnd = content.indexOf('>', tagStart + 2)
            cursor = declarationEnd < 0 ? content.length : declarationEnd + 1
            continue
        }

        const token = parseHtmlTagAt(content, tagStart)
        if (!token) {
            cursor = tagStart + 1
            continue
        }
        if (!token.closing && HTML_RAW_TEXT_ELEMENTS.has(token.name)) {
            cursor = findRawTextElementEnd(content, token)
            continue
        }
        if (token.name === 'html') {
            if (!token.closing) hasOpeningHtmlTag = true
            else if (hasOpeningHtmlTag) return token.end
        }
        cursor = token.end
    }
    return null
}

function parseHtmlTagAt(content: string, start: number): HtmlTagToken | null {
    if (content[start] !== '<') return null
    let cursor = start + 1
    const closing = content[cursor] === '/'
    if (closing) cursor += 1
    while (/\s/.test(content[cursor] ?? '')) cursor += 1

    const nameMatch = content.slice(cursor).match(/^[a-z][\w:-]*/i)
    if (!nameMatch) return null
    const name = nameMatch[0].toLowerCase()
    cursor += nameMatch[0].length

    let quote = ''
    while (cursor < content.length) {
        const character = content[cursor]
        if (quote) {
            if (character === quote) quote = ''
        } else if (character === '"' || character === "'") {
            quote = character
        } else if (character === '>') {
            const source = content.slice(start, cursor + 1)
            return {
                start,
                end: cursor + 1,
                name,
                closing,
                selfClosing: /\/\s*>$/.test(source),
                source,
            }
        }
        cursor += 1
    }
    return null
}

function getSandboxMarkerDataValue(token: HtmlTagToken): HtmlAttributeToken | null {
    const attributes = parseHtmlAttributes(token)
    const classValue = attributes.get('class')?.value ?? ''
    const classes = classValue.trim().split(/\s+/).filter(Boolean)
    const dataValue = attributes.get('data-value')
    return classes.includes(SANDBOX_MARKER_CLASS) && dataValue?.value
        ? dataValue
        : null
}

function parseHtmlAttributes(token: HtmlTagToken): Map<string, HtmlAttributeToken> {
    const attributes = new Map<string, HtmlAttributeToken>()
    const source = token.source
    let cursor = 1
    while (/\s/.test(source[cursor] ?? '')) cursor += 1
    if (source[cursor] === '/') cursor += 1
    while (/\s/.test(source[cursor] ?? '')) cursor += 1
    cursor += source.slice(cursor).match(/^[a-z][\w:-]*/i)?.[0].length ?? 0

    while (cursor < source.length) {
        while (/\s/.test(source[cursor] ?? '')) cursor += 1
        if (source[cursor] === '>' || source[cursor] === '/') break

        const nameStart = cursor
        while (cursor < source.length && !/[\s=/>]/.test(source[cursor])) cursor += 1
        if (cursor === nameStart) {
            cursor += 1
            continue
        }
        const name = source.slice(nameStart, cursor).toLowerCase()
        while (/\s/.test(source[cursor] ?? '')) cursor += 1

        let value = ''
        let valueStart = cursor
        let valueEnd = cursor
        if (source[cursor] === '=') {
            cursor += 1
            while (/\s/.test(source[cursor] ?? '')) cursor += 1
            const quote = source[cursor] === '"' || source[cursor] === "'" ? source[cursor] : ''
            if (quote) cursor += 1
            valueStart = cursor
            if (quote) {
                while (cursor < source.length && source[cursor] !== quote) cursor += 1
            } else {
                while (cursor < source.length && !/[\s/>]/.test(source[cursor])) cursor += 1
            }
            valueEnd = cursor
            value = source.slice(valueStart, valueEnd)
            if (quote && source[cursor] === quote) cursor += 1
        }

        if (!attributes.has(name)) {
            attributes.set(name, { name, value, valueStart, valueEnd })
        }
    }

    return attributes
}

function skipHtmlWhitespace(content: string, start: number): number {
    let cursor = start
    while (/\s/.test(content[cursor] ?? '')) cursor += 1
    return cursor
}

function findRawTextElementEnd(content: string, openingToken: HtmlTagToken): number {
    if (openingToken.selfClosing || openingToken.name === 'plaintext') return content.length
    const closingPattern = new RegExp(`<\\/\\s*${openingToken.name}\\s*>`, 'gi')
    closingPattern.lastIndex = openingToken.end
    const match = closingPattern.exec(content)
    return match ? match.index + match[0].length : content.length
}

export function buildSandboxedPostHtmlSource(content: string, frameId: string): string {
    const applicationOrigin = getSandboxApplicationOrigin()
    const documentParts = normalizeSandboxedPostDocument(content)
    return [
        '<!doctype html>',
        `<html${documentParts.htmlAttributes}>`,
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        `<meta http-equiv="Content-Security-Policy" content="${buildSandboxCsp(applicationOrigin)}">`,
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
        getHeightBridgeElement(frameId, applicationOrigin),
        '</body>',
        '</html>',
    ].join('')
}

function buildSandboxCsp(applicationOrigin: string): string {
    return [
        "default-src 'none'",
        "style-src 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com",
        `script-src ${applicationOrigin}`,
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

function getHeightBridgeElement(frameId: string, applicationOrigin: string): string {
    const bridgeVersion = typeof __COMMIT_HASH__ === 'undefined' ? 'runtime' : __COMMIT_HASH__
    const bridgeUrl = `${applicationOrigin}/sandbox-height-bridge.js?v=${encodeURIComponent(bridgeVersion)}`
    return `<script src="${escapeSandboxAttribute(bridgeUrl)}" data-frame-id="${escapeSandboxAttribute(frameId)}"></script>`
}
