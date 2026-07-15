import { describe, expect, it, vi } from 'vitest'
import { useEmoticonTags } from '../useEmoticonTags'

describe('useEmoticonTags', () => {
  it('adds normalized tags and clears the input', () => {
    const tags = useEmoticonTags()

    tags.tagInput.value = ' #happy '
    tags.addTag()

    expect(tags.tags.value).toEqual(['happy'])
    expect(tags.tagInput.value).toBe('')
  })

  it('does not duplicate existing tags', () => {
    const tags = useEmoticonTags()

    tags.tagInput.value = 'happy'
    tags.addTag()
    tags.tagInput.value = 'happy'
    tags.addTag()

    expect(tags.tags.value).toEqual(['happy'])
  })

  it('reports max tag validation through the caller callback', () => {
    const onMaxTags = vi.fn()
    const tags = useEmoticonTags({ onMaxTags })
    tags.tags.value = Array.from({ length: 10 }, (_, index) => `tag-${index}`)

    tags.tagInput.value = 'overflow'
    tags.addTag()

    expect(onMaxTags).toHaveBeenCalledOnce()
    expect(tags.tags.value).toHaveLength(10)
    expect(tags.tagInput.value).toBe('')
  })

  it('removes tags by stable client id', () => {
    const tags = useEmoticonTags()
    tags.tagInput.value = 'first'
    tags.addTag()
    tags.tagInput.value = 'second'
    tags.addTag()

    tags.removeTag(tags.tagItems.value[0].clientId)

    expect(tags.tags.value).toEqual(['second'])
  })
})
