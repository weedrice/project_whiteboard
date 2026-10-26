import { effectScope, nextTick, ref } from 'vue'
import { createPinia, disposePinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useThemeStore } from '@/stores/theme'
import { createDeferred } from '@/test/async'
import type { ApiResponse, UserSettings } from '@/types'
import { useUserSettingsForm } from '../useUserSettingsForm'

let pinia: ReturnType<typeof createPinia>
let scope: ReturnType<typeof effectScope>
beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
  scope = effectScope()
})
afterEach(() => {
  scope.stop()
  disposePinia(pinia)
  setActivePinia(undefined)
  localStorage.removeItem('theme')
  document.documentElement.classList.remove('dark')
})

function setup() {
  const theme = useThemeStore(pinia)
  theme.setTheme('LIGHT')
  const settingsData = ref<UserSettings>({ theme: 'LIGHT', language: 'ko', timezone: 'AUTO', hideNsfw: true, pushEnabled: false })
  const pending = createDeferred<ApiResponse<UserSettings>>()
  const updateSettings = vi.fn().mockReturnValueOnce(pending.promise).mockImplementation(async payload => ({ success: true, data: { ...settingsData.value, ...payload } }))
  const generation = ref(1)
  const model = scope.run(() => useUserSettingsForm({
    settingsData, isSaving: ref(false), themeIsDark: () => theme.isDark,
    updateSettings, setTheme: theme.setTheme, getSessionGeneration: () => generation.value, t: key => key,
  }))!
  return { theme, model, settingsData, pending, updateSettings, generation }
}

describe('settings theme draft during a save', () => {
  it.each([false, true])('keeps a newer theme selection when the saved query refreshes before completion: %s', async (refetchFirst) => {
    const { theme, model, settingsData, pending, updateSettings } = setup()
    model.form.theme = 'DARK'
    const saving = model.save()
    await nextTick()
    model.form.theme = 'LIGHT'
    if (refetchFirst) {
      settingsData.value = { ...settingsData.value, theme: 'DARK' }
      await nextTick()
    }
    pending.resolve({ success: true, data: { ...settingsData.value, theme: 'DARK' } })
    await saving
    await nextTick()
    expect(theme.isDark).toBe(true)
    expect(model.form.theme).toBe('LIGHT')
    expect(model.isDirty.value).toBe(true)
    expect(model.canSave.value).toBe(true)
    await model.save()
    await nextTick()
    expect(updateSettings).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'LIGHT' }))
    expect(theme.isDark).toBe(false)
    expect(model.isDirty.value).toBe(false)
  })

  it('still follows the toolbar theme toggle outside a save', async () => {
    const { theme, model } = setup()
    await theme.toggleTheme()
    await nextTick()
    expect(model.form.theme).toBe('DARK')
    expect(model.isDirty.value).toBe(true)
  })

  it('keeps a failed save editable without applying its submitted theme', async () => {
    const { theme, model, pending } = setup()
    model.form.theme = 'DARK'
    const saving = model.save()
    pending.reject(new Error('save failed'))
    await saving
    expect(theme.isDark).toBe(false)
    expect(model.form.theme).toBe('DARK')
    expect(model.canSave.value).toBe(true)
  })

  it('does not apply an old session completion to the new settings draft', async () => {
    const { theme, model, pending, generation, settingsData } = setup()
    model.form.theme = 'DARK'
    const saving = model.save()
    generation.value++
    settingsData.value = { ...settingsData.value, language: 'en' }
    await nextTick()
    pending.resolve({ success: true, data: { ...settingsData.value, theme: 'DARK' } })
    await saving
    expect(theme.isDark).toBe(false)
    expect(model.form.theme).toBe('LIGHT')
    expect(model.form.language).toBe('en')
    expect(model.isDirty.value).toBe(false)
  })
})
