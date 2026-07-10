<template>
  <div class="container mx-auto px-4 py-8">
    <h1 class="text-3xl font-bold mb-6 nv-title">{{ $t('common.terms.title') }}</h1>
    <div class="nv-rich-content prose max-w-none dark:prose-invert">
      <p class="nv-text-muted">{{ $t('common.terms.lastRevised') }}: {{ termsLastRevisedDate }}</p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article1.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        {{ $t('common.terms.article1.body') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article2.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article2.item1') }}<br>
        2. {{ $t('common.terms.article2.item2') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article3.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article3.intro') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article3.communitySpace') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article3.postManagement') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article3.commentsAndLikes') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article3.userCommunication') }}<br>
        2. {{ $t('common.terms.article3.item2') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article4.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article4.item1') }}<br>
        2. {{ $t('common.terms.article4.intro') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article4.anotherIdentity') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article4.falseInformation') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article4.publicOrder') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article5.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article5.intro') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.identityTheft') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.alterInformation') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.unauthorizedInformation') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.intellectualProperty') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.reputationOrOperations') }}<br>
        &nbsp;&nbsp;- {{ $t('common.terms.article5.improperContent') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article6.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        {{ $t('common.terms.article6.body') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article7.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article7.item1') }}<br>
        2. {{ $t('common.terms.article7.item2') }}<br>
        3. {{ $t('common.terms.article7.item3') }}<br>
        4. {{ $t('common.terms.article7.item4') }}
      </p>

      <h2 class="text-xl font-semibold mt-6 mb-3 nv-title">{{ $t('common.terms.article8.title') }}</h2>
      <p class="mb-4 nv-text-muted">
        1. {{ $t('common.terms.article8.item1') }}<br>
        2. {{ $t('common.terms.article8.item2') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const TERMS_LAST_REVISED_AT = new Date(2026, 5, 30)
const termsLastRevisedDate = computed(() => TERMS_LAST_REVISED_AT.toLocaleDateString(
  locale.value === 'ko' ? 'ko-KR' : 'en-US',
))
</script>
