const SHORT_COMMIT_HASH_LENGTH = 9
const FULL_COMMIT_HASH_PATTERN = /^[0-9a-f]{10,}$/i

export function toShortCommitHash(commitHash: string): string {
    if (!FULL_COMMIT_HASH_PATTERN.test(commitHash)) {
        return commitHash
    }

    return commitHash.slice(0, SHORT_COMMIT_HASH_LENGTH)
}
