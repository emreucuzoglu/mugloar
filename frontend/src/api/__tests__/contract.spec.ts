import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { QUEST_ART } from '../../utils/questArt'
import type { GameState, QuestView, ShopItem, TurnEvent, TurnType } from '../types'
import { isGameState, isQuest, isShopItem, isTurnEvent, listOf } from '../validate'

/**
 * The frontend's half of the contract in contract/: the backend's FrontendContractTest proves it
 * sends exactly these files, and this spec proves types.ts describes exactly the same fields. Each
 * key list has to name every field of its type or the typecheck fails, and has to match the file
 * or this test fails, so a field added on either side can't go unnoticed.
 */
const contract = (file: string): unknown =>
  JSON.parse(readFileSync(resolve(process.cwd(), '../contract', file), 'utf8'))

const GAME_STATE_KEYS = {
  gameId: true,
  lives: true,
  gold: true,
  level: true,
  score: true,
  highScore: true,
  turn: true,
} satisfies Record<keyof GameState, true>

const QUEST_KEYS = {
  questId: true,
  message: true,
  reward: true,
  expiresIn: true,
  probability: true,
  encrypted: true,
  questType: true,
  trap: true,
  rank: true,
  recommended: true,
  successChance: true,
  expectedValue: true,
  acceptable: true,
} satisfies Record<keyof QuestView, true>

const SHOP_ITEM_KEYS = {
  id: true,
  name: true,
  cost: true,
  recommended: true,
} satisfies Record<keyof ShopItem, true>

const TURN_EVENT_KEYS = {
  type: true,
  success: true,
  message: true,
  state: true,
} satisfies Record<keyof TurnEvent, true>

const TURN_TYPES = {
  SOLVE: true,
  PURCHASE: true,
  INVESTIGATE: true,
  SKIP: true,
  GAME_OVER: true,
} satisfies Record<TurnType, true>

const keysOf = (value: unknown) => Object.keys(value as object).sort()
const sorted = (keys: object) => Object.keys(keys).sort()
const first = (value: unknown) => (value as unknown[])[0]

describe('the contract with the backend', () => {
  it('describes a game state with exactly the fields sent', () => {
    const state = contract('game-state.json')

    expect(keysOf(state)).toEqual(sorted(GAME_STATE_KEYS))
    expect(isGameState(state)).toBe(true)
  })

  it('describes a quest with exactly the fields sent', () => {
    const quests = contract('quests.json')

    expect(keysOf(first(quests))).toEqual(sorted(QUEST_KEYS))
    expect(listOf(isQuest)(quests)).toBe(true)
  })

  it('describes a shop item with exactly the fields sent', () => {
    const shop = contract('shop.json')

    expect(keysOf(first(shop))).toEqual(sorted(SHOP_ITEM_KEYS))
    expect(listOf(isShopItem)(shop)).toBe(true)
  })

  it('describes a turn event with exactly the fields sent', () => {
    const history = contract('history.json')
    const event = first(history) as TurnEvent

    expect(keysOf(event)).toEqual(sorted(TURN_EVENT_KEYS))
    expect(keysOf(event.state)).toEqual(sorted(GAME_STATE_KEYS))
    expect(listOf(isTurnEvent)(history)).toBe(true)
  })

  it('knows every turn type the backend sends', () => {
    expect([...(contract('turn-types.json') as string[])].sort()).toEqual(sorted(TURN_TYPES))
  })

  it.each(contract('quest-types.json') as string[])('has its own drawing for the %s quest type', (type) => {
    expect(Object.keys(QUEST_ART)).toContain(type)
  })
})
