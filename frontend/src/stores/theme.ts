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

export const useThemeStore = defineStore('theme', () => {
    // Initialize theme: localStorage -> System Preference -> Light
    const storedTheme = Storage.getString('theme')
    const systemDark = getSystemDarkPreference()
    const isDark = ref(storedTheme === 'dark' || (!storedTheme && systemDark))

    async function toggleTheme() {
        isDark.value = !isDark.value
    }

    function setTheme(theme: 'DARK' | 'LIGHT') {
        if (theme === 'DARK') {
            isDark.value = true
        } else {
            isDark.value = false
        }
    }

    watch(isDark, (val) => {
        applyThemeClass(val)
        Storage.setString('theme', val ? 'dark' : 'light')
    }, { immediate: true })

    return {
        isDark,
        toggleTheme,
        setTheme
    }
})
