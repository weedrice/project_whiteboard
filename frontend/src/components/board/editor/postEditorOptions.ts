export type SlashAction = 'heading' | 'quote' | 'list' | 'link' | 'table' | 'codeBlock' | 'divider' | 'poll'

export const colorPresets = [
  '#000000', '#374151', '#6b7280', '#9ca3af',
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
] as const
export const fontSizes = ['12px', '14px', '16px', '18px', '24px']
export const lineHeights = ['1', '1.25', '1.5', '1.75', '2']
