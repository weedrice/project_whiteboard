import { describe, expect, it, vi } from 'vitest'
import { usePostEditorImageFiles } from '@/features/board/posts/editor/usePostEditorImageFiles'

function createPasteEvent(options: { files?: File[]; html?: string }) {
  return {
    clipboardData: {
      files: options.files ?? [],
      getData: (type: string) => type === 'text/html' ? (options.html ?? '') : '',
    },
    preventDefault: vi.fn(),
  } as unknown as ClipboardEvent
}

describe('usePostEditorImageFiles', () => {
  it('gives pasted image files priority over an accompanying HTML representation', () => {
    const enqueueFiles = vi.fn()
    const insertPreservedHtml = vi.fn(() => true)
    const { onEditorPaste } = usePostEditorImageFiles({ enqueueFiles }, insertPreservedHtml)
    const image = new File(['image'], 'image.png', { type: 'image/png' })
    const event = createPasteEvent({
      files: [image],
      html: '<style>.card{display:grid}</style><p>Clipboard preview</p>',
    })

    onEditorPaste(event)

    expect(enqueueFiles).toHaveBeenCalledWith([image])
    expect(insertPreservedHtml).not.toHaveBeenCalled()
    expect(event.preventDefault).toHaveBeenCalledOnce()
  })

  it('prevents default only when the HTML paste is handled as a preserved block', () => {
    const enqueueFiles = vi.fn()
    const insertPreservedHtml = vi.fn((html: string) => html.includes('<style>'))
    const { onEditorPaste } = usePostEditorImageFiles({ enqueueFiles }, insertPreservedHtml)
    const lossyEvent = createPasteEvent({ html: '<style>.card{display:grid}</style><p>Widget</p>' })
    const supportedEvent = createPasteEvent({ html: '<p><strong>Supported</strong></p>' })

    onEditorPaste(lossyEvent)
    onEditorPaste(supportedEvent)

    expect(lossyEvent.preventDefault).toHaveBeenCalledOnce()
    expect(supportedEvent.preventDefault).not.toHaveBeenCalled()
    expect(enqueueFiles).not.toHaveBeenCalled()
  })
})
