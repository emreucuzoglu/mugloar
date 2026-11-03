<script setup lang="ts">
import { computed } from 'vue'
import type { GameState } from '../api'
import { formatNumber, hasTarget, percent } from '../utils/format'

const props = defineProps<{ game: GameState; target: number; progress: number }>()

/** Past this many marks, the number says it better. */
const MAX_LIFE_MARKS = 5

const lifeMarks = computed(() => Math.min(Math.max(props.game.lives, 0), MAX_LIFE_MARKS))
</script>

<template>
  <section class="stats" aria-label="Game status">
    <div class="stats__score">
      <p class="stats__number">
        {{ formatNumber(game.score)
        }}<span v-if="hasTarget(target)" class="stats__of"> / {{ formatNumber(target) }} points</span
        ><span v-else class="stats__of"> points</span>
      </p>
      <div
        v-if="hasTarget(target)"
        class="stats__track"
        role="progressbar"
        :aria-valuenow="Math.min(game.score, target)"
        aria-valuemin="0"
        :aria-valuemax="target"
        :aria-label="`Score ${formatNumber(game.score)} of ${formatNumber(target)}`"
      >
        <div class="stats__fill" :style="{ width: percent(progress) }"></div>
      </div>
    </div>

    <dl class="stats__list">
      <div class="stats__item">
        <dt>Lives</dt>
        <dd class="stats__lives">
          <svg v-for="n in lifeMarks" :key="n" class="stats__life" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 21.5 C5 15.5 1.5 11.5 1.5 7.5 A5.3 5.3 0 0 1 12 6 A5.3 5.3 0 0 1 22.5 7.5 C22.5 11.5 19 15.5 12 21.5Z" />
          </svg>
          <span :class="{ 'visually-hidden': game.lives > 0 && game.lives <= MAX_LIFE_MARKS }">{{ game.lives }}</span>
        </dd>
      </div>
      <div class="stats__item">
        <dt>Gold</dt>
        <dd class="stats__gold">{{ formatNumber(game.gold) }}</dd>
      </div>
      <div class="stats__item">
        <dt>Level</dt>
        <dd>{{ game.level }}</dd>
      </div>
      <div class="stats__item">
        <dt>Turn</dt>
        <dd>{{ game.turn }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.stats {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
}

.stats__score {
  flex: 1 1 16rem;
  min-width: 0;
}

.stats__number {
  margin: 0;
  font-size: var(--step-3);
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.stats__of {
  font-weight: 400;
  font-size: var(--step-0);
  color: var(--ink-soft);
}

.stats__track {
  margin-top: 0.6rem;
  height: 10px;
  border: 1px solid var(--frame);
  background: var(--field);
  overflow: hidden;
}

.stats__fill {
  height: 100%;
  background: var(--ember);
  transition: width 0.3s ease-out;
}

.stats__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.75rem;
  margin: 0;
}

.stats__item dt {
  font-size: var(--step--1);
  color: var(--ink-soft);
}

.stats__item dd {
  margin: 0;
  min-height: 1.75rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: var(--step-1);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stats__gold {
  color: var(--gold-ink);
}

.stats__life {
  flex: none;
  width: 1.5rem;
  height: 1.5rem;
  fill: var(--heart);
  stroke: var(--frame);
  stroke-width: 1.2;
  stroke-linejoin: round;
}
</style>
