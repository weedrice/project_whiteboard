import { describe, expect, it } from 'vitest'
import { toShortCommitHash } from '../commitHash'

describe('toShortCommitHash', () => {
    it('shortens a full commit hash to the repository short hash length', () => {
        expect(toShortCommitHash('73a3d339e35d105b203c648e546908e5ada59d30')).toBe('73a3d339e')
    })

    it.each(['1c711639e', 'unknown', 'docker'])('keeps an already short or non-hash value unchanged: %s', value => {
        expect(toShortCommitHash(value)).toBe(value)
    })
})
