<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { Sun, Moon } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const currentYear = computed(() => new Date().getFullYear())
const themeStore = useThemeStore()
const commitHash = __COMMIT_HASH__
</script>

<template>
  <footer
    class="bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 mt-auto pb-[max(0.75rem,env(safe-area-inset-bottom))]"
  >
    <div class="max-w-7xl mx-auto py-2 sm:py-3 px-4 sm:px-6 lg:px-8">
      <!-- 모바일: 1.다크모드|Github 2.서비스약관|개인정보 3.저작권 / PC: 저작권(왼쪽) | 다크모드|Github|Commit|약관|개인정보(오른쪽) -->
      <div class="flex flex-col md:flex-row justify-between items-center gap-0.5 md:gap-0">
        <!-- 저작권: 모바일에서는 맨 아래(order-3), PC에서는 왼쪽 -->
        <p
          class="text-sm text-gray-500 dark:text-gray-400 text-center md:text-left order-3 md:order-1 leading-tight"
        >
          &copy; {{ currentYear }} {{ $t('common.appName') }}. {{ $t('common.footer.rights') }}
        </p>

        <!-- 모바일: 2줄(다크모드|Github / 서비스약관|개인정보) / PC: 한 줄 -->
        <div
          class="flex flex-col md:flex-row flex-nowrap justify-center items-center gap-0 md:gap-0 order-1 md:order-2 w-full md:w-auto"
        >
          <!-- 1행: 다크모드 | Github -->
          <div class="flex justify-center items-center gap-x-2 md:contents shrink-0">
            <BaseButton
              @click="themeStore.toggleTheme()"
              variant="ghost"
              size="sm"
              class="min-h-[44px] min-w-[44px] p-0 rounded-full touch-manipulation flex items-center justify-center shrink-0"
              :title="themeStore.isDark ? $t('common.footer.switchToLight') : $t('common.footer.switchToDark')"
            >
              <Sun v-if="themeStore.isDark" class="h-5 w-5" />
              <Moon v-else class="h-5 w-5" />
            </BaseButton>
            <span class="text-gray-300 dark:text-gray-600 md:ml-2" aria-hidden="true">|</span>
            <a
              href="https://github.com/weedrice/project_whiteboard"
              target="_blank"
              rel="noopener noreferrer"
              class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 min-h-[44px] px-3 py-1 md:py-0 inline-flex items-center justify-center rounded-md touch-manipulation active:bg-gray-100 dark:active:bg-gray-700 text-sm"
            >
              <span class="sr-only">{{ $t('common.footer.github') }}</span>
              {{ $t('common.footer.github') }}
            </a>
          </div>
          <span class="text-gray-300 dark:text-gray-600 pl-3 hidden sm:inline md:ml-2" aria-hidden="true">|</span>
          <span
            class="text-xs text-gray-400 pl-3 hidden sm:inline"
            :title="'Commit: ' + commitHash"
          >
            {{ commitHash }}
          </span>
          <span class="text-gray-300 dark:text-gray-600 pl-3 hidden sm:inline" aria-hidden="true">|</span>
          <!-- 2행(모바일): 서비스 약관 | 개인정보 처리방침 (첫째줄과 간격 최소화) -->
          <nav class="flex justify-center items-center gap-x-2 md:contents -mt-1 md:mt-0" aria-label="Footer navigation">
            <router-link
              to="/terms"
              class="text-sm text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300 py-1.5 px-3 md:py-2.5 min-h-[44px] inline-flex items-center justify-center rounded-md touch-manipulation active:bg-gray-100 dark:active:bg-gray-700 md:pl-3"
            >
              서비스 약관
            </router-link>
            <span class="text-gray-300 dark:text-gray-600 md:ml-2" aria-hidden="true">|</span>
            <router-link
              to="/privacy"
              class="text-sm text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300 py-1.5 px-3 md:py-2.5 min-h-[44px] inline-flex items-center justify-center rounded-md touch-manipulation active:bg-gray-100 dark:active:bg-gray-700"
            >
              개인정보 처리방침
            </router-link>
          </nav>
        </div>
      </div>
    </div>
  </footer>
</template>
