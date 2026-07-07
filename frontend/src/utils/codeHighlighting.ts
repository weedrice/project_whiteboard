import { createLowlight, common } from 'lowlight'

export const lowlight = createLowlight(common)

const LANGUAGE_CLASS_PATTERN = /\blanguage-([a-z0-9_+-]+)\b/i

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderHastNode(node: any): string {
  if (!node) return ''
  if (node.type === 'text') {
    return escapeHtml(String(node.value ?? ''))
  }
  if (node.type !== 'element') {
    return Array.isArray(node.children) ? node.children.map(renderHastNode).join('') : ''
  }

  const tagName = String(node.tagName ?? 'span')
  const properties = node.properties ?? {}
  const className = Array.isArray(properties.className)
    ? properties.className.map(String).join(' ')
    : ''
  const classAttribute = className ? ` class="${escapeHtml(className)}"` : ''
  const children = Array.isArray(node.children) ? node.children.map(renderHastNode).join('') : ''
  return `<${tagName}${classAttribute}>${children}</${tagName}>`
}

export function highlightCodeBlocks(html: string): string {
  if (typeof DOMParser === 'undefined') {
    return html
  }

  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  doc.querySelectorAll('pre code').forEach((code) => {
    const rawCode = code.textContent ?? ''
    const language = code.className.match(LANGUAGE_CLASS_PATTERN)?.[1]
    const tree = language && lowlight.registered(language)
      ? lowlight.highlight(language, rawCode)
      : lowlight.highlightAuto(rawCode)
    const resolvedLanguage = language ?? tree.data?.language

    code.innerHTML = Array.isArray(tree.children) ? tree.children.map(renderHastNode).join('') : escapeHtml(rawCode)
    code.classList.add('hljs')
    if (resolvedLanguage) {
      code.classList.add(`language-${resolvedLanguage}`)
    }
  })

  return doc.body.innerHTML
}
