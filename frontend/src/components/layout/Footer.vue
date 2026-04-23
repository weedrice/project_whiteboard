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
  <footer class="nv-footer mt-auto pb-[max(0.5rem,env(safe-area-inset-bottom))]">
    <div class="max-w-7xl mx-auto py-1.5 sm:py-2 px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col md:flex-row justify-between items-center gap-0.5 md:gap-0">
        <p class="nv-footer-copy text-sm text-center md:text-left order-3 md:order-1 leading-tight">
          &copy; {{ currentYear }} {{ $t('common.appName') }}. {{ $t('common.footer.rights') }}
        </p>

        <div
          class="flex flex-col md:flex-row flex-nowrap justify-center items-center gap-0 md:gap-0 order-1 md:order-2 w-full md:w-auto"
        >
          <div class="flex justify-center items-center gap-x-2 md:contents shrink-0">
            <BaseButton
              @click="themeStore.toggleTheme()"
              variant="ghost"
              size="sm"
              class="min-h-[36px] min-w-[36px] p-0 rounded-full touch-manipulation flex items-center justify-center shrink-0"
              :title="themeStore.isDark ? $t('common.footer.switchToLight') : $t('common.footer.switchToDark')"
            >
              <Sun v-if="themeStore.isDark" class="h-5 w-5" />
              <Moon v-else class="h-5 w-5" />
            </BaseButton>
          </div>

          <span class="nv-footer-separator pl-3 hidden sm:inline md:ml-2" aria-hidden="true">|</span>
          <span class="nv-footer-commit text-xs pl-3 hidden sm:inline" :title="'Commit: ' + commitHash">
            {{ commitHash }}
          </span>
          <span class="nv-footer-separator pl-3 hidden sm:inline" aria-hidden="true">|</span>

          <nav class="flex justify-center items-center gap-x-1 md:contents -mt-0.5 md:mt-0" aria-label="Footer navigation">
            <router-link
              to="/inquiry"
              class="nv-footer-link text-sm py-1 px-2 md:py-1.5 min-h-[36px] inline-flex items-center justify-center rounded-md touch-manipulation md:pl-3"
            >
              {{ '\uBB38\uC758\uD558\uAE30' }}
            </router-link>
            <span class="nv-footer-separator md:ml-2" aria-hidden="true">|</span>
            <router-link
              to="/terms"
              class="nv-footer-link text-sm py-1 px-2 md:py-1.5 min-h-[36px] inline-flex items-center justify-center rounded-md touch-manipulation md:pl-3"
            >
              {{ '\uC11C\uBE44\uC2A4\uC57D\uAD00' }}
            </router-link>
            <span class="nv-footer-separator md:ml-2" aria-hidden="true">|</span>
            <router-link
              to="/privacy"
              class="nv-footer-link text-sm py-1 px-2 md:py-1.5 min-h-[36px] inline-flex items-center justify-center rounded-md touch-manipulation"
            >
              {{ '\uAC1C\uC778\uC815\uBCF4 \uCC98\uB9AC\uBC29\uCE68' }}
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
  transition: background-color 0.2s ease, border-color 0.2s ease;
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
