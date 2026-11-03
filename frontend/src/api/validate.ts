import type { GameState, QuestView, ShopItem, TurnEvent } from './types'

export type Check<T> = (value: unknown) => value is T

type Fields = Record<string, 'string' | 'number' | 'boolean'>

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function hasFields(value: unknown, fields: Fields): value is Record<string, unknown> {
  return isRecord(value) && Object.entries(fields).every(([name, type]) => typeof value[name] === type)
}

const GAME_STATE: Fields = {
  gameId: 'string',
  lives: 'number',
  gold: 'number',
  level: 'number',
  score: 'number',
  highScore: 'number',
  turn: 'number',
}

const QUEST: Fields = {
  questId: 'string',
  message: 'string',
  reward: 'number',
  expiresIn: 'number',
  probability: 'string',
  encrypted: 'boolean',
  questType: 'string',
  trap: 'boolean',
  rank: 'number',
  recommended: 'boolean',
  successChance: 'number',
  expectedValue: 'number',
  acceptable: 'boolean',
}

const SHOP_ITEM: Fields = { id: 'string', name: 'string', cost: 'number', recommended: 'boolean' }

const TURN_EVENT: Fields = { type: 'string', success: 'boolean', message: 'string' }

export const isGameState: Check<GameState> = (value): value is GameState => hasFields(value, GAME_STATE)

export const isQuest: Check<QuestView> = (value): value is QuestView => hasFields(value, QUEST)

export const isShopItem: Check<ShopItem> = (value): value is ShopItem => hasFields(value, SHOP_ITEM)

export const isTurnEvent: Check<TurnEvent> = (value): value is TurnEvent =>
  hasFields(value, TURN_EVENT) && isGameState(value.state)

export function listOf<T>(check: Check<T>): Check<T[]> {
  return (value): value is T[] => Array.isArray(value) && value.every(check)
}
