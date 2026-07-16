import { computed, ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'

describe('emoticonQueryKeys', () => {
  it('builds value-based keys without retaining refs', () => {
    const emoticonId = ref(7)

    const key = emoticonQueryKeys.detail(emoticonId.value)

    expect(key).toEqual(['emoticon', 7])
    expect(key).not.toContain(emoticonId)
    expect(emoticonQueryKeys.purchaseStatus(7)).toEqual(['emoticon', 7, 'purchased'])
  })

  it('updates a consumer computed key when its source ref changes', () => {
    const emoticonId = ref(7)
    const key = computed(() => emoticonQueryKeys.detail(emoticonId.value))

    expect(key.value).toEqual(['emoticon', 7])

    emoticonId.value = 8

    expect(key.value).toEqual(['emoticon', 8])
  })
})
