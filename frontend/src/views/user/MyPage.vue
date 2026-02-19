<script setup lang="ts">
import UserNavigation from '@/components/user/UserNavigation.vue'
</script>

<template>
  <div
    class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 md:py-8 pb-[calc(1rem+env(safe-area-inset-bottom))] sm:pb-6 md:pb-8">
    <UserNavigation />

    <router-view />
  </div>
</template>
