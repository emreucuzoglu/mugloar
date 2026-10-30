/** Mirrors the backend's JSON. Keep in sync with com.mugloar.* records. */

export interface GameState {
  gameId: string
  lives: number
  gold: number
  level: number
  score: number
  highScore: number
  turn: number
}

export interface QuestView {
  questId: string
  message: string
  reward: number
  expiresIn: number
  probability: string
  encrypted: boolean
  questType: string
  trap: boolean
  /** 1 = the strategy's favourite */
  rank: number
  recommended: boolean
  /** 0..1 */
  successChance: number
  expectedValue: number
  acceptable: boolean
}

export interface ShopItem {
  id: string
  name: string
  cost: number
  recommended: boolean
}

export type TurnType = 'SOLVE' | 'PURCHASE' | 'INVESTIGATE' | 'SKIP' | 'GAME_OVER'

export interface TurnEvent {
  type: TurnType
  success: boolean
  message: string
  state: GameState
}

export interface Problem {
  title?: string
  detail?: string
  status?: number
}
