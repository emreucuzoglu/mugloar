<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useGameStore } from './stores/game'
import { formatNumber, hasTarget } from './utils/format'
import QuestBoard from './components/QuestBoard.vue'
import DragonSketch from './components/DragonSketch.vue'
import ErrorBanner from './components/ErrorBanner.vue'
import GameControls from './components/GameControls.vue'
import OutcomeStrip from './components/OutcomeStrip.vue'
import ShopPanel from './components/ShopPanel.vue'
import SketchFilters from './components/SketchFilters.vue'
import StatsBar from './components/StatsBar.vue'
import SunsetScene from './components/SunsetScene.vue'
import TurnLog from './components/TurnLog.vue'

const store = useGameStore()
const {
  game,
  quests,
  shop,
  suggestedItem,
  log,
  lastMove,
  busy,
  resuming,
  loading,
  autoplaying,
  stopRequested,
  target,
  error,
  isOver,
  canAct,
  progress,
  reachedTarget,
} = storeToRefs(store)

const landing = computed(() => !game.value && !resuming.value)

const banner = computed(() => {
  if (!game.value) {
    return ''
  }
  const score = formatNumber(game.value.score)
  if (isOver.value) {
    return `Game over at ${score} points after ${game.value.turn} turns.`
  }
  if (reachedTarget.value && !autoplaying.value) {
    return `Target reached: ${score} points. Keep playing or start a new game.`
  }
  return ''
})

const announcement = computed(() => {
  if (isOver.value) {
    return banner.value
  }
  if (autoplaying.value) {
    return hasTarget(target.value)
      ? `Autoplaying to ${formatNumber(target.value)} points.`
      : 'Autoplaying until the dragon dies.'
  }
  if (!lastMove.value) {
    return banner.value
  }
  const { event, changes } = lastMove.value
  const changed = changes.length > 0 ? ` ${changes.map((change) => change.text).join(', ')}.` : ''
  return `Turn ${event.state.turn}: ${event.message}${changed}${reachedTarget.value ? ' Target reached.' : ''}`
})

onMounted(() => store.resume())
</script>

<template>
  <SunsetScene />
  <SketchFilters />
  <div class="page">
    <header class="masthead" :class="{ 'masthead--compact': !landing }">
      <h1 class="masthead__title">Dragons of Mugloar</h1>
    </header>

    <div class="panel" :class="{ 'panel--busy': busy }">
      <div class="hud">
        <template v-if="game">
          <StatsBar :game="game" :target="target" :progress="progress" />
          <OutcomeStrip :move="lastMove" :autoplaying="autoplaying" :target="target" />
        </template>
        <ErrorBanner v-if="error" :message="error" @dismiss="store.clearError" />
      </div>

      <p class="visually-hidden" role="status">{{ announcement }}</p>

      <p v-if="banner" class="banner" :class="isOver ? 'banner--over' : 'banner--won'">{{ banner }}</p>

      <div class="panel__top">
        <p v-if="landing" class="panel__sub">
          Take jobs, keep your dragon alive,
          {{ hasTarget(target) ? `reach ${formatNumber(target)} points.` : 'score as many points as you can.' }}
        </p>
        <GameControls
          :has-game="game !== null"
          :game-in-progress="game !== null && !isOver"
          :can-act="canAct"
          :autoplaying="autoplaying"
          :stopping="stopRequested"
          :busy="busy"
          :resuming="resuming"
          :score="game?.score ?? 0"
          :initial-target="target"
          @start="store.startGame"
          @step="store.step"
          @investigate="store.investigate"
          @autoplay="store.autoplay"
          @stop="store.stopAutoplay"
        />
      </div>

      <main class="layout">
        <template v-if="landing">
          <DragonSketch class="layout__board" />
          <div class="layout__side intro">
            <h2 class="intro__title">Two ways to play</h2>
            <p class="intro__text">
              <strong>By hand:</strong> choose jobs from the board and buy upgrades, with the solver's pick marked on
              each turn.
            </p>
            <p class="intro__text">
              <strong>Let the solver run:</strong> it weighs each job's reward against its risk. Watch one turn at a
              time, or autoplay until the dragon dies or reaches a target score.
            </p>
          </div>
        </template>
        <template v-else>
          <QuestBoard
            class="layout__board"
            :quests="quests"
            :disabled="!canAct"
            :over="isOver"
            :autoplaying="autoplaying"
            :loading="loading"
            :suggested-item="suggestedItem"
            @take="store.solve"
            @buy="store.buy"
          />

          <aside class="layout__side">
            <ShopPanel
              :items="shop"
              :gold="game?.gold ?? 0"
              :disabled="!canAct"
              :over="isOver"
              :autoplaying="autoplaying"
              :has-game="game !== null"
              :loading="loading"
              @buy="store.buy"
            />
            <TurnLog :events="log" />
          </aside>
        </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 82rem;
  margin: 0 auto;
  padding: 0 1rem 3rem;
}

.masthead {
  padding: clamp(2rem, 7vw, 5rem) 0 clamp(4rem, 12vw, 10rem);
  text-align: center;
}

.masthead__title {
  /* A plain colour first: the gradient below needs background-clip on text. */
  color: #a8281f;
  font-size: clamp(2.4rem, 8vw, 5.75rem);
  line-height: 1.05;
  filter: drop-shadow(0 3px 2px rgb(30 10 5 / 0.75));
}

@supports ((background-clip: text) or (-webkit-background-clip: text)) {
  .masthead__title {
    background: linear-gradient(#ffeeb0 12%, #f3b53f 38%, #cf3a22 62%, #7f150c 92%);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }
}

.masthead--compact {
  padding: 0.75rem 0;
}

.masthead--compact .masthead__title {
  font-size: clamp(1.75rem, 4vw, 2.5rem);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  padding: 1.25rem;
  border: 1px solid var(--frame);
  background: var(--panel);
  box-shadow: 0 10px 18px rgb(0 0 0 / 0.3);
}

.hud {
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: -1.25rem -1.25rem 0;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid var(--line);
  background: var(--page);
}

@media (min-height: 36rem) {
  .hud {
    position: sticky;
    top: 0;
  }
}

.hud:empty {
  display: none;
}

.panel__top {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-bottom: 1.25rem;
  border-bottom: 1px solid var(--line);
}

.panel__sub {
  margin: 0;
  font-size: var(--step-1);
  font-weight: 300;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20rem;
  grid-template-areas: 'board side';
  gap: 1.5rem 2rem;
  align-items: start;
}

.layout__board { grid-area: board; }

.layout__side {
  grid-area: side;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.intro {
  gap: 0.75rem;
}

.intro__title {
  font-size: var(--step-2);
}

.intro__text {
  margin: 0;
}

.banner {
  margin: 0;
  padding: 0.7rem 1rem;
  font-family: var(--font-display);
  font-size: var(--step-1);
  border: 1px solid var(--frame);
}

.banner--over {
  background: var(--ember-deep);
  color: #fff;
}

.banner--won {
  background: var(--gold);
  color: var(--ink);
}

@media (max-width: 56rem) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'board'
      'side';
  }
}
</style>
