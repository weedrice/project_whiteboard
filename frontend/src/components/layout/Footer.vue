<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import { useThemePreference } from '@/composables/useThemePreference'
import { Sun, Moon } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'

const COPYRIGHT_YEAR = 2026
const themeStore = useThemeStore()
const { toggleTheme } = useThemePreference()
const commitHash = __COMMIT_HASH__
</script>

<template>
  <footer class="nv-footer mt-auto">
    <div class="max-w-7xl mx-auto py-1.5 sm:py-2 px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col md:flex-row justify-between items-center gap-0.5 md:gap-0">
        <p class="nv-footer-copy text-sm text-center md:text-left order-3 md:order-1 leading-tight">
          &copy; {{ COPYRIGHT_YEAR }} {{ $t('common.appName') }}. {{ $t('common.footer.rights') }}
        </p>

        <div
          class="flex flex-col md:flex-row flex-nowrap justify-center items-center gap-0 md:gap-0 order-1 md:order-2 w-full md:w-auto"
        >
          <div class="flex justify-center items-center gap-x-2 md:contents shrink-0">
            <BaseButton
              @click="toggleTheme"
              variant="ghost"
              size="sm"
              class="min-h-11 min-w-11 p-0 rounded-full touch-manipulation flex items-center justify-center shrink-0 sm:min-h-9 sm:min-w-9"
              :title="themeStore.isDark ? $t('common.footer.switchToLight') : $t('common.footer.switchToDark')"
              :aria-label="themeStore.isDark ? $t('common.footer.switchToLight') : $t('common.footer.switchToDark')"
            >
              <Sun v-if="themeStore.isDark" class="h-5 w-5" aria-hidden="true" />
              <Moon v-else class="h-5 w-5" aria-hidden="true" />
            </BaseButton>
          </div>

          <span class="nv-footer-separator pl-3 hidden sm:inline md:ml-2" aria-hidden="true">|</span>
          <span class="nv-footer-commit text-xs pl-3 hidden sm:inline" :title="'Commit: ' + commitHash">
            {{ commitHash }}
          </span>
          <span class="nv-footer-separator pl-3 hidden sm:inline" aria-hidden="true">|</span>

          <nav class="flex justify-center items-center gap-x-1 md:contents -mt-0.5 md:mt-0" :aria-label="$t('layout.a11y.footerNavigation')">
            <router-link
              to="/inquiries/new"
              class="nv-footer-link inline-flex min-h-[44px] items-center justify-center rounded-md px-2 py-1 text-sm touch-manipulation sm:min-h-[36px] md:py-1.5 md:pl-3"
            >
              {{ $t('common.footer.contact') }}
            </router-link>
            <span class="nv-footer-separator md:ml-2" aria-hidden="true">|</span>
            <router-link
              to="/terms"
              class="nv-footer-link inline-flex min-h-[44px] items-center justify-center rounded-md px-2 py-1 text-sm touch-manipulation sm:min-h-[36px] md:py-1.5 md:pl-3"
            >
              {{ $t('common.footer.termsOfService') }}
            </router-link>
            <span class="nv-footer-separator md:ml-2" aria-hidden="true">|</span>
            <router-link
              to="/privacy"
              class="nv-footer-link inline-flex min-h-[44px] items-center justify-center rounded-md px-2 py-1 text-sm touch-manipulation sm:min-h-[36px] md:py-1.5"
            >
              {{ $t('common.footer.privacyPolicy') }}
            </router-link>
          </nav>
        </div>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.nv-footer {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border-top: 1px solid var(--nv-line);
  padding-bottom: calc(var(--nv-bottom-nav-height) + env(safe-area-inset-bottom) + 0.5rem);
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

@media (min-width: 640px) {
  .nv-footer {
    padding-bottom: max(0.5rem, env(safe-area-inset-bottom));
  }
}

.nv-footer-copy,
.nv-footer-commit {
  color: var(--nv-muted);
}

.nv-footer-separator {
  color: color-mix(in srgb, var(--nv-line) 92%, transparent);
}

.nv-footer-link {
  color: var(--nv-ink-soft);
  transition: color 0.2s ease, background-color 0.2s ease;
}

.nv-footer-link:hover {
  color: var(--nv-ink);
}

.nv-footer-link:active {
  background: color-mix(in srgb, var(--nv-surface-2) 86%, transparent);
}
</style>
