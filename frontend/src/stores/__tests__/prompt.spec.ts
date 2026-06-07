import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePromptStore } from '../prompt'

describe('Prompt Store', () => {
  let store: ReturnType<typeof usePromptStore>

  beforeEach(() => {
    setActivePinia(createPinia())
    store = usePromptStore()
  })

  it('resolves the current prompt with the entered value', async () => {
    const result = store.open('Enter a value')

    store.inputValue = 'noviis'
    store.confirm()

    await expect(result).resolves.toBe('noviis')
    expect(store.isOpen).toBe(false)
  })

  it('resolves empty input as null', async () => {
    const result = store.open('Enter a value')

    store.inputValue = ''
    store.confirm()

    await expect(result).resolves.toBeNull()
  })

  it('resolves pending prompt as null when a new prompt opens', async () => {
    const first = store.open('First prompt')
    const second = store.open('Second prompt')

    await expect(first).resolves.toBeNull()

    store.inputValue = 'second'
    store.confirm()

    await expect(second).resolves.toBe('second')
  })

  it('resolves prompt as null on cancel', async () => {
    const result = store.open('Enter a value')

    store.cancel()

    await expect(result).resolves.toBeNull()
    expect(store.isOpen).toBe(false)
  })
})
