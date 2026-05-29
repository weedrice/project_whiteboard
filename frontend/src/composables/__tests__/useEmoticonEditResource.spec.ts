import { describe, expect, it } from 'vitest'
import { toEmoticonEditFormState } from '../useEmoticonEditResource'
import type { EmoticonMaster } from '@/types/emoticon'

describe('toEmoticonEditFormState', () => {
  it('maps backend emoticon detail fields into edit form state', () => {
    const emoticon: EmoticonMaster = {
      emoticonId: 9,
      name: 'Novi',
      thumbnailUrl: '',
      tags: ['hello'],
      isActive: true,
      creatorId: 2,
      creatorName: 'Creator',
      purchaseCount: 1200,
      images: [
        { imageId: 1, emoticonId: 9, imageUrl: '/one.png', sortOrder: 0 },
        { imageId: 2, emoticonId: 9, imageUrl: '/two.png', sortOrder: 1 }
      ],
      createdAt: '2026-05-01T10:00:00',
      modifiedAt: '2026-05-02T10:00:00'
    }

    const result = toEmoticonEditFormState(emoticon)

    expect(result).toEqual({
      emoticonId: 9,
      name: 'Novi',
      thumbnailUrl: null,
      tags: ['hello'],
      existingImages: [
        { imageId: 1, emoticonId: 9, imageUrl: '/one.png', sortOrder: 0 },
        { imageId: 2, emoticonId: 9, imageUrl: '/two.png', sortOrder: 1 }
      ]
    })
    expect(result.tags).not.toBe(emoticon.tags)
    expect(result.existingImages).not.toBe(emoticon.images)
  })
})
