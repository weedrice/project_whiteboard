import { describe, expect, it } from 'vitest'
import { useEmoticonTagItems } from '../useEmoticonTagItems'

describe('useEmoticonTagItems', () => {
  it('maps tag values to stable client ids', () => {
    const { tagItems, tags, addTagItem } = useEmoticonTagItems()

    addTagItem('cute')
    addTagItem('fun')

    expect(tags.value).toEqual(['cute', 'fun'])
    expect(tagItems.value).toEqual([
      { clientId: 'emoticon-tag-1', value: 'cute' },
      { clientId: 'emoticon-tag-2', value: 'fun' },
    ])
  })

  it('preserves remaining duplicate tag identities when one item is removed', () => {
    const { tagItems, tags, removeTagItem } = useEmoticonTagItems()

    tags.value = ['same', 'same', 'tail']
    const firstTagId = tagItems.value[0].clientId
    const secondTagId = tagItems.value[1].clientId

    removeTagItem(secondTagId)

    expect(tags.value).toEqual(['same', 'tail'])
    expect(tagItems.value[0].clientId).toBe(firstTagId)
    expect(tagItems.value[1].value).toBe('tail')
  })

  it('uses a custom id prefix when provided', () => {
    const { tagItems, addTagItem } = useEmoticonTagItems('custom-tag')

    addTagItem('custom')

    expect(tagItems.value[0].clientId).toBe('custom-tag-1')
  })
})
