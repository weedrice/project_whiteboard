import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { usePostContentViewRef } from '@/features/board/posts/detail/usePostContentViewRef'

describe('usePostContentViewRef', () => {
  it('assigns exposed plain and ref-like elements', () => {
    const contentRef = ref<HTMLElement | null>(null)
    const { assignContentRef } = usePostContentViewRef(contentRef)
    const element = document.createElement('article')

    assignContentRef({ element } as unknown as Parameters<typeof assignContentRef>[0])
    expect(contentRef.value).toBe(element)

    const nested = document.createElement('section')
    assignContentRef({ element: { value: nested } } as unknown as Parameters<typeof assignContentRef>[0])
    expect(contentRef.value).toBe(nested)

    assignContentRef(null)
    expect(contentRef.value).toBeNull()
  })
})
