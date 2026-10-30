import type { GameState, TurnEvent } from '../api/types'

export type RiskBand = 'safe' | 'fair' | 'risky' | 'deadly'

const SAFE_FROM = 0.88
const FAIR_FROM = 0.7
const RISKY_FROM = 0.4
const PERCENT = 100

export function riskBand(successChance: number): RiskBand {
  if (successChance >= SAFE_FROM) {
    return 'safe'
  }
  if (successChance >= FAIR_FROM) {
    return 'fair'
  }
  if (successChance >= RISKY_FROM) {
    return 'risky'
  }
  return 'deadly'
}

export function percent(chance: number): string {
  const clamped = Math.min(1, Math.max(0, chance))
  return `${Math.round(clamped * PERCENT)}%`
}

export function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`
}

export function formatNumber(value: number): string {
  return value.toLocaleString('en')
}

export function signed(value: number): string {
  const rounded = Math.round(value)
  return `${rounded < 0 ? '−' : '+'}${formatNumber(Math.abs(rounded))}`
}

export type TurnTone = 'good' | 'bad' | 'muted'

export function turnTone(event: TurnEvent): TurnTone {
  if (event.type === 'GAME_OVER') {
    return 'bad'
  }
  if (event.type === 'SKIP') {
    return 'muted'
  }
  return event.success ? 'good' : 'bad'
}

export interface MoveChange {
  text: string
  bad: boolean
}

export function moveChanges(before: GameState, after: GameState): MoveChange[] {
  const describe = (delta: number, one: string, many = `${one}s`) =>
    `${delta < 0 ? '−' : '+'}${plural(Math.abs(delta), one, many)}`
  const changes: MoveChange[] = []
  const add = (delta: number, one: string, many?: string) => {
    if (delta !== 0) {
      changes.push({ text: describe(delta, one, many), bad: one === 'life' && delta < 0 })
    }
  }
  add(after.score - before.score, 'point')
  add(after.gold - before.gold, 'gold', 'gold')
  add(after.lives - before.lives, 'life', 'lives')
  add(after.level - before.level, 'level')
  return changes
}

export const DEFAULT_TARGET = 1000
export const MIN_TARGET = 1
export const MAX_TARGET = 100_000
export const TARGET_RANGE = `Pick a target between ${MIN_TARGET} and ${MAX_TARGET.toLocaleString('en')}.`

export function isValidTarget(value: number): boolean {
  return Number.isInteger(value) && value >= MIN_TARGET && value <= MAX_TARGET
}

export function parseTarget(raw: string): { value: number } | { error: string } {
  const text = raw.trim()
  if (text === '') {
    return { error: 'Enter a target score.' }
  }
  if (!/^\d+$/.test(text)) {
    return { error: 'Use whole numbers only.' }
  }
  const value = Number(text)
  return isValidTarget(value) ? { value } : { error: TARGET_RANGE }
}
