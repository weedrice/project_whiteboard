<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const TERMS_LAST_REVISED_AT = new Date(2026, 5, 30)
const termsLastRevisedDate = computed(() => TERMS_LAST_REVISED_AT.toLocaleDateString(
  locale.value === 'ko' ? 'ko-KR' : 'en-US',
))
</script>

<template>
  <article class="mx-auto max-w-3xl">
    <header class="mb-8 border-b nv-border pb-6">
      <h1 class="text-3xl font-bold nv-title">{{ $t('common.terms.title') }}</h1>
      <p class="mt-3 text-sm nv-text-muted">
        {{ $t('common.terms.lastRevised') }}: {{ termsLastRevisedDate }}
      </p>
    </header>

    <div class="space-y-10 text-base leading-7 nv-text-muted">
      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article1.title') }}</h2>
        <p>{{ $t('common.terms.article1.body') }}</p>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article2.title') }}</h2>
        <ol class="list-decimal space-y-2 pl-5">
          <li>{{ $t('common.terms.article2.item1') }}</li>
          <li>{{ $t('common.terms.article2.item2') }}</li>
        </ol>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article3.title') }}</h2>
        <ol class="list-decimal space-y-3 pl-5">
          <li>
            {{ $t('common.terms.article3.intro') }}
            <ul class="mt-2 list-disc space-y-1 pl-5">
              <li>{{ $t('common.terms.article3.communitySpace') }}</li>
              <li>{{ $t('common.terms.article3.postManagement') }}</li>
              <li>{{ $t('common.terms.article3.commentsAndLikes') }}</li>
              <li>{{ $t('common.terms.article3.userCommunication') }}</li>
            </ul>
          </li>
          <li>{{ $t('common.terms.article3.item2') }}</li>
        </ol>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article4.title') }}</h2>
        <ol class="list-decimal space-y-3 pl-5">
          <li>{{ $t('common.terms.article4.item1') }}</li>
          <li>
            {{ $t('common.terms.article4.intro') }}
            <ul class="mt-2 list-disc space-y-1 pl-5">
              <li>{{ $t('common.terms.article4.anotherIdentity') }}</li>
              <li>{{ $t('common.terms.article4.falseInformation') }}</li>
              <li>{{ $t('common.terms.article4.publicOrder') }}</li>
            </ul>
          </li>
        </ol>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article5.title') }}</h2>
        <p>{{ $t('common.terms.article5.intro') }}</p>
        <ul class="list-disc space-y-1 pl-5">
          <li>{{ $t('common.terms.article5.identityTheft') }}</li>
          <li>{{ $t('common.terms.article5.alterInformation') }}</li>
          <li>{{ $t('common.terms.article5.unauthorizedInformation') }}</li>
          <li>{{ $t('common.terms.article5.intellectualProperty') }}</li>
          <li>{{ $t('common.terms.article5.reputationOrOperations') }}</li>
          <li>{{ $t('common.terms.article5.improperContent') }}</li>
        </ul>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article6.title') }}</h2>
        <p>{{ $t('common.terms.article6.body') }}</p>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article7.title') }}</h2>
        <ol class="list-decimal space-y-2 pl-5">
          <li>{{ $t('common.terms.article7.item1') }}</li>
          <li>{{ $t('common.terms.article7.item2') }}</li>
          <li>{{ $t('common.terms.article7.item3') }}</li>
          <li>{{ $t('common.terms.article7.item4') }}</li>
        </ol>
      </section>

      <section class="space-y-3">
        <h2 class="text-xl font-semibold nv-title">{{ $t('common.terms.article8.title') }}</h2>
        <ol class="list-decimal space-y-2 pl-5">
          <li>{{ $t('common.terms.article8.item1') }}</li>
          <li>{{ $t('common.terms.article8.item2') }}</li>
        </ol>
      </section>
    </div>
  </article>
</template>
