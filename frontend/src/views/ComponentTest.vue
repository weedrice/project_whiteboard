<template>
  <div class="p-8 space-y-8">
    <h1 class="text-2xl font-bold">Component Test Page</h1>

    <section class="space-y-4">
      <h2 class="text-xl font-semibold">BaseButton</h2>
      <div class="flex space-x-4">
        <BaseButton variant="primary">Primary</BaseButton>
        <BaseButton variant="secondary">Secondary</BaseButton>
        <BaseButton variant="danger">Danger</BaseButton>
        <BaseButton variant="ghost">Ghost</BaseButton>
        <BaseButton disabled>Disabled</BaseButton>
      </div>
    </section>

    <section class="space-y-4">
      <h2 class="text-xl font-semibold">BaseInput</h2>
      <div class="max-w-md space-y-4">
        <BaseInput label="Username" v-model="username" placeholder="Enter username" />
        <BaseInput label="Email" v-model="email" type="email" placeholder="Enter email" error="Invalid email address" />
        <p>Username: {{ username }}</p>
      </div>
    </section>

    <section class="space-y-4">
      <h2 class="text-xl font-semibold">BaseModal</h2>
      <BaseButton @click="isModalOpen = true">Open Modal</BaseButton>
      <BaseModal :isOpen="isModalOpen" title="Test Modal" @close="isModalOpen = false">
        <p>This is a test modal content.</p>
        <template #footer>
          <BaseButton @click="isModalOpen = false">Close</BaseButton>
          <BaseButton variant="secondary" @click="isModalOpen = false">Cancel</BaseButton>
        </template>
      </BaseModal>
    </section>

    <section class="space-y-4">
      <h2 class="text-xl font-semibold">Pagination</h2>
      <Pagination :currentPage="currentPage" :totalPages="10" @page-change="currentPage = $event" />
      <p>Current Page: {{ currentPage + 1 }}</p>
    </section>
    <section class="space-y-4">
      <h2 class="text-xl font-semibold">CodeNameDisplay</h2>
      <p>Code 'POST' (type 'BOARD_TYPE'): <CodeNameDisplay code="BOARD_TYPE" value="POST" /></p>
    </section>
    <section class="space-y-4">
      <h2 class="text-xl font-semibold">User Components</h2>
      <div class="space-y-4">
        <h3 class="text-lg">BlockButton</h3>
        <BlockButton :userId="1" :initialBlocked="false" />
        <BlockButton :userId="2" :initialBlocked="true" />
      </div>
    </section>
    <section class="space-y-4">
      <h2 class="text-xl font-semibold">Tag Components</h2>
      <div class="space-y-4">
        <h3 class="text-lg">PostTags (Read Only)</h3>
        <PostTags :modelValue="['Vue', 'Vite', 'Tailwind']" :readOnly="true" />
        
        <h3 class="text-lg">PostTags (Editable)</h3>
        <PostTags v-model="testTags" />
        <p>Current Tags: {{ testTags }}</p>

        <h3 class="text-lg">TagCloud</h3>
        <TagCloud />
      </div>
    </section>
    <section class="space-y-4">
      <h2 class="text-xl font-semibold">Search Components</h2>
      <div class="space-y-4">
        <h3 class="text-lg">GlobalSearchInput</h3>
        <div class="max-w-xs">
          <GlobalSearchInput />
        </div>

        <h3 class="text-lg">PopularKeywordList</h3>
        <PopularKeywordList />

        <h3 class="text-lg">PopularPosts</h3>
        <PopularPosts />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import Pagination from '@/components/common/Pagination.vue'
import CodeNameDisplay from '@/components/common/CodeNameDisplay.vue'
import BlockButton from '@/components/user/BlockButton.vue'
import PostTags from '@/components/tag/PostTags.vue'
import TagCloud from '@/components/tag/TagCloud.vue'
import GlobalSearchInput from '@/components/search/GlobalSearchInput.vue'
import PopularKeywordList from '@/components/search/PopularKeywordList.vue'
import PopularPosts from '@/components/board/PopularPosts.vue'

const username = ref('')
const email = ref('')
const testTags = ref(['Editable', 'Tag'])
const isModalOpen = ref(false)
const currentPage = ref(0)
</script>
