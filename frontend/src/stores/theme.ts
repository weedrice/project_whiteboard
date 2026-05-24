import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { Storage } from '@/utils/storage'

export const useThemeStore = defineStore('theme', () => {
    // Initialize theme: localStorage -> System Preference -> Light
    const storedTheme = Storage.getString('theme')
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches
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
        if (val) {
            document.documentElement.classList.add('dark')
            Storage.setString('theme', 'dark')
        } else {
            document.documentElement.classList.remove('dark')
            Storage.setString('theme', 'light')
        }
    }, { immediate: true })

    return {
        isDark,
        toggleTheme,
        setTheme
    }
})
