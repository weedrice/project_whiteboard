import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { Storage } from '@/utils/storage'

function getSystemDarkPreference(): boolean {
    return typeof window !== 'undefined'
        && typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyThemeClass(isDark: boolean) {
    if (typeof document === 'undefined') return
    document.documentElement.classList.toggle('dark', isDark)
}

function isStoredTheme(value: string | null): value is 'dark' | 'light' {
    return value === 'dark' || value === 'light'
}

export const useThemeStore = defineStore('theme', () => {
    // Initialize theme: localStorage -> System Preference -> Light
    const storedTheme = Storage.getString('theme')
    const hasStoredTheme = isStoredTheme(storedTheme)
    const systemDark = getSystemDarkPreference()
    const isDark = ref(hasStoredTheme ? storedTheme === 'dark' : systemDark)

    function persistTheme() {
        Storage.setString('theme', isDark.value ? 'dark' : 'light')
    }

    async function toggleTheme() {
        isDark.value = !isDark.value
        persistTheme()
    }

    function setTheme(theme: 'DARK' | 'LIGHT') {
        if (theme === 'DARK') {
            isDark.value = true
        } else {
            isDark.value = false
        }
        persistTheme()
    }

    watch(isDark, (val) => {
        applyThemeClass(val)
    }, { immediate: true, flush: 'sync' })

    return {
        isDark,
        toggleTheme,
        setTheme
    }
})
