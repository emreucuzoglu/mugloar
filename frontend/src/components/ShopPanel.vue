<script setup lang="ts">
import { computed } from 'vue'
import type { ShopItem } from '../api'

const props = defineProps<{
  items: ShopItem[]
  gold: number
  disabled: boolean
  over?: boolean
  autoplaying?: boolean
  hasGame?: boolean
  loading?: boolean
}>()
const emit = defineEmits<{ buy: [itemId: string] }>()

// The shop isn't re-read during autoplay, so its pick would be stale.
const showPick = (item: ShopItem) => item.recommended && !props.over && !props.autoplaying

const emptyMessage = computed(() => {
  if (props.loading) {
    return 'Loading the shop…'
  }
  return props.hasGame ? 'The shop could not be loaded. It will try again after your next move.' : 'The shop is closed until a game starts.'
})
</script>

<template>
  <section class="shop" aria-labelledby="shop-title">
    <h2 id="shop-title" class="shop__title">Shop</h2>
    <p class="shop__hint">A potion restores one life. Everything else raises the dragon's level. Each purchase uses a turn.</p>
    <p v-if="items.length === 0" class="shop__empty">{{ emptyMessage }}</p>
    <ul v-else class="shop__list">
      <li
        v-for="item in items"
        :key="item.id"
        class="shop__item"
        :class="{ 'shop__item--pick': showPick(item) }"
      >
        <span class="shop__name">
          {{ item.name }}
          <span v-if="showPick(item)" class="shop__pick">Solver's pick</span>
          <span v-if="item.cost > gold && !over" class="shop__short">Need {{ item.cost - gold }} more gold</span>
        </span>
        <button
          type="button"
          class="btn shop__buy"
          :class="{ 'btn--primary': showPick(item), 'btn--short': item.cost > gold }"
          :disabled="disabled || item.cost > gold"
          :aria-label="`Buy ${item.name} for ${item.cost} gold`"
          @click="emit('buy', item.id)"
        >
          {{ item.cost }} gold
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.shop__title {
  font-size: var(--step-2);
}

.shop__hint,
.shop__empty {
  margin: 0.25rem 0 0.75rem;
  font-size: var(--step--1);
  color: var(--ink-soft);
}

.shop__list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.shop__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.25rem 0.75rem;
  padding: 0.35rem 0;
  border-bottom: 1px solid var(--line);
}

.shop__item--pick {
  --btn-colour: var(--ember-deep);
  --btn-hover: var(--on-parchment);
  position: relative;
  isolation: isolate;
  margin: 0.4rem 0;
  padding: 0.6rem 0.7rem;
  border-bottom: none;
  color: var(--on-parchment);
}

.shop__item--pick::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: var(--parchment-wash);
  filter: url(#paper-edge) drop-shadow(0 0 2px var(--ember-deep)) drop-shadow(0 0 7px var(--gold));
}

.shop__name {
  flex: 1 1 8rem;
  min-width: 0;
  overflow-wrap: anywhere;
}

.shop__pick {
  display: block;
  width: fit-content;
  margin-top: 0.2rem;
  padding: 0.05rem 0.5rem;
  background: var(--ember-deep);
  color: #fff;
  font-size: var(--step--1);
  font-weight: 600;
}

.shop__short {
  display: block;
  font-size: var(--step--1);
  color: var(--ink-soft);
}

.shop__buy {
  min-width: 6.5rem;
  font-variant-numeric: tabular-nums;
}
</style>
