import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
    // Initialize theme: localStorage -> System Preference -> Light
    const storedTheme = localStorage.getItem('theme')
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
            localStorage.setItem('theme', 'dark')
        } else {
            document.documentElement.classList.remove('dark')
            localStorage.setItem('theme', 'light')
        }
    }, { immediate: true })

    return {
        isDark,
        toggleTheme,
        setTheme
    }
})
