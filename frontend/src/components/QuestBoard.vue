<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { QuestView, ShopItem } from '../api'
import QuestNotice from './QuestNotice.vue'

const props = defineProps<{
  quests: QuestView[]
  disabled: boolean
  over: boolean
  autoplaying?: boolean
  loading?: boolean
  suggestedItem?: ShopItem | null
}>()
const emit = defineEmits<{ take: [questId: string]; buy: [itemId: string] }>()

const title = ref<HTMLElement | null>(null)
let movedFromBoard = false

function take(questId: string) {
  movedFromBoard = true
  emit('take', questId)
}

function buy(itemId: string) {
  movedFromBoard = true
  emit('buy', itemId)
}

watch(
  () => props.disabled && !props.over,
  async (working) => {
    if (working || !movedFromBoard) {
      return
    }
    movedFromBoard = false
    await nextTick()
    if (document.activeElement === document.body) {
      title.value?.focus()
    }
  },
)
</script>

<template>
  <section class="board" aria-labelledby="board-title">
    <h2 id="board-title" ref="title" class="board__title" tabindex="-1">Jobs on the board</h2>
    <p v-if="over" class="board__empty">The dragon is out of lives. Start a new game to take more jobs.</p>
    <!-- The board isn't re-read during autoplay, so it would show jobs that have gone. -->
    <p v-else-if="autoplaying" class="board__empty">The solver is choosing its own jobs. The board is back when it stops.</p>
    <template v-else>
      <!-- Repeated here because on a phone the shop is below all the jobs. -->
      <div v-if="suggestedItem" class="board__shop-first">
        <p class="board__shop-text">The solver would shop this turn instead of taking a job.</p>
        <button type="button" class="btn btn--primary" :disabled="disabled" @click="buy(suggestedItem.id)">
          Buy {{ suggestedItem.name }} for {{ suggestedItem.cost }} gold
        </button>
      </div>
      <p v-if="quests.length === 0" class="board__empty">
        {{ loading ? 'Loading the jobs…' : 'No jobs posted right now. Play a turn and new ones will appear.' }}
      </p>
      <ul v-else class="board__grid">
        <li v-for="quest in quests" :key="quest.questId">
          <QuestNotice :quest="quest" :disabled="disabled" @take="take" />
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.board__title {
  font-size: var(--step-2);
  margin-bottom: 0.75rem;
}

.board__shop-first {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  margin-bottom: 1rem;
  padding: 0.6rem 0.9rem;
  border-left: 4px solid var(--ember);
  background: rgb(255 255 255 / 0.6);
}

.board__shop-first .btn {
  overflow-wrap: anywhere;
}

.board__shop-text {
  flex: 1 1 14rem;
  margin: 0;
}

.board__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 17rem), 1fr));
  gap: 1.25rem;
}

.board__grid > li {
  display: flex;
  min-width: 0;
}

.board__grid > li > * {
  flex: 1;
  min-width: 0;
}

.board__empty {
  max-width: 40ch;
  color: var(--ink-soft);
}
</style>
