import { describe, expect, it } from 'vitest'
import { stripHtmlToText, truncateWithEllipsis } from '../textExcerpt'

describe('stripHtmlToText', () => {
  it('strips tags and trims text', () => {
    expect(stripHtmlToText('<p>Hello <strong>world</strong></p>')).toBe('Hello world')
  })

  it('can replace tags with spaces and collapse whitespace', () => {
    expect(stripHtmlToText('<p>Hello</p><p>world</p>', {
      tagReplacement: ' ',
      collapseWhitespace: true,
    })).toBe('Hello world')
  })

  it('returns an empty string for missing values', () => {
    expect(stripHtmlToText(null)).toBe('')
    expect(stripHtmlToText(undefined)).toBe('')
  })

  it('decodes entities and keeps comparison text intact', () => {
    expect(stripHtmlToText('<p>Tom &amp; Jerry</p><p>1 &lt; 2</p>', {
      tagReplacement: ' ',
      collapseWhitespace: true,
    })).toBe('Tom & Jerry 1 < 2')
  })

  it('removes script and style contents from excerpts', () => {
    expect(stripHtmlToText('<p>Hello</p><script>alert(1)</script><style>body{}</style>')).toBe('Hello')
  })
})

describe('truncateWithEllipsis', () => {
  it('truncates text longer than the max length', () => {
    expect(truncateWithEllipsis('abcdef', 3)).toBe('abc...')
  })

  it('keeps text shorter than or equal to the max length by default', () => {
    expect(truncateWithEllipsis('abc', 3)).toBe('abc')
  })

  it('can append an ellipsis when text is exactly max length', () => {
    expect(truncateWithEllipsis('abc', 3, { ellipsisAtLength: true })).toBe('abc...')
  })
})
