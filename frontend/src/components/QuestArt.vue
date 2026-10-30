<script setup lang="ts">
import { computed } from 'vue'
import { questArt } from '../utils/questArt'

const props = defineProps<{ type: string }>()
const art = computed(() => questArt(props.type))
</script>

<template>
  <svg class="quest-art" viewBox="0 0 120 90" aria-hidden="true">
    <path
      d="M22 12 C48 0 94 4 106 20 C120 42 108 80 78 84 C50 90 12 82 10 50 C8 34 12 20 22 12Z"
      :fill="art.wash"
      fill-opacity="0.65"
      filter="url(#ink-wobble)"
    />
    <g
      fill="none"
      stroke="#2a1c0f"
      stroke-width="2.2"
      stroke-linecap="round"
      stroke-linejoin="round"
      filter="url(#ink-wobble)"
    >
      <path v-for="d in art.ink" :key="d" :d="d" />
      <path v-for="d in art.solid" :key="d" :d="d" fill="#2a1c0f" stroke-width="0.6" />
    </g>
  </svg>
</template>

<style scoped>
.quest-art {
  display: block;
  width: 100%;
  height: auto;
}
</style>
