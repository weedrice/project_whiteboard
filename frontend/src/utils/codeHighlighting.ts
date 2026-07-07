import { createLowlight, common } from 'lowlight'
import { escapeHtmlAttribute, escapeHtmlText } from '@/utils/htmlEscape'
import { transformHtmlDocument } from '@/utils/htmlTransformPipeline'

export const lowlight = createLowlight(common)

const LANGUAGE_CLASS_PATTERN = /\blanguage-([a-z0-9_+-]+)\b/i

export interface CodeBlockHighlightLabels {
  copy: string
  copyAriaLabel: string
}

const DEFAULT_CODE_BLOCK_LABELS: CodeBlockHighlightLabels = {
  copy: 'Copy',
  copyAriaLabel: 'Copy code',
}

function renderHastNode(node: any): string {
  if (!node) return ''
  if (node.type === 'text') {
    return escapeHtmlText(String(node.value ?? ''))
  }
  if (node.type !== 'element') {
    return Array.isArray(node.children) ? node.children.map(renderHastNode).join('') : ''
  }

  const tagName = String(node.tagName ?? 'span')
  const properties = node.properties ?? {}
  const className = Array.isArray(properties.className)
    ? properties.className.map(String).join(' ')
    : ''
  const classAttribute = className ? ` class="${escapeHtmlAttribute(className)}"` : ''
  const children = Array.isArray(node.children) ? node.children.map(renderHastNode).join('') : ''
  return `<${tagName}${classAttribute}>${children}</${tagName}>`
}

export function highlightCodeBlocksInDocument(
  doc: Document,
  labels: CodeBlockHighlightLabels = DEFAULT_CODE_BLOCK_LABELS
): void {
  doc.querySelectorAll('pre code').forEach((code) => {
    const rawCode = code.textContent ?? ''
    const language = code.className.match(LANGUAGE_CLASS_PATTERN)?.[1]
    const tree = language && lowlight.registered(language)
      ? lowlight.highlight(language, rawCode)
      : lowlight.highlightAuto(rawCode)
    const resolvedLanguage = language ?? tree.data?.language

    code.innerHTML = Array.isArray(tree.children) ? tree.children.map(renderHastNode).join('') : escapeHtmlText(rawCode)
    code.classList.add('hljs')
    if (resolvedLanguage) {
      code.classList.add(`language-${resolvedLanguage}`)
    }

    const pre = code.closest('pre')
    if (!pre || pre.parentElement?.classList.contains('nv-code-block')) return

    const wrapper = doc.createElement('div')
    wrapper.className = 'nv-code-block'

    const toolbar = doc.createElement('div')
    toolbar.className = 'nv-code-block-toolbar'

    const label = doc.createElement('span')
    label.className = 'nv-code-block-language'
    label.textContent = resolvedLanguage || 'text'

    const copyButton = doc.createElement('button')
    copyButton.type = 'button'
    copyButton.className = 'nv-code-block-copy'
    copyButton.setAttribute('data-code-copy-button', '')
    copyButton.setAttribute('aria-label', labels.copyAriaLabel)
    copyButton.textContent = labels.copy

    toolbar.append(label, copyButton)
    pre.parentNode?.insertBefore(wrapper, pre)
    wrapper.append(toolbar, pre)
  })
}

export function highlightCodeBlocks(
  html: string,
  labels: CodeBlockHighlightLabels = DEFAULT_CODE_BLOCK_LABELS
): string {
  return transformHtmlDocument(html, [
    (doc) => highlightCodeBlocksInDocument(doc, labels),
  ])
}
