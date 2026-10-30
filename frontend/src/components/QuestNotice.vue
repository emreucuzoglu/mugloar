<script setup lang="ts">
import { computed } from 'vue'
import type { QuestView } from '../api'
import { percent, plural, riskBand, signed } from '../utils/format'
import QuestArt from './QuestArt.vue'

const props = defineProps<{ quest: QuestView; disabled: boolean }>()
const emit = defineEmits<{ take: [questId: string] }>()

const band = computed(() => riskBand(props.quest.successChance))
const expiry = computed(() =>
  props.quest.expiresIn <= 1 ? 'Expires this turn' : `Expires in ${plural(props.quest.expiresIn, 'turn')}`,
)
</script>

<template>
  <article
    class="notice"
    :class="[`notice--${band}`, { 'notice--pick': quest.recommended, 'notice--trap': quest.trap }]"
    :aria-label="`${quest.questType}: ${quest.message}, ${quest.reward} gold, ${quest.probability}`"
  >
    <header class="notice__head">
      <QuestArt class="notice__art" :type="quest.questType" />
      <div class="notice__labels">
        <h3 class="notice__type">{{ quest.questType }}</h3>
        <p v-if="quest.recommended" class="notice__pick">Solver's pick</p>
        <p v-else-if="!quest.acceptable && !quest.trap" class="notice__skip">Solver skips</p>
      </div>
    </header>
    <p v-if="quest.trap" class="notice__trap">Trap: this job never succeeds, whatever its odds say</p>
    <p class="notice__message">{{ quest.message }}</p>
    <dl class="notice__facts">
      <div>
        <dt>Reward</dt>
        <dd class="notice__strong">{{ quest.reward }} gold</dd>
      </div>
      <div>
        <dt>Odds</dt>
        <dd><span class="notice__strong">~{{ percent(quest.successChance) }}</span> {{ quest.probability }}</dd>
      </div>
      <div v-if="quest.recommended" class="notice__value">
        <dt>Expected value</dt>
        <dd class="notice__strong">{{ signed(quest.expectedValue) }}</dd>
      </div>
    </dl>
    <!-- Full width is certainty, the filled part is the estimated chance. -->
    <div class="notice__risk" :style="{ '--chance': quest.successChance }" aria-hidden="true"></div>
    <footer class="notice__footer">
      <span class="notice__expiry">{{ expiry }}</span>
      <button
        type="button"
        class="btn"
        :class="{ 'btn--primary': quest.recommended }"
        :disabled="disabled"
        :aria-label="`${quest.trap ? 'Take anyway' : 'Take this job'}: ${quest.message}`"
        @click="emit('take', quest.questId)"
      >
        {{ quest.trap ? 'Take anyway' : 'Take this job' }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.notice {
  --band: var(--risk-safe);
  --btn-colour: var(--ember-deep);
  --btn-hover: var(--on-parchment);
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1.1rem 1.15rem 1rem;
  color: var(--on-parchment);
}

/* The paper sits on its own layer, so tearing its edge leaves the text sharp. */
.notice::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: var(--parchment-wash);
  filter: url(#paper-edge) drop-shadow(0 2px 3px rgb(40 25 10 / 0.5));
}

.notice--fair { --band: var(--risk-fair); }
.notice--risky { --band: var(--risk-risky); }
.notice--deadly { --band: var(--risk-deadly); }

.notice--pick::before {
  filter: url(#paper-edge) drop-shadow(0 0 2px var(--ember-deep)) drop-shadow(0 0 7px var(--gold));
}

.notice--trap::before {
  background: radial-gradient(ellipse at 50% 40%, #efd6b8 30%, #dfae92 100%);
}

.notice__head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.notice__art {
  flex: none;
  /* Capped by the card, so very large text can't push the drawing past its edge. */
  width: min(7.5rem, 50%);
}

.notice__labels {
  min-width: 0;
  overflow-wrap: anywhere;
}

.notice__type {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--step-2);
  line-height: 1.2;
}

.notice__pick {
  display: inline-block;
  margin: 0.3rem 0 0;
  padding: 0.05rem 0.5rem;
  background: var(--ember-deep);
  color: #fff;
  font-size: var(--step--1);
  font-weight: 600;
}

.notice__skip {
  display: inline-block;
  margin: 0.3rem 0 0;
  padding: 0 0.5rem;
  border: 1px solid var(--on-parchment-soft);
  color: var(--on-parchment-soft);
  font-size: var(--step--1);
  font-weight: 600;
}

.notice__trap {
  margin: 0;
  font-weight: 700;
  color: var(--ember-deep);
}

.notice__message {
  margin: 0;
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.notice__facts {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.25rem 1.25rem;
  margin: 0;
}

.notice__facts dt {
  font-size: var(--step--1);
  color: var(--on-parchment-soft);
}

.notice__facts dd {
  margin: 0;
}

.notice__value {
  grid-column: 1 / -1;
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.notice__strong {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.notice__risk {
  position: relative;
  height: 7px;
  margin-top: auto;
  border: 1px solid var(--on-parchment);
  background: rgb(255 255 255 / 0.35);
}

.notice__risk::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: calc(var(--chance) * 100%);
  background: var(--band);
}

.notice__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.notice__expiry {
  font-size: var(--step--1);
  color: var(--on-parchment-soft);
}
</style>
