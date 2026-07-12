import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('post editor popover responsive styles', () => {
  it('keeps editor dialogs within the viewport safe area', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/components/board/editor/editor-popovers.css'), 'utf8')

    expect(css).toContain('width: min(460px, calc(100vw - 24px))')
    expect(css).toContain('min-width: 0')
    expect(css).toContain('max-height: calc(100dvh - 24px)')
    expect(css).not.toContain('min-width: 320px')
  })

  it('keeps the video dialog within the viewport safe area', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/components/board/PostVideoUrlPopover.vue'), 'utf8')

    expect(source).toContain('width: min(420px, calc(100vw - 24px))')
    expect(source).toContain('max-height: calc(100dvh - 24px)')
    expect(source).not.toContain('min-width: 320px')
  })

  it('applies the shared modal focus contract to the color panel', () => {
    const composable = readFileSync(resolve(process.cwd(), 'src/features/board/posts/editor/usePostEditorPopovers.ts'), 'utf8')
    const panels = readFileSync(resolve(process.cwd(), 'src/components/board/editor/PostEditorFloatingPanels.vue'), 'utf8')

    expect(composable).toContain('usePopoverFocus(colorPanelRef, showColorPanel)')
    expect(panels).toMatch(/id="editor-color-dialog"[\s\S]*aria-modal="true"/)
  })
})
