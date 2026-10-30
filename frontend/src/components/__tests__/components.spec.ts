import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { QuestView, TurnType } from '../../api'
import QuestNotice from '../QuestNotice.vue'
import QuestBoard from '../QuestBoard.vue'
import ErrorBanner from '../ErrorBanner.vue'
import GameControls from '../GameControls.vue'
import QuestArt from '../QuestArt.vue'
import ShopPanel from '../ShopPanel.vue'
import StatsBar from '../StatsBar.vue'
import TurnLog from '../TurnLog.vue'
import { QUEST_ART, questArt } from '../../utils/questArt'

const quest: QuestView = {
  questId: 'a1',
  message: 'Escort Gail Scarlett to grassland in Sharpfirth',
  reward: 61,
  expiresIn: 6,
  probability: 'Walk in the park',
  encrypted: true,
  questType: 'Escort',
  trap: false,
  rank: 1,
  recommended: true,
  successChance: 0.9,
  expectedValue: 48.9,
  acceptable: true,
}

describe('QuestNotice', () => {
  it('shows the job and emits take', async () => {
    const wrapper = mount(QuestNotice, { props: { quest, disabled: false } })

    expect(wrapper.text()).toContain('Escort Gail Scarlett')
    expect(wrapper.text()).toContain('61 gold')
    expect(wrapper.text()).toContain('~90%')
    expect(wrapper.text()).toContain("Solver's pick")
    expect(wrapper.text()).not.toContain('decoded')
    expect(wrapper.classes()).toContain('notice--safe')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('take')).toEqual([['a1']])
  })

  it('colours risky jobs and disables the button', () => {
    const risky = { ...quest, successChance: 0.3, recommended: false, encrypted: false, expiresIn: 1 }
    const wrapper = mount(QuestNotice, { props: { quest: risky, disabled: true } })

    expect(wrapper.classes()).toContain('notice--deadly')
    expect(wrapper.text()).not.toContain("Solver's pick")
    expect(wrapper.text()).toContain('Expires this turn')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('warns about a trap, and only then', () => {
    const trap = { ...quest, trap: true, questType: 'Trap', probability: 'Sure thing', successChance: 0 }

    expect(mount(QuestNotice, { props: { quest: trap, disabled: false } }).text()).toContain('Trap: this job never succeeds')
    expect(mount(QuestNotice, { props: { quest, disabled: false } }).text()).not.toContain('Trap')
  })

  it('names the job on its button, for screen readers', () => {
    const wrapper = mount(QuestNotice, { props: { quest, disabled: false } })

    expect(wrapper.get('button').attributes('aria-label')).toContain(quest.message)
  })
})

describe('QuestNotice and the solver', () => {
  it('explains its pick with the expected value, and fills the button', () => {
    const wrapper = mount(QuestNotice, { props: { quest, disabled: false } })

    expect(wrapper.get('.notice__value').text()).toContain('Expected value')
    expect(wrapper.get('.notice__value').text()).toContain('+49')
    expect(wrapper.get('button').classes()).toContain('btn--primary')
  })

  it('says when it would skip a job, and shows no expected value there', () => {
    const skipped = { ...quest, recommended: false, acceptable: false }
    const wrapper = mount(QuestNotice, { props: { quest: skipped, disabled: false } })

    expect(wrapper.text()).toContain('Solver skips')
    expect(wrapper.find('.notice__value').exists()).toBe(false)
    expect(wrapper.get('button').classes()).not.toContain('btn--primary')
  })

  it('does not call its own last-resort pick a skip', () => {
    const lastResort = { ...quest, recommended: true, acceptable: false, expectedValue: -12.4 }
    const wrapper = mount(QuestNotice, { props: { quest: lastResort, disabled: false } })

    expect(wrapper.text()).not.toContain('Solver skips')
    expect(wrapper.get('.notice__value').text()).toContain('−12')
  })

  it('makes a trap harder to take by accident', () => {
    const trap = { ...quest, trap: true, recommended: false, acceptable: false, questType: 'Trap', successChance: 0 }
    const wrapper = mount(QuestNotice, { props: { quest: trap, disabled: false } })

    expect(wrapper.get('button').text()).toBe('Take anyway')
    expect(wrapper.get('button').attributes('aria-label')).toBe(`Take anyway: ${quest.message}`)
    expect(wrapper.text()).not.toContain('Solver skips')
  })

  it('heads each job with its type, for moving around by heading', () => {
    expect(mount(QuestNotice, { props: { quest, disabled: false } }).get('h3').text()).toBe('Escort')
  })
})

describe('QuestBoard', () => {
  it('explains an empty board', () => {
    const wrapper = mount(QuestBoard, { props: { quests: [], disabled: false, over: false } })
    expect(wrapper.text()).toContain('No jobs posted right now')
  })

  it('explains a finished game', () => {
    const wrapper = mount(QuestBoard, { props: { quests: [quest], disabled: true, over: true } })
    expect(wrapper.text()).toContain('out of lives')
    expect(wrapper.findAll('article')).toHaveLength(0)
  })

  it('passes take events up', async () => {
    const wrapper = mount(QuestBoard, { props: { quests: [quest], disabled: false, over: false } })
    await wrapper.get('article button').trigger('click')
    expect(wrapper.emitted('take')).toEqual([['a1']])
  })

  it('points to the shop when the solver would buy instead', async () => {
    const suggestedItem = { id: 'hpot', name: 'Healing potion', cost: 50, recommended: true }
    const wrapper = mount(QuestBoard, { props: { quests: [quest], disabled: false, over: false, suggestedItem } })

    expect(wrapper.text()).toContain('The solver would shop this turn')
    await wrapper.get('.board__shop-first button').trigger('click')
    expect(wrapper.emitted('buy')).toEqual([['hpot']])
  })

  it('offers no purchase when the solver would take a job', () => {
    const wrapper = mount(QuestBoard, { props: { quests: [quest], disabled: false, over: false, suggestedItem: null } })

    expect(wrapper.find('.board__shop-first').exists()).toBe(false)
  })

  it('says the jobs are loading rather than that there are none', () => {
    const wrapper = mount(QuestBoard, { props: { quests: [], disabled: true, over: false, loading: true } })

    expect(wrapper.text()).toContain('Loading the jobs')
    expect(wrapper.text()).not.toContain('No jobs posted')
  })

  describe('focus after a move', () => {
    const attached = async (props: { over: boolean }) => {
      const wrapper = mount(QuestBoard, {
        props: { quests: [quest], disabled: false, ...props },
        attachTo: document.body,
      })
      const button = wrapper.get('article button').element as HTMLButtonElement
      button.focus()
      await wrapper.get('article button').trigger('click')
      await wrapper.setProps({ disabled: true })
      button.blur()
      return wrapper
    }
    const title = () => document.getElementById('board-title')

    it('moves to the title when the move has replaced the job', async () => {
      const wrapper = await attached({ over: false })

      await wrapper.setProps({ quests: [], disabled: false })
      await flushPromises()

      expect(document.activeElement).toBe(title())
      wrapper.unmount()
    })

    it('moves to the title when the move ended the game', async () => {
      const wrapper = await attached({ over: false })

      await wrapper.setProps({ over: true })
      await flushPromises()

      expect(document.activeElement).toBe(title())
      wrapper.unmount()
    })

    it('leaves focus alone when the player has already moved it elsewhere', async () => {
      const other = document.createElement('button')
      document.body.append(other)
      const wrapper = await attached({ over: false })
      other.focus()

      await wrapper.setProps({ disabled: false })
      await flushPromises()

      expect(document.activeElement).toBe(other)
      wrapper.unmount()
      other.remove()
    })

    it('does not take focus when the move came from elsewhere', async () => {
      const wrapper = mount(QuestBoard, {
        props: { quests: [quest], disabled: true, over: false },
        attachTo: document.body,
      })

      await wrapper.setProps({ disabled: false })
      await flushPromises()

      expect(document.activeElement).toBe(document.body)
      wrapper.unmount()
    })
  })
})

describe('QuestArt', () => {
  const types = [
    'Fix', 'Clean', 'Negotiate', 'Transport', 'Sell', 'Write', 'Advertise', 'Escort',
    'Rescue', 'Investigate', 'Infiltrate', 'Kill', 'Defend', 'Trap', 'Steal', 'Unknown',
  ]

  it('has a different drawing and wash colour for every backend quest type', () => {
    const drawings = new Set(types.map((type) => QUEST_ART[type].ink.join()))
    const washes = new Set(types.map((type) => QUEST_ART[type].wash))

    expect(drawings.size).toBe(types.length)
    expect(washes.size).toBe(types.length)
  })

  it('falls back to the unknown drawing, and hides from screen readers', () => {
    const wrapper = mount(QuestArt, { props: { type: 'Something new' } })

    expect(questArt('Something new')).toBe(QUEST_ART.Unknown)
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.findAll('path').length).toBeGreaterThan(1)
  })
})

describe('ShopPanel', () => {
  const items = [
    { id: 'hpot', name: 'Healing potion', cost: 50, recommended: false },
    { id: 'ch', name: 'Claw Honing', cost: 300, recommended: false },
  ]

  it('highlights only the item the solver would buy', () => {
    const picked = [items[0], { ...items[1], recommended: true }]
    const wrapper = mount(ShopPanel, { props: { items: picked, gold: 500, disabled: false } })

    expect(wrapper.findAll('.shop__item--pick')).toHaveLength(1)
    expect(wrapper.get('.shop__item--pick').text()).toContain('Claw Honing')
    expect(wrapper.get('.shop__item--pick').text()).toContain("Solver's pick")
  })

  it('highlights nothing once the game is over', () => {
    const picked = [{ ...items[0], recommended: true }]
    const wrapper = mount(ShopPanel, { props: { items: picked, gold: 500, disabled: true, over: true } })

    expect(wrapper.find('.shop__item--pick').exists()).toBe(false)
  })

  it('only enables what the player can afford', async () => {
    const wrapper = mount(ShopPanel, { props: { items, gold: 120, disabled: false } })
    const [potion, honing] = wrapper.findAll('button')

    expect(potion.attributes('disabled')).toBeUndefined()
    expect(honing.attributes('disabled')).toBeDefined()

    await potion.trigger('click')
    expect(wrapper.emitted('buy')).toEqual([['hpot']])
  })

  it('says how much gold is missing, and that buying uses a turn', () => {
    const wrapper = mount(ShopPanel, { props: { items, gold: 120, disabled: false } })

    expect(wrapper.text()).toContain('Each purchase uses a turn')
    expect(wrapper.text()).toContain('Need 180 more gold')
    expect(wrapper.text()).not.toContain('Need -')
    expect(wrapper.findAll('button')[1].classes()).toContain('btn--short')
    expect(wrapper.findAll('button')[0].classes()).not.toContain('btn--short')
  })

  it('stops asking for more gold once the game is over', () => {
    const wrapper = mount(ShopPanel, { props: { items, gold: 0, disabled: true, over: true } })

    expect(wrapper.text()).not.toContain('more gold')
  })

  it('disables everything while the player cannot act', () => {
    const wrapper = mount(ShopPanel, { props: { items, gold: 1000, disabled: true } })

    expect(wrapper.findAll('button').every((b) => b.attributes('disabled') !== undefined)).toBe(true)
  })

  it('explains an empty shop', () => {
    const wrapper = mount(ShopPanel, { props: { items: [], gold: 0, disabled: false } })

    expect(wrapper.text()).toContain('The shop is closed')
    expect(wrapper.findAll('li')).toHaveLength(0)
  })

  it('tells loading and a failed load apart from a closed shop', () => {
    const base = { items: [], gold: 0, disabled: true, hasGame: true }

    expect(mount(ShopPanel, { props: { ...base, loading: true } }).text()).toContain('Loading the shop')
    expect(mount(ShopPanel, { props: base }).text()).toContain('could not be loaded')
  })
})

describe('ErrorBanner', () => {
  it('announces the message and emits dismiss', async () => {
    const wrapper = mount(ErrorBanner, { props: { message: 'Cannot reach the server.' } })

    expect(wrapper.get('[role="alert"]').text()).toContain('Cannot reach the server.')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })
})

describe('StatsBar', () => {
  it('shows score against target and one mark per life', () => {
    const game = { gameId: 'g1', lives: 2, gold: 75, level: 3, score: 250, highScore: 250, turn: 9 }
    const wrapper = mount(StatsBar, { props: { game, target: 1000, progress: 0.25 } })

    expect(wrapper.text()).toContain('250')
    expect(wrapper.text()).toContain('/ 1,000 points')
    expect(wrapper.findAll('.stats__life')).toHaveLength(2)
    expect(wrapper.get('.stats__fill').attributes('style')).toContain('width: 25%')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('250')
  })

  it('keeps the progress value within its range past the target', () => {
    const game = { gameId: 'g1', lives: 2, gold: 75, level: 3, score: 1500, highScore: 1500, turn: 9 }
    const wrapper = mount(StatsBar, { props: { game, target: 1000, progress: 1 } })

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('1000')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-label')).toBe('Score 1500 of 1000')
  })
})

describe('TurnLog', () => {
  it('lists moves newest first with an empty state', () => {
    expect(mount(TurnLog, { props: { events: [] } }).text()).toContain('newest first')

    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const wrapper = mount(TurnLog, {
      props: { events: [{ id: 1, type: 'PURCHASE', success: true, message: 'Bought Healing potion for 50 gold', state }] },
    })
    expect(wrapper.text()).toContain('Turn 4 · Shop')
    expect(wrapper.get('li').classes()).toContain('log__entry--good')
    // Safari doesn't make scrollable areas keyboard-focusable by itself.
    expect(wrapper.get('ol').attributes('tabindex')).toBe('0')
  })

  it.each<[TurnType, boolean, string, string]>([
    ['SOLVE', true, 'Job', 'good'],
    ['SOLVE', false, 'Job', 'bad'],
    ['INVESTIGATE', true, 'Reputation', 'good'],
    ['SKIP', false, 'Skipped', 'muted'],
    ['GAME_OVER', false, 'Game over', 'bad'],
  ])('labels %s (success: %s) as %s in the %s tone', (type, success, label, tone) => {
    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const wrapper = mount(TurnLog, { props: { events: [{ id: 1, type, success, message: 'Something', state }] } })

    expect(wrapper.text()).toContain(`Turn 4 · ${label}`)
    expect(wrapper.get('li').classes()).toContain(`log__entry--${tone}`)
  })

  it('keeps existing entries in the page when a new one arrives', async () => {
    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const entry = (id: number) => ({ id, type: 'SOLVE' as const, success: true, message: `Move ${id}`, state })
    const wrapper = mount(TurnLog, { props: { events: [entry(2), entry(1)] } })
    const before = wrapper.findAll('li').map((li) => li.element)

    await wrapper.setProps({ events: [entry(3), entry(2), entry(1)] })

    const after = wrapper.findAll('li').map((li) => li.element)
    expect(after.slice(1)).toEqual(before)
    expect(after[1]).toBe(before[0])
  })

  it('leaves announcing to the status region, so autoplay stays quiet', () => {
    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const wrapper = mount(TurnLog, { props: { events: [{ id: 1, type: 'SOLVE', success: true, message: 'Won', state }] } })

    expect(wrapper.get('ol').attributes('aria-live')).toBeUndefined()
  })

  it.each([
    [true, '✓'],
    [false, '✗'],
  ])('marks success %s with %s as well as a colour', (success, glyph) => {
    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const wrapper = mount(TurnLog, { props: { events: [{ id: 1, type: 'SOLVE', success, message: 'Done', state }] } })

    expect(wrapper.get('.log__glyph').text()).toBe(glyph)
  })

  it('shows a turn type it has no label for, rather than nothing', () => {
    const state = { gameId: 'g1', lives: 3, gold: 0, level: 0, score: 0, highScore: 0, turn: 4 }
    const type = 'NEW_TYPE' as TurnType
    const wrapper = mount(TurnLog, { props: { events: [{ id: 1, type, success: true, message: 'x', state }] } })

    expect(wrapper.text()).toContain('Turn 4 · NEW_TYPE')
  })
})

describe('GameControls', () => {
  const props = {
    hasGame: true,
    gameInProgress: true,
    canAct: true,
    autoplaying: false,
    stopping: false,
    busy: false,
    resuming: false,
    score: 0,
    initialTarget: 1000,
  }
  const noGame = { hasGame: false, gameInProgress: false, canAct: false }

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('emits autoplay with the parsed target', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.get('input').setValue(' 1500 ')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('autoplay')).toEqual([[1500]])
  })

  it('blocks invalid targets and says why', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.get('input').setValue('lots')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('autoplay')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toBe('Use whole numbers only.')
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
  })

  it('swaps autoplay for stop while running', async () => {
    const wrapper = mount(GameControls, { props: { ...props, autoplaying: true, canAct: false } })
    const stop = wrapper.findAll('button').find((b) => b.text() === 'Stop')

    expect(stop).toBeDefined()
    await stop!.trigger('click')
    expect(wrapper.emitted('stop')).toHaveLength(1)
  })

  it('offers only start before a game exists', () => {
    const wrapper = mount(GameControls, { props: { ...props, ...noGame } })

    expect(wrapper.findAll('button').map((b) => b.text())).toEqual(['Start a game'])
  })

  it('shows that the first game is starting', () => {
    const wrapper = mount(GameControls, { props: { ...props, ...noGame, busy: true } })

    expect(wrapper.get('button').text()).toBe('Starting…')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('says a saved game is loading, not that one is starting', () => {
    const wrapper = mount(GameControls, { props: { ...props, ...noGame, busy: true, resuming: true } })

    expect(wrapper.get('button').text()).toBe('Loading your game…')
  })

  it('at game over offers only a new game, as the main action', () => {
    const wrapper = mount(GameControls, { props: { ...props, gameInProgress: false, canAct: false } })

    expect(wrapper.findAll('button').map((b) => b.text())).toEqual(['Start a new game'])
    expect(wrapper.get('button').classes()).toContain('btn--primary')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('asks before abandoning a game in progress', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(GameControls, { props })
    const start = wrapper.findAll('button').find((b) => b.text() === 'Start a new game')!

    await start.trigger('click')
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('start')).toBeUndefined()

    confirm.mockReturnValue(true)
    await start.trigger('click')
    expect(wrapper.emitted('start')).toHaveLength(1)
  })

  it('keeps the new game button quiet and last while a game is in progress', () => {
    const wrapper = mount(GameControls, { props })
    const buttons = wrapper.findAll('button')
    const last = buttons[buttons.length - 1]

    expect(last.text()).toBe('Start a new game')
    expect(last.classes()).toContain('btn--quiet')
  })

  it('does not ask when no game would be lost', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    const wrapper = mount(GameControls, { props: { ...props, gameInProgress: false, canAct: false } })

    await wrapper.get('button').trigger('click')

    expect(confirm).not.toHaveBeenCalled()
    expect(wrapper.emitted('start')).toHaveLength(1)
  })

  it('refuses a target already reached, and says so only after a submit', async () => {
    const wrapper = mount(GameControls, { props: { ...props, score: 1200 } })

    await wrapper.get('input').trigger('blur')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('autoplay')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toBe("You're already at 1,200. Set a higher target.")

    await wrapper.get('input').setValue('1500')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('autoplay')).toEqual([[1500]])
  })

  it('does not complain when autoplay has passed its own target', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.get('form').trigger('submit')
    await wrapper.setProps({ score: 1200 })

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('uses one button for autoplay and stop, so it keeps focus', async () => {
    const wrapper = mount(GameControls, { props })
    const button = wrapper.get('form button').element

    await wrapper.setProps({ autoplaying: true, canAct: false })

    expect(wrapper.get('form button').element).toBe(button)
    expect(wrapper.get('form button').text()).toBe('Stop')
    expect(wrapper.get('form button').attributes('type')).toBe('button')
  })

  it('links the target field to its range', () => {
    const wrapper = mount(GameControls, { props })

    expect(wrapper.get('input').attributes('aria-describedby')).toBe('target-hint')
    expect(wrapper.get('#target-hint').text()).toContain('100000')
  })

  it('updates the field when a saved target arrives', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.setProps({ initialTarget: 2500 })

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('2500')
  })

  describe('focus after a move', () => {
    const button = (wrapper: ReturnType<typeof mount>, text: string) =>
      wrapper.findAll('button').find((b) => b.text() === text)!

    it('comes back to the clicked button once it is enabled again', async () => {
      const wrapper = mount(GameControls, { props, attachTo: document.body })
      const step = button(wrapper, 'Solver plays one turn')
      await step.trigger('click')
      await wrapper.setProps({ busy: true, canAct: false })
      ;(document.activeElement as HTMLElement).blur()

      await wrapper.setProps({ busy: false, canAct: true })
      await flushPromises()

      expect(document.activeElement).toBe(step.element)
      wrapper.unmount()
    })

    it('goes to the new game button when the clicked button has gone', async () => {
      const wrapper = mount(GameControls, { props, attachTo: document.body })
      await button(wrapper, 'Solver plays one turn').trigger('click')
      await wrapper.setProps({ busy: true, canAct: false })

      await wrapper.setProps({ busy: false, gameInProgress: false })
      await flushPromises()

      expect(document.activeElement).toBe(button(wrapper, 'Start a new game').element)
      wrapper.unmount()
    })

    it('takes no focus when the work was started from elsewhere', async () => {
      const wrapper = mount(GameControls, { props, attachTo: document.body })

      await wrapper.setProps({ busy: true, canAct: false })
      await wrapper.setProps({ busy: false, canAct: true })
      await flushPromises()

      expect(document.activeElement).toBe(document.body)
      wrapper.unmount()
    })
  })

  it('emits start, step and investigate from their buttons', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(GameControls, { props })
    const button = (text: string) => wrapper.findAll('button').find((b) => b.text() === text)!

    await button('Start a new game').trigger('click')
    await button('Solver plays one turn').trigger('click')
    await button('Check reputation').trigger('click')

    expect(wrapper.emitted('start')).toHaveLength(1)
    expect(wrapper.emitted('step')).toHaveLength(1)
    expect(wrapper.emitted('investigate')).toHaveLength(1)
  })

  it('disables moves while the player cannot act', () => {
    const wrapper = mount(GameControls, { props: { ...props, canAct: false } })
    const disabled = (text: string) =>
      wrapper.findAll('button').find((b) => b.text() === text)!.attributes('disabled') !== undefined

    expect(disabled('Solver plays one turn')).toBe(true)
    expect(disabled('Check reputation')).toBe(true)
    expect(disabled('Autoplay')).toBe(true)
    expect(disabled('Start a new game')).toBe(false)
  })

  it('disables start while busy', () => {
    const wrapper = mount(GameControls, { props: { ...props, busy: true } })

    expect(wrapper.findAll('button').find((b) => b.text() === 'Start a new game')!.attributes('disabled')).toBeDefined()
  })

  it('shows that it is stopping and locks the target', () => {
    const wrapper = mount(GameControls, { props: { ...props, autoplaying: true, stopping: true, canAct: false } })
    const stop = wrapper.findAll('button').find((b) => b.text() === 'Stopping…')

    expect(stop!.attributes('disabled')).toBeDefined()
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  })

  it('waits until the field is left before complaining', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.get('input').setValue('')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await wrapper.get('input').trigger('blur')
    expect(wrapper.get('[role="alert"]').text()).toBe('Enter a target score.')
  })

  it('blocks targets outside the allowed range', async () => {
    const wrapper = mount(GameControls, { props })

    await wrapper.get('input').setValue('100001')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('autoplay')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toBe('Pick a target between 1 and 100,000.')
  })

  it('starts from the given target', () => {
    const wrapper = mount(GameControls, { props: { ...props, initialTarget: 2500 } })

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('2500')
  })
})
