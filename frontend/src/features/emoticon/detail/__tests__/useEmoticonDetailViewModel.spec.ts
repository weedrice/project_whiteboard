import { describe, expect, it } from 'vitest'
import { DEFAULT_EMOTICON_IMAGE_URL } from '@/utils/imageFallback'
import { toEmoticonDetailViewModel } from '../useEmoticonDetailViewModel'
import type { EmoticonMaster } from '@/types/emoticon'

describe('toEmoticonDetailViewModel', () => {
  it('maps backend emoticon detail fields into template-facing values', () => {
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
        { imageId: 1, emoticonId: 9, imageUrl: '', sortOrder: 0 },
        { imageId: 2, emoticonId: 9, imageUrl: '/image.png', sortOrder: 1 }
      ],
      createdAt: '2026-05-01T10:00:00',
      modifiedAt: '2026-05-02T10:00:00'
    }

    expect(toEmoticonDetailViewModel(emoticon)).toEqual({
      emoticonId: 9,
      name: 'Novi',
      isActive: true,
      creatorId: 2,
      creatorDisplayName: 'Creator',
      purchaseCount: 1200,
      purchaseCountText: '1,200',
      createdAt: '2026-05-01T10:00:00',
      tags: ['hello'],
      thumbnailSrc: DEFAULT_EMOTICON_IMAGE_URL,
      imageCount: 2,
      imageItems: [
        { imageId: 1, src: DEFAULT_EMOTICON_IMAGE_URL, alt: 'Novi - 1' },
        { imageId: 2, src: '/image.png', alt: 'Novi - 2' }
      ]
    })
  })
})
