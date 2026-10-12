export type SlashAction = 'heading' | 'quote' | 'list' | 'link' | 'table' | 'codeBlock' | 'divider' | 'poll'

export const DEFAULT_EDITOR_TEXT_COLOR = '#000000'

export const colorPresets = [
  DEFAULT_EDITOR_TEXT_COLOR, '#374151', '#6b7280', '#9ca3af',
  '#ef4444', '#f97316', '#eab308', '#22c55e',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6',
  '#ffffff', '#1f2937', '#4b5563', '#d1d5db',
]

export const colorLabelKeys = [
  'black', 'gray', 'muted', 'lightGray',
  'red', 'orange', 'yellow', 'green',
  'blue', 'purple', 'pink', 'teal',
  'white', 'dark', 'slate', 'paleGray',
]

export const slashActions: SlashAction[] = ['heading', 'quote', 'list', 'link', 'table', 'codeBlock', 'divider', 'poll']

export const codeBlockLanguages = [
  '',
  'text',
  'javascript',
  'typescript',
  'java',
  'kotlin',
  'python',
  'sql',
  'json',
  'html',
  'css',
  'bash',
  'c',
  'cpp',
  'csharp',
  'go',
  'rust',
  'swift',
  'php',
  'ruby',
  'yaml',
  'xml',
  'markdown',
] as const

const codeLanguageLabels: Record<string, string> = {
  text: 'Plain text', plaintext: 'Plain text',
  javascript: 'JavaScript', js: 'JavaScript', typescript: 'TypeScript', ts: 'TypeScript',
  java: 'Java', kotlin: 'Kotlin', python: 'Python', py: 'Python', sql: 'SQL', json: 'JSON',
  html: 'HTML', css: 'CSS', bash: 'Bash', sh: 'Shell', shell: 'Shell',
  c: 'C', cpp: 'C++', 'c++': 'C++', csharp: 'C#', cs: 'C#', 'c#': 'C#',
  go: 'Go', rust: 'Rust', swift: 'Swift', php: 'PHP', ruby: 'Ruby',
  yaml: 'YAML', yml: 'YAML', xml: 'XML', markdown: 'Markdown', md: 'Markdown',
}

export function codeLanguageLabel(language: string): string {
  return Object.hasOwn(codeLanguageLabels, language.toLowerCase())
    ? codeLanguageLabels[language.toLowerCase()]
    : language
}
export const fontSizes = ['12px', '14px', '16px', '18px', '24px']
export const lineHeights = ['1', '1.25', '1.5', '1.75', '2']
