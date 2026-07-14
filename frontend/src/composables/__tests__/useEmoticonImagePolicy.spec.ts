import { describe, expect, it } from 'vitest'
import {
  DEFAULT_EMOTICON_IMAGE_MAX_COUNT,
  resolveEmoticonImageMaxCount,
} from '@/features/emoticon/form/useEmoticonImagePolicy'

describe('resolveEmoticonImageMaxCount', () => {
  it.each([undefined, null, '', 'NaN', '0', '101', '1.5'])(
    'falls back to the default for %s',
    (value) => {
      expect(resolveEmoticonImageMaxCount(value)).toBe(DEFAULT_EMOTICON_IMAGE_MAX_COUNT)
    }
  )

  it.each([['1', 1], ['20', 20], ['100', 100]])(
    'accepts %s within the supported range',
    (value, expected) => {
      expect(resolveEmoticonImageMaxCount(value)).toBe(expected)
    }
  )
})
