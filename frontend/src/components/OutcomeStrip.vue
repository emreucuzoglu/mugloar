<script setup lang="ts">
import { computed } from 'vue'
import type { LastMove } from '../stores/game'
import { formatNumber, hasTarget, turnTone } from '../utils/format'
import { TONE_GLYPHS } from '../utils/turnGlyphs'

const props = defineProps<{ move: LastMove | null; autoplaying: boolean; target: number }>()

const tone = computed(() => (props.move ? turnTone(props.move.event) : null))
</script>

<template>
  <div class="outcome" :class="tone ? `outcome--${tone}` : null">
    <!-- Not a live region: App.vue's status element does the announcing, so autoplay stays quiet. -->
    <p v-if="move" class="outcome__message">
      <span class="outcome__glyph" aria-hidden="true">{{ TONE_GLYPHS[tone!] }}</span>
      {{ move.event.message }}
    </p>
    <p v-else class="outcome__message outcome__message--idle">Pick a job, or let the solver play a turn.</p>
    <ul v-if="move && move.changes.length > 0" class="outcome__changes">
      <li v-for="change in move.changes" :key="change.text" :class="{ 'outcome__change--bad': change.bad }">
        {{ change.text }}
      </li>
    </ul>
    <p v-if="autoplaying" class="outcome__running">
      {{ hasTarget(target) ? `Autoplaying to ${formatNumber(target)}…` : 'Autoplaying until the dragon dies…' }}
    </p>
  </div>
</template>

<style scoped>
.outcome {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem 1rem;
  /* Room for two lines, as real messages vary between one and two: otherwise the bar jumps during autoplay. */
  min-height: 3.4rem;
  padding: 0.35rem 0.75rem;
  border-left: 4px solid var(--line);
  background: var(--field);
  font-size: var(--text-small);
}

.outcome--good { border-left-color: var(--risk-safe); }
.outcome--bad { border-left-color: var(--heart); }

.outcome__message {
  flex: 1 1 14rem;
  /* The message comes from the game server and can be long and unbroken. */
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.outcome__message--idle {
  color: var(--ink-soft);
}

.outcome__glyph {
  font-weight: 700;
}

.outcome--good .outcome__glyph { color: var(--risk-safe); }
.outcome--bad .outcome__glyph { color: var(--heart); }

.outcome__changes {
  display: flex;
  flex-wrap: wrap;
  gap: 0 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.outcome__change--bad {
  color: var(--heart);
}

.outcome__running {
  margin: 0;
  font-weight: 600;
  color: var(--ember);
}
</style>
