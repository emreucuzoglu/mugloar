import { createApi, type Api } from './client'

let current: Api = createApi()

export function getApi(): Api {
  return current
}

/** Swaps the API, for tests. */
export function setApi(api: Api): void {
  current = api
}

export { ApiError, createApi, type Api } from './client'
export type * from './types'
