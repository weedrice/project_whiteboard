<script setup>
import { computed } from 'vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<template>
  <footer class="bg-white dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 mt-auto">
    <div class="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col md:flex-row justify-between items-center">
        <div class="mb-4 md:mb-0">
          <p class="text-sm text-gray-500 dark:text-gray-400">
            &copy; {{ currentYear }} Whiteboard. All rights reserved.
          </p>
        </div>
        <div class="flex space-x-6">
          <a href="https://github.com/weedrice/project_whiteboard" target="_blank" rel="noopener noreferrer" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            <span class="sr-only">GitHub</span>
            <!-- Github Icon or similar could go here -->
            GitHub
          </a>
          <a href="#" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            Terms
          </a>
          <a href="#" class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
            Privacy
          </a>
        </div>
      </div>
    </div>
  </footer>
</template>
