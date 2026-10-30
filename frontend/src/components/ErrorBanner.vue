<script setup lang="ts">
defineProps<{ message: string }>()
const emit = defineEmits<{ dismiss: [] }>()
</script>

<template>
  <div class="error" role="alert">
    <p class="error__text">{{ message }}</p>
    <button type="button" class="error__close" @click="emit('dismiss')">Dismiss</button>
  </div>
</template>

<style scoped>
.error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0.9rem;
  border-left: 4px solid var(--ember-deep);
  background: var(--field);
}

.error__text {
  flex: 1 1 0;
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.error__close {
  flex-shrink: 0;
  min-height: 2.75rem;
  border: none;
  background: transparent;
  color: var(--ember-deep);
  text-decoration: underline;
}
</style>
