<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { formatNumber, MAX_TARGET, MIN_TARGET, parseTarget } from '../utils/format'

const props = defineProps<{
  hasGame: boolean
  gameInProgress: boolean
  canAct: boolean
  autoplaying: boolean
  stopping: boolean
  busy: boolean
  resuming: boolean
  score: number
  initialTarget: number
}>()

const emit = defineEmits<{
  start: []
  step: []
  investigate: []
  autoplay: [target: number]
  stop: []
}>()

const targetText = ref(String(props.initialTarget))
const touched = ref(false)
const parsed = computed(() => parseTarget(targetText.value))
const alreadyReached = ref<number | null>(null)
const targetError = computed(() => {
  if (alreadyReached.value !== null) {
    return `You're already at ${formatNumber(alreadyReached.value)}. Set a higher target.`
  }
  return 'error' in parsed.value && touched.value ? parsed.value.error : null
})
const describedBy = computed(() => (targetError.value ? 'target-hint target-error' : 'target-hint'))
const startLabel = computed(() => {
  if (props.hasGame) {
    return 'Start a new game'
  }
  if (props.resuming) {
    return 'Loading your game…'
  }
  return props.busy ? 'Starting…' : 'Start a game'
})

watch(
  () => props.initialTarget,
  (target) => {
    targetText.value = String(target)
  },
)
watch(targetText, () => {
  alreadyReached.value = null
})

function start() {
  if (props.gameInProgress && !window.confirm('Start a new game? The current game will be abandoned.')) {
    lastClicked = null
    return
  }
  emit('start')
}

function submitAutoplay() {
  touched.value = true

  if (!('value' in parsed.value)) {
    lastClicked = null
    return
  }
  if (parsed.value.value <= props.score) {
    alreadyReached.value = props.score
    lastClicked = null
    return
  }
  emit('autoplay', parsed.value.value)
}

function stopIfRunning() {
  if (props.autoplaying) {
    emit('stop')
  }
}

const startButton = ref<HTMLButtonElement | null>(null)
let lastClicked: HTMLElement | null = null

function rememberClicked(event: MouseEvent) {
  lastClicked = (event.target as HTMLElement).closest('button')
}

watch(
  () => props.busy || props.autoplaying,
  async (working) => {
    if (working || !lastClicked) {
      return
    }
    const clicked = lastClicked
    lastClicked = null
    await nextTick()
    if (document.activeElement !== document.body) {
      return
    }
    const target = clicked.isConnected ? clicked : startButton.value
    target?.focus()
  },
)
</script>

<template>
  <div class="controls" @click.capture="rememberClicked">
    <template v-if="gameInProgress">
      <button type="button" class="btn" :disabled="!canAct" @click="emit('step')">Solver plays one turn</button>
      <button type="button" class="btn" :disabled="!canAct" @click="emit('investigate')">Check reputation</button>

      <form class="controls__auto" novalidate @submit.prevent="submitAutoplay">
        <label class="controls__label" for="target">Autoplay to</label>
        <input
          id="target"
          v-model="targetText"
          class="controls__input"
          inputmode="numeric"
          autocomplete="off"
          :aria-invalid="targetError ? 'true' : 'false'"
          :aria-describedby="describedBy"
          :disabled="autoplaying"
          @blur="touched = true"
        />
        <span class="controls__unit">points</span>
        <button
          :type="autoplaying ? 'button' : 'submit'"
          class="btn btn--primary"
          :disabled="autoplaying ? stopping : !canAct"
          @click="stopIfRunning"
        >
          {{ autoplaying ? (stopping ? 'Stopping…' : 'Stop') : 'Autoplay' }}
        </button>
        <p id="target-hint" class="visually-hidden">Autoplay targets range from {{ MIN_TARGET }} to {{ MAX_TARGET }}.</p>
        <p v-if="targetError" id="target-error" class="controls__error" role="alert">{{ targetError }}</p>
      </form>
    </template>

    <button
      ref="startButton"
      type="button"
      class="btn"
      :class="gameInProgress ? 'btn--quiet' : 'btn--primary'"
      :disabled="busy || autoplaying"
      @click="start"
    >
      {{ startLabel }}
    </button>
  </div>
</template>

<style scoped>
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.controls__auto {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}

.controls__label,
.controls__unit {
  color: var(--ink-soft);
}

.controls__input {
  width: 6.5rem;
  min-height: 2.75rem;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--frame);
  background: var(--field);
  font-variant-numeric: tabular-nums;
}

.controls__input[aria-invalid='true'] {
  border: 2px solid var(--ember-deep);
}

.controls__error {
  flex-basis: 100%;
  margin: 0;
  font-size: var(--step--1);
  color: var(--ember-deep);
}

@media (max-width: 40rem) {
  .controls__auto {
    margin-left: 0;
  }
}
</style>
