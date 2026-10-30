import type { TurnTone } from './format'

/** Win or loss must not rely on colour alone. Decorative: the message beside it says the same. */
export const TONE_GLYPHS: Record<TurnTone, string> = {
  good: '✓',
  bad: '✗',
  muted: '–',
}
