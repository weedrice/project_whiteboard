import { readdir, readFile } from 'node:fs/promises'
import { basename, dirname, extname, join, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { NodeTypes } from '@vue/compiler-dom'
import { parse as parseSfc } from '@vue/compiler-sfc'
import ts from 'typescript'

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
const nativeClassifications = new Set(['choice', 'color', 'file', 'radio', 'specialized'])
const nativeElements = new Set(['input', 'select', 'textarea', 'table'])
const sharedPrimitiveClasses = new Set([
  'btn-primary',
  'btn-secondary',
  'btn-danger',
  'btn-ghost',
  'btn-sm',
  'input-base',
])
const scannedExtensions = new Set(['.css', '.scss', '.ts', '.vue'])
const primitiveImplementationFiles = new Set([
  'src/components/common/ui/BaseCheckbox.vue',
  'src/components/common/ui/BaseInput.vue',
  'src/components/common/ui/BaseSelect.vue',
  'src/components/common/ui/BaseTable.vue',
  'src/components/common/ui/BaseTextarea.vue',
])
const primitiveClassOwnerFiles = new Set([
  ...primitiveImplementationFiles,
  'src/components/common/ui/BaseButton.vue',
  'src/components/common/ui/Pagination.vue',
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

function getSpreadBindingExpressions(node) {
  return node.props
    .filter((prop) => (
      prop.type === NodeTypes.DIRECTIVE
      && prop.name === 'bind'
      && !prop.arg
    ))
    .map((prop) => prop.type === NodeTypes.DIRECTIVE ? prop.exp?.loc.source ?? '' : '')
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
  return findMatchingClasses(value, retiredClasses)
}

function findMatchingClasses(value, classNames) {
  return [...classNames].filter((className) => (
    new RegExp(`(^|[^\\w-])${escapeRegExp(className)}(?=$|[^\\w-])`).test(value)
  ))
}

function isNativeClassificationValid(node, classification) {
  if (classification === 'specialized') return true
  if (node.tag !== 'input') return false

  const staticType = getStaticAttribute(node, 'type')?.value?.content?.toLowerCase() ?? ''
  const boundType = getBoundAttributeExpression(node, 'type')
  if (classification === 'file' || classification === 'color' || classification === 'radio') {
    return staticType === classification
  }
  if (classification === 'choice') {
    return ['checkbox', 'radio'].includes(staticType)
      || (/['"]checkbox['"]/.test(boundType) && /['"]radio['"]/.test(boundType))
  }
  return false
}

function getPropertyName(node) {
  if (!node) return ''
  if (ts.isIdentifier(node) || ts.isStringLiteralLike(node) || ts.isNumericLiteral(node)) {
    return node.text
  }
  return ''
}

function isClassBindingName(name) {
  return /class(?:es|name|names)?$/i.test(name)
}

function createScriptAnalysis(value, fileName = 'source.ts') {
  const sourceFile = ts.createSourceFile(
    fileName,
    value,
    ts.ScriptTarget.Latest,
    true,
    fileName.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  )
  const bindings = new Map()

  const collectBindings = (node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
      bindings.set(node.name.text, node.initializer)
    }
    ts.forEachChild(node, collectBindings)
  }
  collectBindings(sourceFile)

  return { sourceFile, bindings, expressionAnalyses: new Map() }
}

function collectClassExpressionMatches(node, classNames, analysis, matches, resolving = new Set()) {
  if (!node) return

  if (ts.isStringLiteralLike(node)) {
    findMatchingClasses(node.text, classNames).forEach((className) => matches.add(className))
    return
  }

  if (ts.isIdentifier(node) && analysis.bindings.has(node.text) && !resolving.has(node.text)) {
    const nextResolving = new Set(resolving).add(node.text)
    collectClassExpressionMatches(analysis.bindings.get(node.text), classNames, analysis, matches, nextResolving)
    return
  }

  if (
    (ts.isPropertyAssignment(node) || ts.isShorthandPropertyAssignment(node) || ts.isMethodDeclaration(node))
    && ts.isObjectLiteralExpression(node.parent)
  ) {
    const propertyName = getPropertyName(node.name)
    findMatchingClasses(propertyName, classNames).forEach((className) => matches.add(className))
  }

  ts.forEachChild(node, (child) => (
    collectClassExpressionMatches(child, classNames, analysis, matches, resolving)
  ))
}

function getExpressionAnalysis(expression, analysis, spread = false) {
  const bindingName = spread ? '__uiSpread' : '__uiExpression'
  const cacheKey = `${bindingName}:${expression}`
  if (!analysis.expressionAnalyses.has(cacheKey)) {
    const expressionAnalysis = createScriptAnalysis(
      `const ${bindingName} = (${expression})`,
      spread ? 'spread-expression.ts' : 'expression.ts',
    )
    analysis.expressionAnalyses.set(cacheKey, {
      sourceFile: expressionAnalysis.sourceFile,
      bindings: new Map([...analysis.bindings, ...expressionAnalysis.bindings]),
      expressionNode: expressionAnalysis.bindings.get(bindingName),
    })
  }
  return analysis.expressionAnalyses.get(cacheKey)
}

function findExpressionClasses(expression, classNames, analysis) {
  if (!expression.trim()) return []
  const combinedAnalysis = getExpressionAnalysis(expression, analysis)
  const matches = new Set()
  collectClassExpressionMatches(combinedAnalysis.expressionNode, classNames, combinedAnalysis, matches)
  return [...matches]
}

function collectSpreadClassMatches(node, classNames, analysis, matches, resolving = new Set()) {
  if (!node) return

  if (ts.isIdentifier(node) && analysis.bindings.has(node.text) && !resolving.has(node.text)) {
    const nextResolving = new Set(resolving).add(node.text)
    collectSpreadClassMatches(analysis.bindings.get(node.text), classNames, analysis, matches, nextResolving)
    return
  }

  if (ts.isObjectLiteralExpression(node)) {
    for (const property of node.properties) {
      if (ts.isSpreadAssignment(property)) {
        collectSpreadClassMatches(property.expression, classNames, analysis, matches, resolving)
        continue
      }
      if (
        (ts.isPropertyAssignment(property) || ts.isShorthandPropertyAssignment(property))
        && getPropertyName(property.name) === 'class'
      ) {
        collectClassExpressionMatches(
          ts.isPropertyAssignment(property) ? property.initializer : property.name,
          classNames,
          analysis,
          matches,
          resolving,
        )
      }
    }
    return
  }

  ts.forEachChild(node, (child) => (
    collectSpreadClassMatches(child, classNames, analysis, matches, resolving)
  ))
}

function findSpreadExpressionClasses(expression, classNames, analysis) {
  if (!expression.trim()) return []
  const combinedAnalysis = getExpressionAnalysis(expression, analysis, true)
  const matches = new Set()
  collectSpreadClassMatches(combinedAnalysis.expressionNode, classNames, combinedAnalysis, matches)
  return [...matches]
}

function findScriptClassLiterals(analysis, classNames) {
  const matches = new Set()

  const visitScript = (node) => {
    if (
      ts.isVariableDeclaration(node)
      && ts.isIdentifier(node.name)
      && isClassBindingName(node.name.text)
      && node.initializer
    ) {
      collectClassExpressionMatches(node.initializer, classNames, analysis, matches)
    }
    if (
      ts.isPropertyAssignment(node)
      && getPropertyName(node.name) === 'class'
    ) {
      collectClassExpressionMatches(node.initializer, classNames, analysis, matches)
    }
    ts.forEachChild(node, visitScript)
  }
  visitScript(analysis.sourceFile)

  return [...matches]
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

    if (basename(file) === 'legacy-components.css') {
      violations.push(`${displayPath}: retired legacy-components.css file`)
    } else if (source.includes('legacy-components.css')) {
      violations.push(`${displayPath}: retired legacy-components.css reference`)
    }

    if (extname(file) === '.css' || extname(file) === '.scss') {
      const retiredSelectors = findRetiredCssSelectors(source)
      if (retiredSelectors.length > 0) {
        violations.push(`${displayPath}: retired UI selector: ${retiredSelectors.join(', ')}`)
      }
    }

    if (extname(file) === '.ts') {
      const scriptAnalysis = createScriptAnalysis(source, file)
      const retiredScriptClasses = findScriptClassLiterals(scriptAnalysis, retiredClasses)
      if (retiredScriptClasses.length > 0) {
        violations.push(`${displayPath}: retired UI class in script: ${retiredScriptClasses.join(', ')}`)
      }
      if (!primitiveClassOwnerFiles.has(displayPath)) {
        const sharedScriptClasses = findScriptClassLiterals(scriptAnalysis, sharedPrimitiveClasses)
        if (sharedScriptClasses.length > 0) {
          violations.push(`${displayPath}: shared primitive class in script: ${sharedScriptClasses.join(', ')}`)
        }
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

    const scriptSource = [parsed.descriptor.script?.content, parsed.descriptor.scriptSetup?.content]
      .filter(Boolean)
      .join('\n')
    const scriptAnalysis = createScriptAnalysis(scriptSource, file)
    const retiredScriptClasses = findScriptClassLiterals(scriptAnalysis, retiredClasses)
    if (retiredScriptClasses.length > 0) {
      violations.push(`${displayPath}: retired UI class in script: ${retiredScriptClasses.join(', ')}`)
    }
    if (!primitiveClassOwnerFiles.has(displayPath)) {
      const sharedScriptClasses = findScriptClassLiterals(scriptAnalysis, sharedPrimitiveClasses)
      if (sharedScriptClasses.length > 0) {
        violations.push(`${displayPath}: shared primitive class in script: ${sharedScriptClasses.join(', ')}`)
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
          violations.push(`${displayPath}:${line} raw <${node.tag}> requires data-ui-native="file|color|radio|choice|specialized"`)
        } else if (!isNativeClassificationValid(node, classification)) {
          violations.push(`${displayPath}:${line} data-ui-native="${classification}" does not match <${node.tag}> type`)
        }
      }

      const staticClasses = getStaticAttribute(node, 'class')?.value?.content ?? ''
      const boundClasses = getBoundAttributeExpression(node, 'class')
      const spreadBindings = getSpreadBindingExpressions(node)
      const spreadClasses = spreadBindings.flatMap((expression) => (
        findSpreadExpressionClasses(expression, retiredClasses, scriptAnalysis)
      ))
      const retired = new Set([
        ...findRetiredClasses(staticClasses),
        ...findExpressionClasses(boundClasses, retiredClasses, scriptAnalysis),
        ...spreadClasses,
      ])
      if (retired.size > 0) {
        violations.push(`${displayPath}:${line} retired UI class: ${[...retired].join(', ')}`)
      }

      if (!primitiveClassOwnerFiles.has(displayPath)) {
        const shared = new Set([
          ...findMatchingClasses(staticClasses, sharedPrimitiveClasses),
          ...findExpressionClasses(boundClasses, sharedPrimitiveClasses, scriptAnalysis),
          ...spreadBindings.flatMap((expression) => (
            findSpreadExpressionClasses(expression, sharedPrimitiveClasses, scriptAnalysis)
          )),
        ])
        if (shared.size > 0) {
          violations.push(`${displayPath}:${line} shared primitive class must be owned by a Base component: ${[...shared].join(', ')}`)
        }
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
