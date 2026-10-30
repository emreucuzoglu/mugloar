import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { GameState, TurnEvent } from '../../api'
import OutcomeStrip from '../OutcomeStrip.vue'

const state: GameState = { gameId: 'g1', lives: 2, gold: 0, level: 0, score: 0, highScore: 0, turn: 3 }
const event = (overrides: Partial<TurnEvent> = {}): TurnEvent => ({
  type: 'SOLVE',
  success: true,
  message: 'Well done',
  state,
  ...overrides,
})
const idle = { move: null, autoplaying: false, target: 1000 }

describe('OutcomeStrip', () => {
  it('invites a first move before there is a result', () => {
    expect(mount(OutcomeStrip, { props: idle }).text()).toBe('Pick a job, or let the solver play a turn.')
  })

  it('shows a win with what it changed', () => {
    const move = { event: event(), changes: [{ text: '+61 gold', bad: false }] }
    const wrapper = mount(OutcomeStrip, { props: { ...idle, move } })

    expect(wrapper.classes()).toContain('outcome--good')
    expect(wrapper.get('.outcome__glyph').text()).toBe('✓')
    expect(wrapper.text()).toContain('Well done')
    expect(wrapper.text()).toContain('+61 gold')
  })

  it('marks a failure and a lost life', () => {
    const move = { event: event({ success: false }), changes: [{ text: '−1 life', bad: true }] }
    const wrapper = mount(OutcomeStrip, { props: { ...idle, move } })

    expect(wrapper.classes()).toContain('outcome--bad')
    expect(wrapper.get('.outcome__glyph').text()).toBe('✗')
    expect(wrapper.get('.outcome__change--bad').text()).toBe('−1 life')
  })

  it('leaves out the list when a move changed nothing', () => {
    const wrapper = mount(OutcomeStrip, { props: { ...idle, move: { event: event(), changes: [] } } })

    expect(wrapper.find('.outcome__changes').exists()).toBe(false)
  })

  it('shows that autoplay is running', () => {
    const wrapper = mount(OutcomeStrip, { props: { ...idle, autoplaying: true, target: 2500 } })

    expect(wrapper.get('.outcome__running').text()).toBe('Autoplaying to 2,500…')
  })

  it('is not a live region, so autoplay does not announce every turn', () => {
    const wrapper = mount(OutcomeStrip, { props: idle })

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('[aria-live]').exists()).toBe(false)
  })

  it('shows server text as plain text', () => {
    const move = { event: event({ message: '<img src=x onerror=alert(1)>' }), changes: [] }
    const wrapper = mount(OutcomeStrip, { props: { ...idle, move } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
  })
})
