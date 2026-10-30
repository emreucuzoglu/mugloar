import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError, getApi, type QuestView, type GameState, type ShopItem, type TurnEvent } from '../api'
import { DEFAULT_TARGET, isValidTarget, moveChanges, parseTarget, TARGET_RANGE, type MoveChange } from '../utils/format'

const GAME_KEY = 'mugloar.gameId'
const TARGET_KEY = 'mugloar.target'

export const LOG_LIMIT = 110

export type LoggedEvent = TurnEvent & { id: number }

export interface LastMove {
  event: TurnEvent
  changes: MoveChange[]
}

const storage = {
  read(key: string): string | null {
    try {
      return sessionStorage.getItem(key)
    } catch {
      return null
    }
  },
  write(key: string, value: string | null): void {
    try {
      if (value) {
        sessionStorage.setItem(key, value)
      } else {
        sessionStorage.removeItem(key)
      }
    } catch {
      // ignore
    }
  },
}

export const useGameStore = defineStore('game', () => {
  const game = ref<GameState | null>(null)
  const quests = ref<QuestView[]>([])
  const shop = ref<ShopItem[]>([])
  const log = ref<LoggedEvent[]>([])
  let nextLogId = 0
  const lastMove = ref<LastMove | null>(null)
  const busy = ref(false)
  const resuming = ref(false)
  const refreshing = ref(false)
  const autoplaying = ref(false)
  const stopRequested = ref(false)
  const target = ref(DEFAULT_TARGET)
  const error = ref<string | null>(null)

  const isOver = computed(() => game.value !== null && game.value.lives <= 0)
  const canAct = computed(() => game.value !== null && !isOver.value && !busy.value && !autoplaying.value)
  const progress = computed(() => (game.value ? Math.min(1, game.value.score / target.value) : 0))
  const reachedTarget = computed(() => game.value !== null && game.value.score >= target.value)
  const suggestedItem = computed(() => (isOver.value ? null : (shop.value.find((i) => i.recommended) ?? null)))
  const loading = computed(() => resuming.value || refreshing.value)

  function logged(events: TurnEvent[]): LoggedEvent[] {
    return events.map((event) => ({ ...event, id: nextLogId++ }))
  }

  function saveProgress() {
    if (!game.value) {
      return
    }
    storage.write(GAME_KEY, game.value.gameId)
    storage.write(TARGET_KEY, String(target.value))
  }

  function clearError() {
    error.value = null
  }

  function handleError(e: unknown) {
    if (e instanceof ApiError) {
      if (e.isGameOver && game.value) {
        game.value = { ...game.value, lives: 0 }
        quests.value = []
      }
      if (e.isNotFound && game.value) {
        // The server no longer knows this game (restarted or evicted).
        forget()
      }
      error.value = e.message
    } else {
      error.value = 'Something unexpected went wrong. Reload the page and try again.'
      console.error(e)
    }
  }

  function forget() {
    game.value = null
    quests.value = []
    shop.value = []
    log.value = []
    lastMove.value = null
    storage.write(GAME_KEY, null)
    storage.write(TARGET_KEY, null)
  }

  function apply(events: TurnEvent[]) {
    const last = events[events.length - 1]
    if (!last) {
      return
    }
    log.value = logged([...events].reverse()).concat(log.value).slice(0, LOG_LIMIT)
    if (game.value) {
      lastMove.value = { event: last, changes: moveChanges(game.value, last.state) }
    }
    game.value = last.state
    if (isOver.value) {
      quests.value = []
    }
  }

  async function resync(gameId: string) {
    try {
      const api = getApi()
      const [state, history] = await Promise.all([api.getGame(gameId), api.getHistory(gameId)])
      game.value = state
      log.value = logged(history).slice(0, LOG_LIMIT)
      lastMove.value = null
      if (isOver.value) {
        quests.value = []
      }
      await refresh()
    } catch {
      // The next move tries again.
    }
  }

  function needsResync(e: unknown): boolean {
    return e instanceof ApiError && (e.isConflict || e.isNoResponse)
  }

  async function loadBoard() {
    if (!game.value || isOver.value) {
      return
    }
    quests.value = await getApi().getQuests(game.value.gameId)
  }

  async function loadShop() {
    if (!game.value) {
      return
    }
    shop.value = await getApi().getShop(game.value.gameId)
  }

  async function startGame() {
    if (busy.value || autoplaying.value) {
      return
    }
    busy.value = true
    clearError()
    try {
      const started = await getApi().startGame()
      game.value = started
      log.value = []
      lastMove.value = null
      quests.value = []
      shop.value = []
      saveProgress()
      await refresh()
    } catch (e) {
      handleError(e)
    } finally {
      busy.value = false
    }
  }

  async function resume() {
    const id = storage.read(GAME_KEY)
    if (!id || game.value) {
      return
    }
    busy.value = true
    resuming.value = true
    const savedTarget = parseTarget(storage.read(TARGET_KEY) ?? '')
    if ('value' in savedTarget) {
      target.value = savedTarget.value
    }
    try {
      const api = getApi()
      game.value = await api.getGame(id)
      log.value = logged(await api.getHistory(id)).slice(0, LOG_LIMIT)
      await refresh()
    } catch (e) {
      if (e instanceof ApiError && e.isNotFound) {
        forget()
      } else {
        handleError(e)
      }
    } finally {
      busy.value = false
      resuming.value = false
    }
  }

  async function refresh() {
    refreshing.value = true
    try {
      const results = await Promise.allSettled([loadBoard(), loadShop()])
      for (const result of results) {
        if (result.status === 'rejected') {
          throw result.reason
        }
      }
    } finally {
      refreshing.value = false
    }
  }

  async function move(action: (gameId: string) => Promise<TurnEvent | TurnEvent[]>) {
    if (!canAct.value || !game.value) {
      return
    }
    const gameId = game.value.gameId
    busy.value = true
    clearError()
    try {
      const result = await action(gameId)
      apply(Array.isArray(result) ? result : [result])
      await refresh()
    } catch (e) {
      handleError(e)
      if (needsResync(e)) {
        await resync(gameId)
      }
    } finally {
      busy.value = false
    }
  }

  const solve = (questId: string) => move((id) => getApi().solve(id, questId))
  const buy = (itemId: string) => move((id) => getApi().buy(id, itemId))
  const investigate = () => move((id) => getApi().investigate(id))
  const step = () => move((id) => getApi().autoplayStep(id))

  async function autoplay(targetScore: number) {
    if (!isValidTarget(targetScore)) {
      error.value = TARGET_RANGE
      return
    }
    if (!canAct.value || !game.value){
      return
    }
    const gameId = game.value.gameId
    target.value = targetScore
    saveProgress()
    autoplaying.value = true
    stopRequested.value = false
    clearError()
    try {
      while (!stopRequested.value && game.value && !isOver.value && game.value.score < targetScore) {
        const events = await getApi().autoplayStep(game.value.gameId)
        if (events.length === 0){
          break
        }
        apply(events)
        await refresh()
      }
    } catch (e) {
      handleError(e)
      if (needsResync(e)) {
        await resync(gameId)
      }
    } finally {
      autoplaying.value = false
      stopRequested.value = false
    }
  }

  function stopAutoplay() {
    if (autoplaying.value) {
      stopRequested.value = true
    }
  }

  return {
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
    clearError,
    startGame,
    resume,
    solve,
    buy,
    investigate,
    step,
    autoplay,
    stopAutoplay,
  }
})
