# Dragons of Mugloar

A full-stack solution to both [Dragons of Mugloar](https://dragonsofmugloar.com/) adventures: a solver that plays the game through its REST API, and a web app for watching it or playing by hand.

## 1. Components

```
┌──────────┐            ┌─────────┐            ┌──────────┐
│ Frontend │ ── moves ▶ │ Backend │ ── moves ▶ │ Game API │
│          │ ◀─ state ─ │         │ ◀─ state ─ │          │
└──────────┘            └─────────┘            └──────────┘
                             ▲
                             │
                  ./gradlew solve (no UI)
```

- **Backend:** holds the solver, which decides every move. It serves the frontend through its own REST API, and can also play from the command line with no UI. It's the only component that talks to the game API.
- **Frontend:** the browser app, for the visual adventure. You can play by hand, see how the solver rates every job, or let it autoplay. It holds no game logic.
- **Game API:** the public game server at `dragonsofmugloar.com/api/v2`, which isn't part of this repository. It runs the game and holds its state.

The browser never calls the game API directly. Keeping the strategy on the server means the UI's "Solver's pick" and the command line make exactly the same decisions, and avoids CORS against the game server.

## 2. Running the app

The backend needs internet access, because it plays on the live game server. Ports 5173 and 8080 must be free.

### With Docker

You only need Docker, and it must be running (on Windows and macOS, start Docker Desktop first). From the project root:

```bash
docker compose up        # the first start downloads everything
```

Wait until the backend logs `Started MugloarApplication`, then open http://localhost:5173. Until then, the frontend can't reach the backend and its log shows proxy errors. Press `Ctrl+C` to stop.

### Without Docker

Install these first:

| Tool | Version |
| --- | --- |
| JDK | 25 (the Gradle wrapper downloads Gradle itself) |
| Node.js | 26 (22.22.2+ and 24.15+ also work) |

Then start each component in its own terminal, and open http://localhost:5173:

```bash
cd backend
./gradlew bootRun            # Windows: gradlew.bat bootRun
```

```bash
cd frontend
npm install                  # first time, and after dependency changes
npm run dev
```

Press `Ctrl+C` in each terminal to stop.

### Playing in the browser

Press **Start a game**, then either take jobs yourself or press **Autoplay**. Autoplay runs until the dragon dies or the score reaches the value in its **Autoplay to** field, which can be up to 100,000.

### Without the UI

The solver plays until the dragon dies. Each move is logged, followed by a summary:

```bash
cd backend
./gradlew solve                    # one game
./gradlew solve -Pgames=10         # ten games
```

With the containers running, use `docker compose exec backend ./gradlew solve` instead.

## 3. What the game does

**What the documentation says.** The [API documentation](https://dragonsofmugloar.com/doc/) lists six endpoints, and the [adventure's brief](https://dragonsofmugloar.com/) gives the goal: start a game, fetch quests, pick the best ones to solve, optionally buy items, and repeat until lives run out, reaching at least 1000 points reliably.

| Endpoint | What's documented |
| --- | --- |
| `POST /game/start` | Game id, lives, gold, level, score, high score and turn |
| `GET /{gameId}/messages` | The board: each quest's id, free text, gold reward, and the number of turns until it expires |
| `POST /{gameId}/solve/{adId}` | Whether it succeeded, the new state, and a text explanation |
| `GET /{gameId}/shop` | Each item's id, name and cost |
| `POST /{gameId}/shop/buy/{itemId}` | Whether it succeeded and the new state, with a note that a refused purchase still costs a turn |
| `POST /{gameId}/investigate/reputation` | Reputation with people, state and underworld |

That's all it says. The refused purchase is the only game rule it spells out. It doesn't say what an item does, what reputation does, how hard a job is, or what makes the game harder. Quests also carry two fields it leaves out: `probability`, the difficulty label, and `encrypted`, which says how the quest is encoded. Live responses also differ from the documented types: `reward` and `shoppingSuccess` are documented as strings, but are a number and a boolean.

**How the rest was found.** By playing the game through the API, recording every response, and comparing the results. The findings are stored in `domain/Probability`, `domain/QuestType` and `domain/Item`. All endpoints the solver uses were checked against live responses, including that encrypted quests are solved with the decoded id.

| Round | Games | API calls | What it was for |
| --- | --- | --- | --- |
| Recorded games | 110 | 15,128 | Edge-case probes, odds by label and level, single-item games, long games, strategies. Most findings below start here |
| Healing and reputation | 65 | 9,992 | How far to heal, and whether reputation matters |
| Upgrade timing | 32 | 6,720 | When to stop buying items, and whether to skip +1 items |
| Reputation, reading it after every win | 164 | 16,122 | What each job changes, and the trap threshold |
| High reputation and the trap line | 266 | 33,000 | People, state and underworld at higher values, and staying above -10 |
| Real solver validation | 205 | about 40,000 (estimated from moves played) | Checking each strategy change |
| All five +2 items | 60 | 13,447 | Whether owning the whole set changes anything |
| Item variety | 160 | 37,597 | What causes that gain, whether to keep rotating, and the +1 set |
| +1 set before rotating | 60 | 28,721 | Whether the +1 set adds anything to rotating |
| **Total** | **1,122** | **about 200,000** | |

Small one-off probes add a few hundred more calls. The 110 recorded games are the base for most figures, and made these calls:

| Call | Count | What it gave |
| --- | --- | --- |
| Start a game | 110 | |
| Read the board | 5,847 | 58,470 quests shown, 17,331 of them distinct |
| Solve a job | 5,257 | 3,042 wins, 2,207 fails, 8 rejected |
| Buy an item | 3,359 | 3,348 bought, 11 refused |
| Read the shop | 285 | |
| Check reputation | 260 | |
| Calls after a game ended | 10 | |
| **Total** | **15,128** | |

Some figures use part of the data: the quest ranges in `QuestType` come from the first 74 games, and the turn-by-turn mix in [section 4](#4-how-the-solver-plays) comes from the solver games it names.

### 3.1 Quest types

**How it was found.** A quest's free text follows fixed patterns, so quests were sorted by their opening words. `QuestType` holds what the 11,397 quests in the first 74 games showed about each type. Turn numbers are when each type was first seen.

| Turns | Quests | Level-0 reward (median) |
| --- | --- | --- |
| 0-20 | Fix, Clean, Negotiate, Transport, Sell, Write, Advertise | 5-34 |
| 0-27 | Escort, Steal delivery | 55-68 |
| 6-31 | Rescue (never at level 0) | 35 at any level |
| 16-36 | Investigate, Infiltrate, Kill (26-30): always encrypted, expire in 1-3 turns | 110-137 |
| 21+ | Defend: nearly the whole board from turn 35 | 67, rising to 271 at level 31+ |

**The trap.** `Steal super awesome diamond …` won 0 of 179 attempts, though nearly all claimed "Sure thing" or "Piece of cake" and they pay well. It was found in 30 targeted games, when wins fell far below what the labels promised. Traps are driven by state reputation, not by stealing itself, and the threshold is sharp. Across the two reputation experiments, 33% of new quests were traps at state -10 or below (of 9,447), and none were above it (of 46,432). The share rises the longer a game stays there, from 18% in the first 5 turns to about 49% after 20. Raising state again through Investigate or Infiltrate jobs stops new ones. Every game that reached -10 had at least 5 steal wins, since each costs 2 state. Ordinary `Steal … delivery` jobs stay as safe as their label, even at low state. Because nothing but expiry clears a trap, they build up and can fill all 10 slots. The solver now stays above -10, which raised scores (see [Live results](#live-results)). Earlier data had hinted otherwise, with games of 5+ steal wins averaging 6,144 against 5,758, but that wasn't a controlled comparison.

### 3.2 Difficulty

**How it was found.** By taking jobs at every label and counting wins against each label, across all 110 recorded games. A label gives the odds. Excluding traps (5,083 attempts), success rates did not vary much with level, turn, lives, encryption or quest type. Of 14 level, turn, lives and encryption groups, the largest gap was level 6-15, with 322 wins against 339 expected, which could be chance.

| Label | Success, all 110 games | Attempts |
| --- | --- | --- |
| Sure thing | 100% | 326 |
| Piece of cake | 96% | 1,153 |
| Walk in the park | 84% | 653 |
| Quite likely | 78% | 540 |
| Hmmm.... | 67% | 348 |
| Gamble | 51% | 256 |
| Risky | 42% | 234 |
| Rather detrimental | 35% | 253 |
| Playing with fire | 22% | 239 |
| Suicide mission | 6% | 725 |
| Impossible | 0% | 356 |

### 3.3 Turns

**How it was found.** The documentation only mentions turns for purchases, so edge-case probes in the first 7 games compared `turn` before and after each kind of call, including unknown and expired quests and calls on a finished game. Long games then showed how the board changes over time.

Solving, buying and checking reputation each cost one turn, even a refused purchase or a reputation check, whose response doesn't mention it. Reading the board or shop is free. The board always holds 10 quests, each usually lasting 7 turns. Solving an unknown or expired quest returns `400` and costs nothing, and every call on a finished game returns `410`. The server can also answer `429` under sustained load.

Difficulty rises with turns, whatever the level, score or gold, so the game is effectively on a clock: by about turn 70 safe jobs are nearly gone, and by turn 100 almost everything is "Suicide mission" or "Impossible". A level-0 game that only passed turns saw nothing but "Impossible" from turn 100 until the check stopped at turn 424.

### 3.4 Reputation

**How it was found.** Three rounds of games played in deliberately different styles: 65 games that also compared healing, 164 games in 14 styles that read reputation after every win, and 266 games aimed at higher values. Only a win changes it, and each job's change was exact in nearly every case (3,483 wins; none of 1,324 failures changed anything):

| Job | People | State | Underworld | Wins |
| --- | --- | --- | --- | --- |
| Advertise, Write | +1 | | | 322 |
| Transport | +0.3 | | | 78 |
| Negotiate | +0.2 | | | 68 |
| Fix, Clean, Sell, Escort, Rescue | +0.1 | | | 572 |
| Steal | +1 | -2 | | 474 |
| Investigate | -0.1 | +1 | | 81 |
| Infiltrate | | +2 | -1 | 84 |
| Kill | | -1 | +2 | 14 |
| Defend | | | | 1,802 |

Apart from the [trap](#31-quest-types) threshold, reputation showed no effect on new quests or odds. The 164 games covered people 0 to 10.1, state -16 to 10 and underworld -4 to 0. With turn, level and quest type held fixed, the effects per reputation point (± one standard error, clustered by game) were:

| Per point of | Stated odds, % points | Reward, % | Wins above the label's odds, % points |
| --- | --- | --- | --- |
| People | +0.26 ±0.13 | -0.12 ±0.11 | -0.51 ±0.49 |
| State | +0.09 ±0.06 | -0.06 ±0.05 | -0.16 ±0.20 |
| Underworld | -0.14 ±0.27 | -0.29 ±0.21 | -0.40 ±1.20 |

Quest mix didn't respond either: of 42 tests (14 quest types against 3 reputations), 3 passed two standard errors, about what chance gives, and none passed three. The same was true when odds and rewards were split by quest type. No new kind of quest appeared at any reputation, and Rescue's level requirement is the only unlock seen.

The 266 games tested the higher values, tracking reputation locally so games didn't spend turns checking it. Each row compares quests and wins at or above a line with those below it, with turn, level and quest type held fixed:

| Line | Reached in | Stated odds, % points | Reward, % | Wins against the labels' promise |
| --- | --- | --- | --- | --- |
| People 10+ | 96 games | +0.1 ±0.3 | -0.4 ±0.8 | 1,806 vs 1,817 |
| State 10+ | 6 games | -0.6 ±1.0 | +5.2 ±2.9 | 98 vs 91 |
| Underworld above 0 | 12 games | -0.3 ±0.8 | +0.4 ±1.4 | 197 vs 207 |

There were no new kinds of quest at any of these. Some per-quest figures passed three standard errors, but they rest on a handful of quests, such as 4 Investigate and Infiltrate quests at state 10+, and the side-by-side quest mix within narrow turn windows showed no matching change.

Underworld looks like a plain counter. Kill raises it, Infiltrate lowers it, and it does go above 0: 12 Kill wins from 0 took it to +2, confirmed by the server. Higher is out of practical reach. Kill jobs only appear on turns 26-30, about 2 per game and mostly labelled "Suicide mission", so +10 would take 5 wins in one game, and no game won more than one. Kill odds match their labels at underworld 0 (12 wins in 108, 10 promised).

The limits: nothing above underworld +2 was tested, no game had people and state both at 10+, state 10+ was reached only after turn 31, when nearly every new quest is Defend, and most games stopped at turn 70.

Checking reputation is also expensive: earlier games that checked every 3 moves averaged 3,719-4,234 points against about 6,000, though they also restricted which jobs they took.

### 3.5 Items

**How it was found.** 285 shop reads and 3,359 purchases, including games that bought only one item, and comparing the labels of the same quests before and after an upgrade. The shop was identical in all 285 reads, up to turn 126 and level 39: it never refreshes and nothing sells out.

| Item | Cost | Effect |
| --- | --- | --- |
| Healing potion (`hpot`) | 50 | +1 life, stacks with no cap seen (11 reached) |
| `cs`, `gas`, `wax`, `tricks`, `wingpot` | 100 | +1 level |
| `ch`, `rf`, `iron`, `mtrix`, `wingpotmax` | 300 | +2 levels |

New quests pay about 5-6 gold more per level, at every level seen. Better labels only come early: each level improves the label of a quest already on the board by about 0.12 steps below level 10 and 0.04 at levels 10-19, and by almost nothing from level 20 up (0 steps across 1,071 quests above level 30). It never makes a label worse. New quests are not much easier at higher levels either: at a given turn, level 20 and level 59 see about the same labels. So past about level 20, upgrades mainly buy higher rewards. Nearly all of these games bought only `ch` as their +2 item, so these figures may only hold for that way of buying.

**Owning all five +2 items makes later quests easier.** 60 live games, 30 per arm and alternating, played the same rules until death. One arm bought `ch` every time; the other bought one of each +2 item first, usually done by about turn 31, then `ch`. No recorded game had owned all five before.

| Arm | Average score | Lowest | Highest | Turns | Level |
| --- | --- | --- | --- | --- | --- |
| `ch` only | 6,243 | 3,987 | 8,442 | 124 | 31 |
| All five first | 7,559 | 4,193 | 11,039 | 149 | 37 |

The difference is +1,316 points, with a 95% range of +638 to +1,994. Comparing new quests at the same turn and level, per game:

| Turns | Stated odds, all five minus `ch` only, % points |
| --- | --- |
| 0-29 | -1.5 ±0.9 and -1.8 ±1.9, before the set is complete |
| 30-39 | +1.6 ±1.1 |
| 40-49 | +8.4 ±1.3 |
| 50-59 | +8.6 ±1.5 |
| 60-69 | +5.6 ±1.2 |
| 70-79 | +2.5 ±0.8 |
| 80-99 | +2.3 ±0.4 |
| 100+ | +0.2 ±0.1 |

Defend quests alone show the same gap, so quest mix doesn't explain it. Rewards didn't change, and labels stayed honest, with 703 wins against 698 promised after the set was complete. The fifth item itself gave only its usual +2 levels, the shop didn't change, and reputation didn't jump.

**Rotating the +2 items does much better than owning one of each.** A second test played 160 live games to find the cause, with every arm following the same rules until death and the arms in a rotating order:

| Arm | Games | Average score | 95% range against `ch` only | Turns | Level |
| --- | --- | --- | --- | --- | --- |
| Rotate all five for the whole game | 20 | 10,109 | +3,002 to +5,485 | 254 | 38 |
| Each +1 item, then `ch` | 20 | 7,691 | +910 to +2,741 | 141 | 41 |
| Four +2 items, then `ch` | 20 | 7,645 | +805 to +2,753 | 153 | 37 |
| All five once, then `ch` | 20 | 7,047 | +313 to +2,049 | 149 | 33 |
| Only `wingpotmax` | 10 | 6,992 | -163 to +2,414 | 135 | 35 |
| Only `rf` | 10 | 6,547 | -348 to +1,709 | 125 | 33 |
| `cs` five times, then `ch` | 20 | 6,468 | -249 to +1,452 | 115 | 36 |
| Only `mtrix` | 10 | 6,028 | -756 to +1,081 | 121 | 30 |
| `ch` only | 20 | 5,866 | | 112 | 30 |
| Only `iron` | 10 | 5,799 | -1,056 to +922 | 107 | 31 |

Rotating beat all five once by 3,062 points (95% range +1,912 to +4,212). With the same turn and level, its new quests had stated odds 11 to 16 points above `ch` only during turns 40-79, against about 8 for all five once. No single item clearly beat `ch`, and four items were not clearly worse than five, so this isn't one special item or a bonus for completing the set. Buying more evenly goes with easier quests, but why isn't known. One guess is a separate hidden stat per item type, with quests testing the weakest ones.

The +1 set beat the same spending on `cs` by 1,224 points (95% range +456 to +1,991), so variety matters for +1 items too. All the arms' wins matched their labels, and nobody took a trap. These ranges come from 19 comparisons, so the weaker ones could be chance, but the gain from rotating is far too large for that.

**Buying the +1 set before rotating showed no clear gain.** 60 more live games, 30 per arm and alternating, played until death. Rotating alone averaged 11,165 (lowest 6,113, highest 16,257). Buying each +1 item first and then rotating averaged 12,383 (lowest 5,723, highest 17,954). The difference is +1,217 points, with a 95% range of -282 to +2,717. It may be a real gain, but this test can't show it, so the solver doesn't buy +1 items.

## 4. How the solver plays

The strategy follows from three facts from [section 3](#3-what-the-game-does):

- **Turns are the scarce resource.** Difficulty rises with every turn, and by about turn 100 almost no job can be won.
- **Score is not spent.** Score counts rewards earned, and buying items doesn't reduce it, so gold is only useful for spending.
- **A job's label gives its real odds**, except for one trap that can be recognised from its text.

So the aim is to earn as much as possible per turn while the board is still winnable, turning gold into lives and levels along the way. Reaching 1000 points is a requirement, not the goal, so the solver keeps playing until the dragon dies.

### Each turn

The solver makes one move, checked in this order. The first rule that applies wins:

| # | Condition | Move | Why |
| --- | --- | --- | --- |
| 1 | Below 3 lives, with at least 50 gold | Buy a healing potion | Dying ends all earning, and a life is cheap |
| 2 | Below 3 lives, with under 50 gold | Take the best job, buying nothing | Saves the gold for a potion instead of an upgrade |
| 3 | 3+ lives, before turn 70, with at least 350 gold | Buy a +2 level item | The most levels per turn |
| 4 | Anything else | Take the best job | |

If the shop refuses a purchase, the solver takes a job in the same step, then tries again on its next turn if it still wants the item. It doesn't retry at once: a refusal costs a turn, gold and item would be the same, and only a job earns anything. Refusals look rare, with none in the 3,141 purchases of the 60-game item test, whose script uses the same gold checks. Failed calls are a separate case, covered in [section 5.1](#51-backend).

### Job choice (`RiskAwareQuestSelector`)

1. **Decode** the quest. Quests with `encrypted: 1` are Base64, `encrypted: 2` are ROT13. The id, message and difficulty are all decoded. Unknown schemes are skipped rather than guessed.
2. **Look up the chance of success**: the measured odds of its label (`Probability`), or 0 if its text identifies it as a trap (`QuestType`). Level isn't a separate input, because the labels and rewards already reflect it.
3. **Score it** by expected value:

   ```
   expected value = chance × reward − (1 − chance) × value of a life
   ```

4. **Pick** the highest expected value; ties go to the job expiring sooner. A trap always ranks last, so it's only taken when the whole board is traps. If every job has a negative value it still plays the best one, because waiting only makes the board harder.
5. **Stay above the trap line.** A job whose win would take state reputation to -10 ranks last too, unless nothing else is left. State is worked out from the jobs won, since checking it costs a turn. Traps then never appear, so they can't take up places on the board. In practice this limits most games to 4 steal wins.

### Value of a life

Losing a life is a cost in gold, so it can be weighed directly against a reward:

| Lives | Value of a life | Why |
| --- | --- | --- |
| 2 or more | 150 gold | A 50-gold potion, plus the turn spent buying it, which is worth about one job (roughly 100 gold) |
| 1 | 450 gold (3×) | Losing it ends the game and all future earning |

This sets the smallest reward at which a job is worth more than doing nothing. The chances are the solver's odds, measured on the first 74 games, and the full 110 games in [section 3.2](#32-difficulty) are within 2 points of them:

| Label | Chance | Minimum reward, 2+ lives | Minimum reward, last life |
| --- | --- | --- | --- |
| Sure thing | 100% | any | any |
| Piece of cake | 96% | 7 | 19 |
| Walk in the park | 84% | 29 | 86 |
| Quite likely | 79% | 40 | 120 |
| Hmmm.... | 68% | 71 | 212 |
| Gamble | 49% | 157 | 469 |
| Risky | 40% | 225 | 675 |
| Rather detrimental | 33% | 305 | 914 |
| Playing with fire | 20% | 600 | 1,800 |
| Suicide mission | 5% | 2,850 | 8,550 |
| Impossible, or a trap | 0% | never | never |

For example, here is one board valued at different numbers of lives:

| Job | Chance | Reward | Value, 2+ lives | Value, last life |
| --- | --- | --- | --- | --- |
| Risky | 40% | 400 | **70** | -110 |
| Walk in the park | 84% | 61 | 27 | -21 |
| Sure thing | 100% | 20 | 20 | **20** |
| Trap labelled "Sure thing" | 0% | 344 | ranked last | ranked last |

With lives to spare it takes the risky job for the bigger payoff, and on its last life it takes the guaranteed 20 gold. With 2 lives or fewer it would also heal first if it had 50 gold.

### Upgrades and healing (`BalancedShoppingStrategy`)

- **Which item:** it rotates through the five +2 items, always buying the one it has bought least, because that scored far higher than any other buying rule tested ([section 3.5](#35-items)). Ties go alphabetically, so the order is `ch`, `iron`, `mtrix`, `rf`, `wingpotmax`, then `ch` again. Purchases are counted from the game's own moves, since the API doesn't list what's owned. An item missing from the `Item` catalogue is never bought.
- **Why only +2 items:** +1 is cheaper per level (100 gold against 150), but +2 uses half the turns per level, and turns are what run out. Saving up costs little, because early jobs pay so little that an early level adds only a few gold per job.
- **Why stop at turn 70:** a level only raises the rewards of jobs posted after it, and few are still winnable by then. Later gold goes on potions.
- **Why levels at all:** each level adds about 5-6 gold to the reward of new jobs and, below about level 20, improves the labels of jobs already on the board.
- **Why the 350 threshold:** it's the item's price plus a 50-gold reserve, so a potion stays affordable after any upgrade.
- **Lives:** it heals back to 3 and stops.

Apart from rotating, the upgrade rules and the choice of 3 lives showed no clear gain over the alternatives, so they were chosen for their logic and simplicity. Those comparisons came before rotating and weren't repeated with it. The comparisons are under [Live results](#live-results).

### The resulting mix

Average per game over 10 live games played until the dragon died, before the trap-line rule was added:

| Turns | Jobs won | Jobs failed | Avg reward per win | Potions | +2 items |
| --- | --- | --- | --- | --- | --- |
| 0-14 | 13.1 | 0.4 | 46 | 0.4 | 1.1 |
| 15-29 | 9.7 | 0.7 | 120 | 0.7 | 3.9 |
| 30-49 | 10.0 | 1.5 | 223 | 1.5 | 7.0 |
| 50-69 | 6.9 | 4.4 | 241 | 3.4 | 5.3 |
| 70+ | 2.9 | 21.4 | 287 | 19.4 | 0 |
| **Total** | **42.6** | **28.4** | **152** | **25.4** | **17.3** |

- **Moves:** about 62% were jobs and 38% were purchases.
- **Gold:** nearly all of it gets spent, about 80% on +2 items and 20% on potions.
- **Phases:** early turns build levels from safe, low-paying jobs. Turns 30-69 earn the most, about 60% of the score. After turn 70 most jobs fail, and potions mainly buy more attempts until the gold runs out.

### Tuning

All of these live in `backend/src/main/resources/application.yml` under `mugloar.selector` and `mugloar.shopping`, and are validated at startup:

| Setting | Value | Effect |
| --- | --- | --- |
| `selector.life-cost` | 150 | Value of a life; higher plays safer |
| `selector.min-chance-healthy`, `-wounded`, `-critical` | 0 | Optional minimum chance at 3+, 2 and 1 lives. Off, because holding back scored lower |
| `shopping.heal-below-lives` | 3 | Buy potions below this many lives |
| `shopping.gold-reserve` | 50 | Gold left over after an upgrade |
| `shopping.small-upgrades-until-turn` | 0 | Allow +1 items before this turn. 0 means never |
| `shopping.upgrades-until-turn` | 70 | Stop buying upgrades from this turn |

The life cost was only tested as part of a whole strategy, and turn 70 is just the better of the two cutoffs tried, so better values may exist.

## 5. Implementation

With Docker, edits on the host are picked up automatically: Vite hot-reloads the frontend, and the backend recompiles and restarts within a few seconds. Without Docker, only the frontend reloads, so stop the backend and run `bootRun` again.

- After changing dependencies in `build.gradle` or `package.json`, run `docker compose restart backend` or `docker compose restart frontend`.
- Dependencies live in Docker volumes, so an IDE on the host can't see them and may flag imports as unresolved.
- `docker compose up -d` starts in the background, `docker compose logs -f backend` follows one component's logs, and `docker compose down` stops it. `docker compose down -v` also deletes the dependency and build caches, so the next start downloads everything again.

### 5.1 Backend

Java, Spring Boot, Gradle. `gateway` talks to the game server, `solver` decides how to play, `game` holds the sessions of web games, and all of them share `domain`. The domain objects and strategies are plain Java with no Spring dependency, so they are easy to unit-test and reuse.

```
com.mugloar
├── domain/         GameState, Quest, ShopItem, SolveResult…; catalogues: Probability, QuestType, Item
├── gateway/        MugloarGateway (domain-only contract), MugloarMapper (MapStruct), QuestDecoder (Base64/ROT13), error types
│   ├── client/     MugloarClient (RestClient), MugloarRetry
│   └── dto/        The game server's JSON, named ...DTO
├── solver/         AutoPlayer (turn loop), TurnEvent, GameResult
│   └── strategy/   QuestSelector + RiskAwareQuestSelector, ShoppingStrategy + BalancedShoppingStrategy, their settings
├── game/           GameService + in-memory GameSessionRepository for web games
├── web/            GameController, QuestView, ShopItemView, ApiExceptionHandler
├── cli/            SolverRunner (active with the "solver" profile)
├── config/         MugloarProperties (validated), bean wiring, CORS
└── util/           DecodeUtils, LogSanitizer, MathUtils
```

Design notes:

- **Layers.** Dependencies run one way: `web` → `game` → `solver` → `gateway`, `cli` → `solver`, and all of them use `domain`, which depends on nothing. Only `MugloarGateway` calls `MugloarClient`, and only `gateway` touches DTOs. A class is `public` only if another package uses it.
- **Strategy pattern.** `QuestSelector` and `ShoppingStrategy` are interfaces; a different playing style is a new class plus one bean definition.
- **Immutable state.** `GameState` is a record; each move returns a new snapshot (`state.after(result)`).
- **One code path.** Manual moves from the UI, autoplay steps and the CLI all go through `AutoPlayer`.
- **Sessions.** Web games are kept in memory, capped at 200 with least-recently-used eviction. Each game is locked while a move runs, so two tabs cannot interleave moves.

Robustness:

- Requests to the game server are at least `mugloar.min-request-interval` apart (340 ms, about 3 per second), across all games.
- Read-only calls (board, shop) are retried on network errors and 5xx, using Spring's `RetryTemplate` (`MugloarRetry`). A move that gets no answer is **not** sent again, because it may have counted.
- After an unanswered move, the solver reads the board again and chooses afresh. The next response corrects lives, gold, score and turn. Only state reputation can't be corrected that way, so a steal with no answer is counted as a win, which keeps the solver clear of the trap line. Checking reputation replaces the tracked value with the real one.
- A `429` means the server didn't act on the request, so any call, including a move, waits and is sent again: up to 3 times, for the server's `Retry-After` if it gives one of 5 seconds or less, otherwise for 1, 2 and then 4 seconds. If it still fails, the solver stops with `RATE_LIMITED` and the command line plays no more games.
- `410 Gone` from the game server ends the game cleanly.
- A job that expired between reading the board and taking it becomes a *skip*, and the board is read again. If the decoded id of an encrypted job is rejected, the raw id is tried once.
- If the shop can't be read, the solver tries again on each following turn, because without it there are no potions.
- After 5 failed moves in a row the solver stops, with `STALLED` on the command line and a 409 from the web autoplay step. Every command-line game also has a move limit (2000 by default).
- Unknown JSON fields from the game API are ignored, so additions upstream won't break parsing.

The REST API:

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/api/games` | Start a game (201) |
| `GET` | `/api/games/{gameId}` | Current state |
| `GET` | `/api/games/{gameId}/quests` | Board, decoded and ranked, with the solver's estimates |
| `GET` | `/api/games/{gameId}/shop` | Shop items, with the one the solver would buy this turn marked |
| `GET` | `/api/games/{gameId}/history` | Last 110 moves, newest first |
| `POST` | `/api/games/{gameId}/quests/{questId}/solve` | Take a job |
| `POST` | `/api/games/{gameId}/shop/{itemId}/buy` | Buy an item |
| `POST` | `/api/games/{gameId}/investigate` | Check reputation |
| `POST` | `/api/games/{gameId}/autoplay/step` | Let the solver make one move |

Ids are validated (`[A-Za-z0-9_-]{1,64}`; item ids `[a-z0-9]{1,32}`) before anything is sent upstream. Errors are RFC 9457 problem documents:

| Status | When |
| --- | --- |
| 400 | Malformed id |
| 404 | Unknown or evicted game |
| 409 | Job no longer on the board, unknown item, not enough gold, autoplay stopped after 5 refused moves |
| 410 | Game over |
| 502 | Game server unreachable or erroring (details logged, not returned) |
| 503 | Game server still rate limiting after the waits above |

A job the game server didn't answer returns 200 with a `SKIP` event saying it may have counted, not an error, so the log shows what happened and the board reloads.

Java code is formatted by [Spotless](https://github.com/diffplug/spotless) with google-java-format. `./gradlew check` and `./gradlew build` fail on unformatted code, and `./gradlew spotlessApply` fixes it.

### 5.2 Frontend

Vue 3 (Composition API, `<script setup>`), TypeScript, Pinia, Vite, Vitest + Vue Test Utils.

```
src/
├── api/          typed fetch client (timeouts, id and response validation, ApiError), DTO types
├── stores/game.ts  Pinia store: all game state and actions
├── components/   StatsBar, GameControls, QuestBoard, QuestNotice, QuestArt, ShopPanel, TurnLog, ErrorBanner,
│                 and the artwork: SunsetScene, DragonSketch, SketchFilters
└── utils/        formatting, input parsing, and the quest drawings (questArt.ts)
```

- **State management.** Components only render store state and call store actions; no component talks to the API. The store guards against double moves, keeps a capped log, and remembers the game id and target in `sessionStorage`, so reloading the page resumes the game.
- **Error handling.** Every failure ends up as one readable message in a dismissible banner. 410 switches the UI to game over, and a game the server has forgotten is dropped quietly. After a 409 or no response, the move may still have happened, so the store reloads the game, log and board from the server. Timeouts cover the whole response, including its body, and a response missing a field the UI reads counts as unreadable.
- **Input validation.** The autoplay target must be a whole number from 1 to 100,000; the field explains what's wrong, and the store re-checks.
- **Responsive and accessible.** One column below 56rem, with no horizontal scrolling at 390px. It uses semantic landmarks and lists, labelled controls, a progress bar with ARIA values, a status region that announces moves, visible focus rings and `prefers-reduced-motion`. It uses only widely supported CSS (grid, flex, custom properties; `color-mix` has a fallback), so it works in current Chrome, Firefox, Safari and Edge.
- **Design.** It follows the look of the game's own site: a mountain sunset, a gold-and-red title, a frosted panel with coral buttons, and ink drawings on torn parchment. All the art is original and drawn in code as inline SVG, so it scales cleanly and needs no image files. Each quest type has its own drawing, wash colour and name. A bar on each job shows the solver's chance of success, coloured from safe green to deadly red, and traps carry a warning.
- **The solver's pick.** One turn is one move, so exactly one thing is highlighted: the job the solver would take, or the shop item it would buy instead. When it would buy, the board says so and offers the purchase, because on a phone the shop is below all the jobs. The server decides using the same strategy as autoplay.

### Security

This is a local application: Docker binds both ports to `127.0.0.1`, it stores no personal data and it needs no secrets.

- **In place.** Every id is validated before it's forwarded upstream, and browser errors don't carry upstream details. Game text is only rendered as text, cross-origin requests from unlisted sites get a 403, and the production build has a CSP without inline code. Text from the game server is cleaned before it's logged.
- **Not suitable for public hosting as it stands.** There's no login or rate limit, so anyone could start games until the 200-game limit evicts everyone else's, and could get the server rate-limited upstream. A game id is the only access to a game. Slow upstream calls hold a game's lock and a server thread. Hosting it would also need HTTPS, a production CORS list and a real web server sending the headers in `preview.headers`.
- **Development only.** `bootRun` includes DevTools, whose error responses are more detailed, and the Docker containers run as root.

## 6. Testing

### Automated tests

```bash
cd backend && ./gradlew test
cd frontend && npm test
cd frontend && npm run typecheck
cd frontend && npm run lint
cd frontend && npm run e2e       # after: npx playwright install
```

The project has no CI, so these are the checks to run before pushing:

```bash
cd backend && ./gradlew check    # tests, formatting and a coverage report in build/reports/jacoco
cd frontend && npm run verify    # lint, typecheck, build and unit tests
cd frontend && npm run coverage  # optional: a coverage report in coverage/
cd backend && ./gradlew pitest   # optional: mutation testing of the decision logic, in build/reports/pitest
```

With Docker, use `docker compose exec backend ./gradlew test`, `docker compose exec frontend npm test`, `docker compose exec frontend npm run typecheck`, `docker compose exec frontend npm run lint` and `docker compose --profile e2e run --rm e2e`. The same goes for `check` and `verify`.

None of these calls the game server: the backend's tests also pass with the network switched off. `./gradlew liveContract` is the exception and only runs when asked: it starts one game on the public server and checks that its board and shop still match the code, which costs no turns.

`e2e` runs Chromium, Firefox and WebKit against the production build with the API mocked, so it needs neither the backend nor the game server. It covers what jsdom can't: the CSP and other security headers, keyboard focus after a move, computed colours, no sideways scrolling at 320, 768 and 1440px even with long unbroken text, and an axe accessibility check of each main state that fails on serious or critical problems. It also checks the built files for inline scripts, inline styles, `data:` URLs in CSS and third-party links. WebKit on Linux isn't Safari, and no real phone has been tried.

Backend tests sit in the same package as the class they test, so they can reach package-private members without those being made `public`. Tests mock `MugloarClient` and go through the real service and mapper (`Fixtures.gateway(client)`), so the conversion is exercised too.

`SimulatedMugloarClient` (test sources) is a seeded imitation of the game built on the rules in [section 3](#3-what-the-game-does): measured odds, traps at -10 state reputation, turn costs, levels that ease only jobs already on the board, and a difficulty curve measured per ten turns from the games that bought all five +2 items. `AutoPlayerTest` asserts that:

- the solver reaches 1000 points in at least 90% of 100 simulated games, and in at least 85% with 2% of calls failing
- until death, it never takes a trap while any other job is on the board, makes no call after the game ends, and tracks the same state reputation as the server
- 100 simulated games until death stay within 25% of the live game's average score, moves and level

The simulator draws wins from the same odds the solver believes, and its curve comes from real games, so it shows the solver works in a realistic game, not that a change scores more. That still takes live games.

Other layers that cross a boundary rather than testing one class:

| Test | What it covers |
| --- | --- |
| `GameClientWireTest` | The real HTTP client as `GameConfig` builds it, over real sockets: dropped connections, timeouts, cut-off JSON, and that a move is never resent |
| `GameApiIntegrationTest` | The whole backend over real HTTP, with `StubGameServer` as the game: a full game until death, error statuses, and that upstream text doesn't reach the browser |
| `SolverProfileTest` | The application started the way `./gradlew solve` starts it |
| `FrontendContractTest`, `contract.spec.ts` | Both sides against the JSON files in `contract/`, so a field added on one side fails a test |
| `RecordedQuestsTest` | 263 real jobs from the recordings, up to two of each quest type, label and encoding. All 403,700 recorded board entries passed the same checks when the sample was made |

With Docker, the `contract/` mount needs `docker compose up -d` once after updating to this version.

### Live results

Tests prove the code works, not that it scores, so every strategy change was also played on the live game. The current solver, measured on 2026-11-01:

| Run | Games | Min | Average | Max | Reached 1000 |
| --- | --- | --- | --- | --- | --- |
| Until the dragon dies | 30 | 98 | 10,387 | 17,193 | 29 of 30 |
| Stopping at 1000 (`-Ptarget=1000`) | 10 | 1,010 | 1,065 | 1,153 | 10 of 10 |

The first run averaged 262 moves and level 38, and its average has a 95% range of 9,289 to 11,485. Neither run took a trap or had a move refused or rate limited.

**The solver can miss 1000, rarely.** One of these 40 games died on turn 9 with 98 points. It lost three jobs in a row, labelled 79%, 79% and 84%, while holding 48 gold, 2 short of a potion. Those three losses together have a chance of about 0.7%. This happens before the first +2 item, so the buying rule can't affect it. Counting the until-death games that played these rules, 2 of 370 ended under 1000: about 0.5%, with a 95% range of about 0.1% to 2%.

Earlier solvers, run at different times rather than alternating, averaged 5,923 over 30 games buying only `ch` and 7,795 over 30 buying one of each and then `ch`. The controlled comparisons are in [section 3.5](#35-items).

Scores vary a lot between games and between runs, so small samples move around: 10 games without the trap-line rule averaged 6,488 and a later 30 averaged 5,448. The highest score seen in any game is 17,954, from a script buying the +1 set and then rotating. The real solver's highest is 17,193.

Besides rotating the +2 items, the trap-line rule is the one rule here with a clear gain. It was tested twice, until death:

| Comparison | Games per arm | Without the rule | With it | Difference (95% range) |
| --- | --- | --- | --- | --- |
| Script copying the solver, arms alternating | 53 | 5,899 | 6,592 | +694 (+188 to +1,199) |
| Real solver, one run after the other | 30 | 5,448 | 5,923 | +475 (-172 to +1,122) |
| Combined | 83 | | | +611 (+213 to +1,009) |

The real solver's lowest score also rose, from 2,855 to 4,336. The script's guard arm scored more than the real solver with the same rule (6,592 against 5,923) and no difference in the code explained it, so the true gain may be nearer the low end. The rule for keeping it was set before each result: a higher average, with a 95% range that excludes zero.

Each upgrade rule was compared with the previous rules (31 games, average 6,347), which bought +1 items before turn 15 and upgraded until the end. None of the differences is clear, with these results showing no sign of harm:

| Rule | Games | Average | Difference (95% range) |
| --- | --- | --- | --- |
| Stop upgrades at turn 50 | 10 | 5,629 | -718 (-1,711 to +275) |
| Stop upgrades at turn 70 | 10 | 6,725 | +378 (-449 to +1,206) |
| Skip +1 items | 10 | 5,912 | -435 (-1,934 to +1,064) |
| Both skip +1 and stop at turn 70, real solver | 10 | 6,488 | +141 (-853 to +1,135) |

The skip +1 average includes one game that failed its first two jobs and ended on 30 points. It had no gold for a potion, so the same start would have ended any strategy.

A live comparison found no clear winner between healing to 2 and to 3, so the solver keeps 3, and healing to 5 or never healing did not beat them:

| Heals up to | Games | Average | Lowest | Reached 1000 |
| --- | --- | --- | --- | --- |
| 2 lives | 17 | 5,998 | 2,645 | 17 of 17 |
| 3 lives | 17 | 6,050 | 3,878 | 17 of 17 |
| 5 lives | 5 | 5,495 | 3,919 | 5 of 5 |
| Never heals | 6 | 3,779 | 749 | 4 of 6 |

The gap between 2 and 3 is -52 points, with a 95% range of -952 to +847, so a real difference of several hundred points either way can't be ruled out.

The site's leaderboard lists scores up to about 1 billion. This solver does not get near that, because once every job is "Impossible" there is nothing left to earn. The site doesn't say how those scores were reached or whether the rules have changed since.
