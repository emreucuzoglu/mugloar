# Dragons of Mugloar: how the game works

The game's documentation describes its endpoints but hardly any of its rules, so everything the solver relies on was measured by playing the live game. This is the evidence behind [section 3 of the README](../README.md#3-what-the-game-does), and the history of the solver rules tested along the way. The raw recordings and analysis scripts aren't in the repository.

## Method

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
| Real solver validation | 285 | about 97,000 (estimated from moves played) | Checking each strategy change |
| All five +2 items | 60 | 13,447 | Whether owning the whole set changes anything |
| Item variety | 160 | 37,597 | What causes that gain, whether to keep rotating, and the +1 set |
| +1 set before rotating | 60 | 28,721 | Whether the +1 set adds anything to rotating |
| Stop turn when rotating | 90 | 38,267 | Whether to keep buying after turn 70 |
| Later stop turns | 107 | 82,831 | Whether to keep buying after turn 110, or never stop |
| **Total** | **1,399** | **about 379,000** | |

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

Some figures use part of the data: the quest ranges in `QuestType` come from the first 74 games, and the turn-by-turn mix in [the README's solver section](../README.md#4-how-the-solver-plays) comes from the solver games it names.

## Quest types

**How it was found.** A quest's free text follows fixed patterns, so quests were sorted by their opening words. `QuestType` holds what the 11,397 quests in the first 74 games showed about each type. Turn numbers are when each type was first seen.

| Turns | Quests | Level-0 reward (median) |
| --- | --- | --- |
| 0-20 | Fix, Clean, Negotiate, Transport, Sell, Write, Advertise | 5-34 |
| 0-27 | Escort, Steal delivery | 55-68 |
| 6-31 | Rescue (never at level 0) | 35 at any level |
| 16-36 | Investigate, Infiltrate, Kill (26-30): always encrypted, expire in 1-3 turns | 110-137 |
| 21+ | Defend: nearly the whole board from turn 35 | 67, rising to 271 at level 31+ |

**The trap.** `Steal super awesome diamond …` won 0 of 179 attempts, though nearly all claimed "Sure thing" or "Piece of cake" and they pay well. It was found in 30 targeted games, when wins fell far below what the labels promised. Traps are driven by state reputation, not by stealing itself, and the threshold is sharp. Across the two reputation experiments, 33% of new quests were traps at state -10 or below (of 9,447), and none were above it (of 46,432). The share rises the longer a game stays there, from 18% in the first 5 turns to about 49% after 20. Raising state again through Investigate or Infiltrate jobs stops new ones. Every game that reached -10 had at least 5 steal wins, since each costs 2 state. Ordinary `Steal … delivery` jobs stay as safe as their label, even at low state. Because nothing but expiry clears a trap, they build up and can fill all 10 slots. The solver now stays above -10, which raised scores (see [Earlier solvers](#earlier-solvers)). Earlier data had hinted otherwise, with games of 5+ steal wins averaging 6,144 against 5,758, but that wasn't a controlled comparison.

## Difficulty

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

## Turns

**How it was found.** The documentation only mentions turns for purchases, so edge-case probes in the first 7 games compared `turn` before and after each kind of call, including unknown and expired quests and calls on a finished game. Long games then showed how the board changes over time.

Solving, buying and checking reputation each cost one turn, even a refused purchase or a reputation check, whose response doesn't mention it. Reading the board or shop is free. The board always holds 10 quests, each usually lasting 7 turns. Solving an unknown or expired quest returns `400` and costs nothing, and every call on a finished game returns `410`. The server can also answer `429` under sustained load.

Difficulty rises with turns, and only buying the +2 items evenly holds it back: from turn 40, one round of all five makes up for about 8 turns ([Items](#items)). Games that buy little or stop buying are on a clock: by about turn 70 safe jobs are nearly gone, and by turn 100 almost everything is "Suicide mission" or "Impossible". A level-0 game that only passed turns saw nothing but "Impossible" from turn 100 until the check stopped at turn 424. A game whose buying keeps up never runs out of winnable jobs.

## Reputation

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

Apart from the [trap](#quest-types) threshold, reputation showed no effect on new quests or odds. The 164 games covered people 0 to 10.1, state -16 to 10 and underworld -4 to 0. With turn, level and quest type held fixed, the effects per reputation point (± one standard error, clustered by game) were:

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

## Items

**How it was found.** 285 shop reads and 3,359 purchases, including games that bought only one item, and comparing the labels of the same quests before and after an upgrade. The shop was identical in all 285 reads, up to turn 126 and level 39: it never refreshes and nothing sells out. Refusals are rare: 11 of the 3,359 purchases, and none of the 3,141 in the 60-game item test, whose script uses the same gold checks as the solver.

| Item | Cost | Effect |
| --- | --- | --- |
| Healing potion (`hpot`) | 50 | +1 life, stacks with no cap seen (11 reached) |
| `cs`, `gas`, `wax`, `tricks`, `wingpot` | 100 | +1 level |
| `ch`, `rf`, `iron`, `mtrix`, `wingpotmax` | 300 | +2 levels |

New quests pay about 6 gold more per level, at every level seen, up to 4,000+. Better labels only come early: each level improves the label of a quest already on the board by about 0.12 steps below level 10 and 0.04 at levels 10-19, and by almost nothing from level 20 up (0 steps across 1,071 quests above level 30). It never makes a label worse. New quests are not much easier at higher levels either: at a given turn, level 20 and level 59 see about the same labels. So past about level 20, upgrades mainly buy higher rewards. Nearly all of these games bought only `ch` as their +2 item, so these figures may only hold for that way of buying.

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

Rotating beat all five once by 3,062 points (95% range +1,912 to +4,212). With the same turn and level, its new quests had stated odds 11 to 16 points above `ch` only during turns 40-79, against about 8 for all five once. No single item clearly beat `ch`, and four items were not clearly worse than five, so this isn't one special item or a bonus for completing the set. Buying more evenly goes with easier quests, but why isn't known. One guess is a separate hidden stat per item type, with quests testing the weakest ones. The later tests below fit that guess, but don't prove it.

The +1 set beat the same spending on `cs` by 1,224 points (95% range +456 to +1,991), so variety matters for +1 items too. All the arms' wins matched their labels, and nobody took a trap. These ranges come from 19 comparisons, so the weaker ones could be chance, but the gain from rotating is far too large for that.

**Buying the +1 set before rotating showed no clear gain.** 60 more live games, 30 per arm and alternating, played until death. Rotating alone averaged 11,165 (lowest 6,113, highest 16,257). Buying each +1 item first and then rotating averaged 12,383 (lowest 5,723, highest 17,954). The difference is +1,217 points, with a 95% range of -282 to +2,717. It may be a real gain, but this test can't show it, so the solver doesn't buy +1 items.

**Buying for longer pays, and holds difficulty back.** 90 more live games, 30 per arm in a rotating order, rotated the +2 items until a stop turn and then bought only potions:

| Stop buying at turn | Average score | Lowest | Highest | Turns | Level | +2 items | 95% range against turn 70 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 70 | 10,321 | 5,871 | 16,892 | 262 | 38 | 19 | |
| 90 | 13,449 | 6,052 | 22,218 | 288 | 57 | 28 | +1,457 to +4,801 |
| 110 | 14,055 | 4,601 | 23,911 | 264 | 66 | 33 | +1,344 to +6,124 |

Stated odds of new quests stay up while buying continues, then fall within about 20 turns of it stopping:

| Turns | Stop at 70 | Stop at 90 | Stop at 110 |
| --- | --- | --- | --- |
| 70-79 | 50% | 58% | 50% |
| 80-89 | 26% | 52% | 42% |
| 90-99 | 14% | 39% | 39% |
| 100-109 | 6% | 20% | 41% |
| 110-119 | 3% | 11% | 31% |
| 120-139 | 1% | 3% | 12% |
| 140-199 | 0% | 0.3% | 1% |

Turn 110 wasn't clearly better than turn 90 (+606, 95% range -2,038 to +3,250), and scores varied more.

**Never stopping lets a game run on indefinitely.** 107 more live games, about 27 per arm in a rotating order, rotated the +2 items until a later stop turn, or for the whole game. Each game was capped at 3,000 moves. One turn-110 game, cut short when the run reached its time limit, is left out:

| Stop buying at turn | Games | Average score | Median | Lowest | Highest | Turns | 95% range against turn 110 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 110 | 26 | 16,439 | 18,938 | 6,282 | 28,349 | 323 | |
| 130 | 26 | 19,647 | 20,147 | 3,754 | 35,797 | 366 | -1,371 to +7,788 |
| 150 | 27 | 22,797 | 21,612 | 4,072 | 46,943 | 414 | +684 to +12,033 |
| Never | 27 | 363,156 | 16,667 | 5,004 | 894,724 | 1,299 | +183,697 to +509,737 |

The never-stop games split in two. 16 fell behind and died between turns 84 and 193, averaging 11,636. The other 11 were still alive at the 3,000-move cap, with 855,644 to 894,724 points, level about 5,700 and every new quest "Sure thing". By then they bought an item on about 19 turns in 20, because one job paid for many items. Their new quests already had easier labels by turns 40-79 (76% stated odds against 62% for the games that died), so the split seems to come from early luck with gold.

New quests' labels follow turns and +2 items together. From turn 40, a fit over 85,000 new quests from the last two tests gives a label step of about 0.18 per turn, with each full round of the five +2 items taking off 1.43 steps (weighted RMS misfit 0.19 steps). One round costs 1,500 gold and 5 turns and makes up for about 8 turns, so a game with gold to spare gets ahead, and one without falls behind. Those games bought the five in turn, so they can't tell whether rounds or the plain number of +2 items is what counts. The fewest purchases of any one item also fits the games that bought only `ch`, whose quests got harder with turns.

So the clock in [Turns](#turns) is an effect of not buying. From turn 1,000 the 11 capped games scored a steady 300 points per turn. That may be how the leaderboard's scores of up to about 1 billion were reached, though at that rate it would take about 3.3 million turns.

The never-stop median is below turn 110's, because the games that fall behind die sooner than they would have done if they had switched to potions. A rule that keeps buying while it keeps up, and switches to potions once it falls behind, might keep both advantages, but it hasn't been tested.

**Harvesting turns spare gold into score.** A game that keeps up gets about 7 gold per level from each job, so at level 20,000 one job pays about 150,000 gold. Buying one item a turn then spends about 500 turns on items after each job, which holds the score to about 300 points per turn. A game played with no move limit showed this: still alive with 3 lives, it had 6,206,591 points at turn 20,318 when it was stopped.

Harvesting takes a job instead of buying while at least 7 of the 10 jobs on the board are "Sure thing", and buys again when fewer are. Played on one live game, the first of two to keep up, its score grew with the square of the turn instead of in a straight line:

| Turn | Harvesting | Buying every time (the game above) |
| --- | --- | --- |
| 1,000 | 1,569,679 | 264,502 |
| 3,846 | 24,267,285 | 1,121,634 |
| 4,746 | 36,854,849 | 1,413,253 |

By turn 4,746 it had failed 8 jobs and still had 3 lives. It held 36 million unspent gold, because it only bought when the board slipped. Its score has been close to 1.6 × turn², which would pass the leaderboard's top score of 1,019,978,705 at about turn 25,000. This is one game, still running, and the extrapolation goes far beyond the data. The first game played with the rule never had 7 "Sure thing" jobs on its board and died at turn 143, as most games do, so harvesting doesn't change whether a game keeps up.

## Earlier solvers

These results come from solvers that came before rotating the +2 items and never stopping. They're kept because they're why the current rules are what they are. [Live results](../README.md#live-results) in the README has the current solver.

### Moves of an older solver

Average per game over 10 live games played until the dragon died, before the trap-line rule, rotating or never stopping were added, so this shows the older solver:

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

Earlier solvers, run at different times rather than alternating, averaged 5,923 over 30 games buying only `ch`, 7,795 over 30 buying one of each and then `ch`, 10,387 over 30 rotating until turn 70, and 14,893 over 30 rotating until turn 110. The controlled comparisons are in [Items](#items).

Scores vary a lot between games and between runs, so small samples move around: 10 games without the trap-line rule averaged 6,488 and a later 30 averaged 5,448. The highest score seen in any game is 894,724, from a never-stop test game capped at 3,000 moves. The real solver's highest is 584,376, at its 2000-move limit.

Besides rotating the +2 items and never stopping, the trap-line rule is the one rule here with a clear gain. It was tested twice, until death:

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
