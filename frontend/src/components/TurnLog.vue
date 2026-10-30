<script setup lang="ts">
import type { TurnType } from '../api'
import type { LoggedEvent } from '../stores/game'
import { turnTone } from '../utils/format'
import { TONE_GLYPHS } from '../utils/turnGlyphs'

defineProps<{ events: LoggedEvent[] }>()

const LABELS: Record<TurnType, string> = {
  SOLVE: 'Job',
  PURCHASE: 'Shop',
  INVESTIGATE: 'Reputation',
  SKIP: 'Skipped',
  GAME_OVER: 'Game over',
}
</script>

<template>
  <section class="log" aria-labelledby="log-title">
    <h2 id="log-title" class="log__title">What happened</h2>
    <p v-if="events.length === 0" class="log__empty">Moves will be listed here, newest first.</p>
    <ol v-if="events.length > 0" class="log__list" tabindex="0" aria-labelledby="log-title">
      <li v-for="e in events" :key="e.id" class="log__entry" :class="`log__entry--${turnTone(e)}`">
        <span class="log__meta">
          <span class="log__glyph" aria-hidden="true">{{ TONE_GLYPHS[turnTone(e)] }}</span>
          Turn {{ e.state.turn }} · {{ LABELS[e.type] ?? e.type }}
        </span>
        <span class="log__message">{{ e.message }}</span>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.log__title {
  font-size: var(--step-2);
  margin-bottom: 0.5rem;
}

.log__empty {
  font-size: var(--step--1);
  color: var(--ink-soft);
}

.log__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 26rem;
  overflow-y: auto;
}

.log__entry {
  display: flex;
  flex-direction: column;
  padding: 0.45rem 0 0.45rem 0.75rem;
  border-left: 3px solid var(--line);
  font-size: var(--text-small);
}

.log__entry + .log__entry {
  margin-top: 0.25rem;
}

.log__entry--good { border-left-color: var(--risk-safe); }
.log__entry--bad { border-left-color: var(--heart); }
.log__entry--muted { opacity: 0.75; }

.log__meta {
  color: var(--ink-soft);
}

.log__glyph {
  font-weight: 700;
}

.log__message {
  overflow-wrap: anywhere;
}
</style>
