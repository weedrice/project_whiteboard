import { describe, expect, it } from 'vitest'
import { highlightCodeBlocksInDocument } from '../codeHighlighting'

describe('code language detection', () => {
  it.each(['c++', 'objective-c', 'js', 'text'])('preserves the complete explicit language %s', (language) => {
    const source = 'std::vector<int> values;'
    const doc = new DOMParser().parseFromString(`<pre><code class="not-language-python language-${language}">std::vector&lt;int&gt; values;</code></pre>`, 'text/html')
    highlightCodeBlocksInDocument(doc)
    expect(doc.querySelector('.nv-code-block-language')?.textContent).toBe(language)
    expect(doc.querySelector('code')?.textContent).toBe(source)
    expect(Array.from(doc.querySelector('code')!.classList).filter(token => token.startsWith('language-'))).toEqual([`language-${language}`])
    highlightCodeBlocksInDocument(doc)
    expect(doc.querySelectorAll('.nv-code-block')).toHaveLength(1)
  })
})
