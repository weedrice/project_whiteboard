import { readdir, readFile } from 'node:fs/promises'
import { dirname, extname, join, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { NodeTypes } from '@vue/compiler-dom'
import { parse as parseSfc } from '@vue/compiler-sfc'

const invokedDirectly = Boolean(
  process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href,
)
const rootDir = invokedDirectly ? resolve(dirname(process.argv[1]), '..') : process.cwd()
const srcDir = join(rootDir, 'src')
const retiredClasses = new Set([
  'card',
  'card-header',
  'card-body',
  'table-container',
  'table-base',
  'table-head',
  'table-th',
  'table-body',
  'table-row',
  'table-td',
  'badge',
  'badge-gray',
  'badge-red',
  'list-group',
  'list-group-item',
])
const nativeClassifications = new Set(['color', 'file', 'radio', 'specialized'])
const nativeElements = new Set(['input', 'select', 'textarea', 'table'])
const scannedExtensions = new Set(['.css', '.scss', '.ts', '.vue'])
const primitiveImplementationFiles = new Set([
  'src/components/common/ui/BaseCheckbox.vue',
  'src/components/common/ui/BaseInput.vue',
  'src/components/common/ui/BaseSelect.vue',
  'src/components/common/ui/BaseTable.vue',
  'src/components/common/ui/BaseTextarea.vue',
])

async function collectSourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectSourceFiles(path)
    return scannedExtensions.has(extname(entry.name)) ? [path] : []
  }))
  return files.flat()
}

function getStaticAttribute(node, name) {
  return node.props.find((prop) => prop.type === NodeTypes.ATTRIBUTE && prop.name === name)
}

function getBoundAttributeExpression(node, name) {
  const directive = node.props.find((prop) => (
    prop.type === NodeTypes.DIRECTIVE
    && prop.name === 'bind'
    && prop.arg?.type === NodeTypes.SIMPLE_EXPRESSION
    && prop.arg.content === name
  ))
  return directive?.type === NodeTypes.DIRECTIVE ? directive.exp?.loc.source ?? '' : ''
}

function visit(node, callback) {
  if (node.type === NodeTypes.ELEMENT) callback(node)
  if ('children' in node && Array.isArray(node.children)) {
    node.children.forEach((child) => visit(child, callback))
  }
  if (node.type === NodeTypes.IF) {
    node.branches.forEach((branch) => visit(branch, callback))
  }
  if (node.type === NodeTypes.FOR) visit(node.children, callback)
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function findRetiredClasses(value) {
  return [...retiredClasses].filter((className) => (
    new RegExp(`(^|[^\\w-])${escapeRegExp(className)}(?=$|[^\\w-])`).test(value)
  ))
}

export function findRetiredBoundClasses(expression) {
  const classBearingParts = []
  for (const match of expression.matchAll(/(['"`])((?:\\.|(?!\1).)*)\1/g)) {
    classBearingParts.push(match[2])
  }
  for (const match of expression.matchAll(/(?:^|[{,])\s*([A-Za-z_$][\w$-]*)\s*:/g)) {
    classBearingParts.push(match[1])
  }
  if (/^[A-Za-z_$][\w$-]*$/.test(expression.trim())) {
    classBearingParts.push(expression.trim())
  }
  return [...new Set(classBearingParts.flatMap(findRetiredClasses))]
}

export function findRetiredCssSelectors(value) {
  return [...retiredClasses].filter((className) => (
    new RegExp(`(^|[^\\w-])\\.${escapeRegExp(className)}(?=$|[^\\w-])`, 'm').test(value)
  ))
}

export async function checkUiPrimitiveContracts(sourceDirectory = srcDir) {
  const violations = []
  for (const file of await collectSourceFiles(sourceDirectory)) {
    const source = await readFile(file, 'utf8')
    const displayPath = relative(rootDir, file).replaceAll('\\', '/')

    if (source.includes('legacy-components.css')) {
      violations.push(`${displayPath}: retired legacy-components.css reference`)
    }

    if (extname(file) === '.css' || extname(file) === '.scss') {
      const retiredSelectors = findRetiredCssSelectors(source)
      if (retiredSelectors.length > 0) {
        violations.push(`${displayPath}: retired UI selector: ${retiredSelectors.join(', ')}`)
      }
    }

    if (extname(file) !== '.vue') continue

    const parsed = parseSfc(source, { filename: file })
    if (parsed.errors.length > 0) {
      violations.push(`${displayPath}: SFC parse failed: ${parsed.errors.join(', ')}`)
      continue
    }

    for (const style of parsed.descriptor.styles) {
      const retiredSelectors = findRetiredCssSelectors(style.content)
      if (retiredSelectors.length > 0) {
        violations.push(`${displayPath}: retired UI selector: ${retiredSelectors.join(', ')}`)
      }
    }

    const ast = parsed.descriptor.template?.ast
    if (!ast) continue

    visit(ast, (node) => {
      const line = node.loc.start.line
      if (node.tag === 'button' && !getStaticAttribute(node, 'type')) {
        violations.push(`${displayPath}:${line} raw button requires a static type attribute`)
      }

      if (nativeElements.has(node.tag) && !primitiveImplementationFiles.has(displayPath)) {
        const classification = getStaticAttribute(node, 'data-ui-native')?.value?.content
        if (!classification || !nativeClassifications.has(classification)) {
          violations.push(`${displayPath}:${line} raw <${node.tag}> requires data-ui-native="file|color|radio|specialized"`)
        }
      }

      const staticClasses = getStaticAttribute(node, 'class')?.value?.content ?? ''
      const boundClasses = getBoundAttributeExpression(node, 'class')
      const retired = new Set([
        ...findRetiredClasses(staticClasses),
        ...findRetiredBoundClasses(boundClasses),
      ])
      if (retired.size > 0) {
        violations.push(`${displayPath}:${line} retired UI class: ${[...retired].join(', ')}`)
      }
    })
  }
  return violations
}

if (invokedDirectly) {
  const violations = await checkUiPrimitiveContracts()
  if (violations.length > 0) {
    console.error('UI primitive contract check failed.')
    violations.forEach((violation) => console.error(`- ${violation}`))
    process.exit(1)
  }

  console.log('UI primitive contract check passed.')
}
