<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { privacyPolicy, privacyPolicyEn } from '@/locales/privacy'

const { locale } = useI18n()
const PRIVACY_POLICY_LAST_REVISED_AT = new Date(2026, 5, 30)
const policy = computed(() => locale.value === 'ko' ? privacyPolicy : privacyPolicyEn)
const privacyPolicyLastRevisedDate = computed(() => PRIVACY_POLICY_LAST_REVISED_AT.toLocaleDateString(
  locale.value === 'ko' ? 'ko-KR' : 'en-US',
))
</script>

<template>
  <article class="mx-auto max-w-3xl">
    <header class="mb-8 border-b nv-border pb-6">
      <h1 class="text-3xl font-bold nv-title">{{ policy.title }}</h1>
      <p class="mt-3 text-sm nv-text-muted">
        {{ policy.lastRevised }}: {{ privacyPolicyLastRevisedDate }}
      </p>
    </header>

    <div class="space-y-10">
      <section v-for="section in policy.sections" :key="section.title" class="space-y-4">
        <h2 class="text-xl font-semibold nv-title">{{ section.title }}</h2>
        <p
          v-for="paragraph in section.paragraphs"
          :key="paragraph"
          class="text-base leading-7 nv-text-muted"
        >
          {{ paragraph }}
        </p>

        <div v-if="section.groups" class="space-y-5">
          <section v-for="group in section.groups" :key="group.title" class="space-y-2">
            <h3 class="text-base font-semibold nv-title">{{ group.title }}</h3>
            <ul class="list-disc space-y-1.5 pl-5 text-base leading-7 nv-text-muted">
              <li v-for="item in group.items" :key="item">{{ item }}</li>
            </ul>
          </section>
        </div>
      </section>
    </div>
  </article>
</template>
