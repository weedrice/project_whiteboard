import { describe, expect, it } from 'vitest'
import messages from '../index'

function collectLeafPaths(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object') {
    return [prefix]
  }

  return Object.entries(value)
    .flatMap(([key, child]) => collectLeafPaths(child, prefix ? `${prefix}.${key}` : key))
    .sort()
}

describe('locale messages', () => {
  it('keeps Korean and English message key structures in sync', () => {
    expect(collectLeafPaths(messages.en)).toEqual(collectLeafPaths(messages.ko))
  })
})
