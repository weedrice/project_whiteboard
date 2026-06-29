import { describe, expect, it } from 'vitest'
import { colorLabelKeys, colorPresets, fontSizes, lineHeights, slashActions } from '../postEditorOptions'
import { escapeHtmlAttr, escapeHtmlText } from '../postEditorHtml'
import { hasCandidateImageFiles, isCandidateImageFile } from '../postEditorImageFiles'

describe('post editor helper modules', () => {
  it('keeps option arrays aligned for toolbar and slash menu rendering', () => {
    expect(colorPresets).toHaveLength(colorLabelKeys.length)
    expect(slashActions).toEqual(['heading', 'quote', 'list', 'link', 'table', 'codeBlock', 'divider'])
    expect(fontSizes).toContain('16px')
    expect(lineHeights).toContain('1.5')
  })

  it('escapes html for attributes and text insertions', () => {
    expect(escapeHtmlAttr('https://x.test/?a=1&b="<tag>"')).toBe('https://x.test/?a=1&amp;b=&quot;&lt;tag&gt;&quot;')
    expect(escapeHtmlText('Fish & "chips" <b>')).toBe('Fish &amp; &quot;chips&quot; &lt;b&gt;')
  })

  it('detects image candidates by mime type or extension', () => {
    const png = new File(['x'], 'photo.bin', { type: 'image/png' })
    const gifByExtension = new File(['x'], 'motion.GIF', { type: 'application/octet-stream' })
    const text = new File(['x'], 'notes.txt', { type: 'text/plain' })

    expect(isCandidateImageFile(png)).toBe(true)
    expect(isCandidateImageFile(gifByExtension)).toBe(true)
    expect(isCandidateImageFile(text)).toBe(false)
    expect(hasCandidateImageFiles([text, gifByExtension])).toBe(true)
  })
})
