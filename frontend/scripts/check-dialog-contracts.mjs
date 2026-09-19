import { readdir, readFile } from 'node:fs/promises'
import { extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { NodeTypes, parse } from '@vue/compiler-dom'

const srcRoot = fileURLToPath(new URL('../src', import.meta.url))
const allowedFooterActionTags = new Set(['BaseButton', 'button', 'a', 'RouterLink', 'router-link'])
const duplicatePaddingToken = /^(?:(?:sm|md|lg|xl|2xl):)*(?:p|px|py)-(?!0$).+$/

async function collectFiles(directory, extensions) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectFiles(path, extensions)
    return extensions.has(extname(entry.name)) ? [path] : []
  }))

  return nested.flat()
}

function extractTemplate(source) {
  const templateStart = source.indexOf('<template')
  if (templateStart === -1) return null
  const contentStart = source.indexOf('>', templateStart) + 1
  const contentEnd = source.lastIndexOf('</template>')
  if (contentStart === 0 || contentEnd < contentStart) return null

  return {
    content: source.slice(contentStart, contentEnd),
    startLine: source.slice(0, contentStart).split(/\r?\n/).length,
  }
}

function walkElements(node, visit, shouldDescend = () => true) {
  if (node.type === NodeTypes.ELEMENT) {
    visit(node)
    if (!shouldDescend(node)) return
  }
  node.children?.forEach((child) => walkElements(child, visit, shouldDescend))
}

function staticAttribute(element, name) {
  const attribute = element.props.find((prop) => prop.type === NodeTypes.ATTRIBUTE && prop.name === name)
  return attribute?.value?.content
}

function hasBoundAttribute(element, name) {
  return element.props.some((prop) => (
    prop.type === NodeTypes.DIRECTIVE
    && prop.name === 'bind'
    && prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION
    && prop.arg.content === name
  ))
}

function classTokens(element) {
  return new Set((staticAttribute(element, 'class') ?? '').split(/\s+/).filter(Boolean))
}

function isFooterTemplate(element) {
  return element.tag === 'template' && element.props.some((prop) => (
    prop.type === NodeTypes.DIRECTIVE
    && prop.name === 'slot'
    && prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION
    && prop.arg.content === 'footer'
  ))
}

function absoluteLine(template, element) {
  return template.startLine + element.loc.start.line - 1
}

function hasLegacyActionContainer(element) {
  const classes = classTokens(element)
  return element.tag === 'AdminModalActions' || classes.has('modal-actions') || classes.has('justify-end')
}

const violations = []
const vueSourceByFile = new Map()
const sourceFiles = await collectFiles(srcRoot, new Set(['.ts', '.vue']))
for (const file of sourceFiles) {
  const displayPath = relative(srcRoot, file)
  const isTestSource = displayPath.split(/[\\/]/).includes('__tests__') || /\.spec\.ts$/.test(displayPath)
  if (isTestSource) continue

  const source = await readFile(file, 'utf8')
  if (extname(file) === '.vue') vueSourceByFile.set(file, source)
  source.split(/\r?\n/).forEach((line, index) => {
    if (/\b(?:window|globalThis)\.(?:alert|confirm|prompt)\s*\(/.test(line)) {
      violations.push(`${displayPath}:${index + 1} 브라우저 기본 dialog 대신 공통 confirm/prompt UI를 사용해야 합니다.`)
    }
  })
}

for (const file of sourceFiles.filter((path) => extname(path) === '.vue')) {
  const source = vueSourceByFile.get(file) ?? await readFile(file, 'utf8')
  const displayPath = relative(srcRoot, file)
  const template = extractTemplate(source)
  if (!template) continue

  let ast
  try {
    ast = parse(template.content, {
      comments: true,
      onError: (error) => { throw error },
    })
  } catch (error) {
    violations.push(`${displayPath}:${template.startLine} Vue template AST를 분석할 수 없습니다: ${error.message}`)
    continue
  }

  walkElements(ast, (element) => {
    const classes = classTokens(element)
    const line = absoluteLine(template, element)

    if (staticAttribute(element, 'role') === 'dialog') {
      const classified = [...classes].some((name) => /^nv-dialog-(?:surface|overlay)--(?:modal|popover|sheet|media)$/.test(name))
      const named = staticAttribute(element, 'aria-label') != null
        || staticAttribute(element, 'aria-labelledby') != null
        || hasBoundAttribute(element, 'aria-label')
        || hasBoundAttribute(element, 'aria-labelledby')
      const nonModal = staticAttribute(element, 'data-dialog-mode') === 'non-modal'
      const hasDynamicModalState = hasBoundAttribute(element, 'aria-modal')
      const hasStaticModalState = staticAttribute(element, 'aria-modal') === 'true'
      const hasTopmostA11yState = hasBoundAttribute(element, 'aria-hidden') && hasBoundAttribute(element, 'inert')

      if (!classified) violations.push(`${displayPath}:${line} raw dialog에 공통 surface 변형이 없습니다.`)
      if (!named) violations.push(`${displayPath}:${line} dialog에 접근 가능한 이름이 없습니다.`)
      if (nonModal && (hasDynamicModalState || hasStaticModalState)) {
        violations.push(`${displayPath}:${line} non-modal dialog에는 aria-modal을 적용할 수 없습니다.`)
      } else if (!nonModal && (!hasDynamicModalState || !hasTopmostA11yState || hasStaticModalState)) {
        violations.push(`${displayPath}:${line} modal dialog는 topmost aria-modal/aria-hidden/inert 상태를 동적으로 연결해야 합니다.`)
      }
    }

    if (classes.has('nv-dialog-overlay')) {
      const hasVariant = [...classes].some((name) => /^nv-dialog-overlay--(?:modal|popover|sheet|media)$/.test(name))
      if (!hasVariant) violations.push(`${displayPath}:${line} dialog overlay에 명시적인 변형이 없습니다.`)
    }

    if (element.tag !== 'BaseModal' || displayPath.endsWith('BaseModal.vue')) return
    if (staticAttribute(element, 'layout') === 'immersive') return

    const footer = element.children.find((child) => child.type === NodeTypes.ELEMENT && isFooterTemplate(child))
    const bodyRoots = element.children.filter((child) => child.type === NodeTypes.ELEMENT && child !== footer)
    const firstBodyRoot = bodyRoots[0]
    if (
      firstBodyRoot
      && ['div', 'form', 'fieldset'].includes(firstBodyRoot.tag)
      && [...classTokens(firstBodyRoot)].some((name) => duplicatePaddingToken.test(name))
    ) {
      violations.push(`${displayPath}:${line} standard BaseModal 본문 루트에 중복 패딩이 있습니다.`)
    }

    let legacyActionFound = false
    bodyRoots.forEach((root) => walkElements(root, (descendant) => {
      if (hasLegacyActionContainer(descendant)) legacyActionFound = true
    }))
    if (legacyActionFound) {
      violations.push(`${displayPath}:${line} standard BaseModal의 최종 액션은 #footer에 직접 배치해야 합니다.`)
    }

    footer?.children.forEach((child) => {
      if (child.type !== NodeTypes.ELEMENT) return
      if (!allowedFooterActionTags.has(child.tag)) {
        violations.push(`${displayPath}:${absoluteLine(template, child)} standard BaseModal의 #footer 직접 자식은 액션 요소여야 합니다.`)
      }
    })
  })
}

if (violations.length > 0) {
  console.error('Dialog UI contract guard found violations:')
  violations.forEach((violation) => console.error(`- ${violation}`))
  process.exit(1)
}

console.log('Dialog UI contract guard passed.')
