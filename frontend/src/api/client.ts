import type { Problem } from './types'
import { isGameState, isQuest, isShopItem, isTurnEvent, listOf, type Check } from './validate'

const NO_RESPONSE = 0
const BAD_REQUEST = 400
const NOT_FOUND = 404
const CONFLICT = 409
const GONE = 410
const DEFAULT_TIMEOUT_MS = 20_000

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }

  get isGameOver(): boolean {
    return this.status === GONE
  }

  get isNotFound(): boolean {
    return this.status === NOT_FOUND
  }

  get isConflict(): boolean {
    return this.status === CONFLICT
  }

  get isNoResponse(): boolean {
    return this.status === NO_RESPONSE
  }
}

const ID = /^[A-Za-z0-9_-]{1,64}$/
const ITEM_ID = /^[a-z0-9]{1,32}$/

function requireId(id: string, pattern: RegExp, what: string): string {
  if (!pattern.test(id)) {
    throw new ApiError(BAD_REQUEST, `That ${what} id is not valid.`)
  }
  return encodeURIComponent(id)
}

export interface ApiOptions {
  baseUrl?: string
  timeoutMs?: number
  fetchFn?: typeof fetch
}

export function createApi(options: ApiOptions = {}) {
  const baseUrl = options.baseUrl ?? '/api'
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS
  const fetchFn: typeof fetch = options.fetchFn ?? ((input, init) => fetch(input, init))

  async function request<T>(method: 'GET' | 'POST', path: string, check: Check<T>): Promise<T> {
    const controller = new AbortController()
    const timeout = () => new ApiError(NO_RESPONSE, 'The server took too long to answer. Try again.')
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    try {
      let response: Response
      try {
        response = await fetchFn(baseUrl + path, {
          method,
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })
      } catch {
        if (controller.signal.aborted) {
          throw timeout()
        }
        throw new ApiError(NO_RESPONSE, 'Cannot reach the server. Check that the backend is running.')
      }

      if (!response.ok) {
        throw new ApiError(response.status, await problemMessage(response))
      }
      let body: unknown
      try {
        body = await response.json()
      } catch {
        if (controller.signal.aborted) {
          throw timeout()
        }
        throw unreadable(response.status)
      }
      if (!check(body)) {
        throw unreadable(response.status)
      }
      return body
    } finally {
      clearTimeout(timer)
    }
  }

  const game = (gameId: string) => `/games/${requireId(gameId, ID, 'game')}`
  const turnEvents = listOf(isTurnEvent)

  // Every method is async, so an invalid id becomes a rejected promise, not a synchronous throw.
  return {
    startGame: async () => request('POST', '/games', isGameState),
    getGame: async (gameId: string) => request('GET', game(gameId), isGameState),
    getHistory: async (gameId: string) => request('GET', `${game(gameId)}/history`, turnEvents),
    getQuests: async (gameId: string) => request('GET', `${game(gameId)}/quests`, listOf(isQuest)),
    getShop: async (gameId: string) => request('GET', `${game(gameId)}/shop`, listOf(isShopItem)),
    solve: async (gameId: string, questId: string) =>
      request('POST', `${game(gameId)}/quests/${requireId(questId, ID, 'quest')}/solve`, isTurnEvent),
    buy: async (gameId: string, itemId: string) =>
      request('POST', `${game(gameId)}/shop/${requireId(itemId, ITEM_ID, 'item')}/buy`, isTurnEvent),
    investigate: async (gameId: string) => request('POST', `${game(gameId)}/investigate`, isTurnEvent),
    autoplayStep: async (gameId: string) => request('POST', `${game(gameId)}/autoplay/step`, turnEvents),
  }
}

function unreadable(status: number): ApiError {
  return new ApiError(status, 'The server sent a response that could not be read.')
}

export type Api = ReturnType<typeof createApi>

async function problemMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as Problem
    if (problem.detail) {
      return problem.detail
    }
    if (problem.title) {
      return problem.title
    }
  } catch {
    // not JSON; fall through
  }
  return `Request failed (HTTP ${response.status}).`
}
