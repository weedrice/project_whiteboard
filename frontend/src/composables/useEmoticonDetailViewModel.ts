import { computed, type Ref } from 'vue'
import type { EmoticonMaster } from '@/types/emoticon'
import { DEFAULT_EMOTICON_IMAGE_URL } from '@/utils/imageFallback'
import { formatInteger } from '@/utils/numberFormat'

export interface EmoticonDetailImageViewModel {
  imageId: number
  src: string
  alt: string
}

export interface EmoticonDetailViewModel {
  emoticonId: number
  name: string
  isActive: boolean
  creatorId?: number
  creatorDisplayName: string
  purchaseCount: number
  purchaseCountText: string
  createdAt: string
  tags: string[]
  thumbnailSrc: string
  imageItems: EmoticonDetailImageViewModel[]
  imageCount: number
}

export function toEmoticonDetailViewModel(emoticon: EmoticonMaster): EmoticonDetailViewModel {
  const imageItems = (emoticon.images ?? []).map((image) => ({
    imageId: image.imageId,
    src: image.imageUrl || DEFAULT_EMOTICON_IMAGE_URL,
    alt: `${emoticon.name} - ${(image.sortOrder ?? 0) + 1}`
  }))
  const purchaseCount = emoticon.purchaseCount ?? 0

  return {
    emoticonId: emoticon.emoticonId,
    name: emoticon.name,
    isActive: emoticon.isActive,
    creatorId: emoticon.creatorId,
    creatorDisplayName: emoticon.creatorName ?? '',
    purchaseCount,
    purchaseCountText: formatInteger(purchaseCount),
    createdAt: emoticon.createdAt,
    tags: emoticon.tags ?? [],
    thumbnailSrc: emoticon.thumbnailUrl || DEFAULT_EMOTICON_IMAGE_URL,
    imageItems,
    imageCount: imageItems.length
  }
}

export function useEmoticonDetailViewModel(emoticon: Ref<EmoticonMaster | undefined>) {
  return computed(() => (emoticon.value ? toEmoticonDetailViewModel(emoticon.value) : null))
}
