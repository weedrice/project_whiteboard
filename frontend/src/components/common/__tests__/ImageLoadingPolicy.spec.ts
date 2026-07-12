import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const repeatedImageSources = [
  'src/components/board/BoardCard.vue',
  'src/components/board/SubscribedBoardList.vue',
  'src/components/comment/CommentItem.vue',
  'src/components/common/widgets/EmoticonPickerGrid.vue',
  'src/components/common/widgets/EmoticonPickerImageGrid.vue',
  'src/components/emoticon/EmoticonImageTile.vue',
  'src/components/emoticon/EmoticonListCard.vue',
  'src/components/home/HomePostCard.vue',
  'src/components/layout/RecentBoardsBar.vue',
  'src/components/search/BoardAutocompleteList.vue',
]

describe('repeated image loading policy', () => {
  it.each(repeatedImageSources)('lazily loads and asynchronously decodes images in %s', (path) => {
    const source = readFileSync(resolve(process.cwd(), path), 'utf8')
    const imageTags = source.match(/<img\b[^>]*>/g) ?? []

    expect(imageTags.length).toBeGreaterThan(0)
    imageTags.forEach((tag) => {
      expect(tag).toContain('loading="lazy"')
      expect(tag).toContain('decoding="async"')
    })
  })
})
