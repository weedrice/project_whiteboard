<script setup>
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { Sun, Moon } from 'lucide-vue-next'

const currentYear = computed(() => new Date().getFullYear())
const themeStore = useThemeStore()
</script>

<template>
  <footer class="bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 mt-auto">
    <div class="max-w-7xl mx-auto py-3 px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col md:flex-row justify-between items-center">
        <div class="mb-2 md:mb-0">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            &copy; {{ currentYear }} {{ $t('common.appName') }}. {{ $t('common.footer.rights') }}
          </p>
        </div>
        <div class="flex space-x-6 items-center">
            <button
                @click="themeStore.toggleTheme()"
                class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 focus:outline-none"
                :title="themeStore.isDark ? $t('common.footer.switchToLight') : $t('common.footer.switchToDark')"
            >
                <Sun v-if="themeStore.isDark" class="h-5 w-5" />
                <Moon v-else class="h-5 w-5" />
            </button>
          <a href="https://github.com/weedrice/project_whiteboard" target="_blank" rel="noopener noreferrer" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            <span class="sr-only">{{ $t('common.footer.github') }}</span>
            <!-- Github Icon or similar could go here -->
            {{ $t('common.footer.github') }}
          </a>
          <!-- <a href="#" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            Terms
          </a>
          <a href="#" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            Privacy
          </a> -->
        </div>
      </div>
    </div>
  </footer>
</template>
