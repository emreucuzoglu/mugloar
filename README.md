# Dragons of Mugloar

A full-stack solution to both [Dragons of Mugloar](https://dragonsofmugloar.com/) adventures: a solver that plays the game through its REST API, and a web app for watching it or playing by hand.

## At a glance

| Requirement | How it's met | Evidence |
| --- | --- | --- |
| A web app based on the instructions | A Vue 3 app to play by hand, see how the solver rates every job, or autoplay, over a Spring Boot API that plays the live game | [Running the app](#2-running-the-app), [Implementation](#5-implementation) |
| Responsive and cross-browser | One column below 56rem, no sideways scrolling, and only widely supported CSS | `frontend/e2e/quality.spec.ts` runs Chromium, Firefox and WebKit at 320, 768 and 1440px, with an axe accessibility check ([Testing](#6-testing)) |
| State management | One Pinia store holds all game state; components only render it and call its actions | `frontend/src/stores/game.ts` ([Frontend](#52-frontend)) |
| A program that reaches 1000+ points | A solver that ranks jobs by expected value and rotates the +2 items | 40 of 40 live games on 2026-11-03; across 520 until-death games with these rules, 2 ended under 1000 ([Live results](#live-results)) |
| Error handling and input validation | Ids checked on both sides, responses checked in the browser, request pacing, `429` backoff, unanswered moves never resent, and one readable message per failure | [Backend](#51-backend), [Frontend](#52-frontend) |
| Unit tests | JUnit and Vitest, plus integration, contract, end-to-end and mutation tests | [Testing](#6-testing) |

To try it, run `docker compose up`, open http://localhost:5173, then press **Start a game** and **Autoplay**. Both play until the dragon dies by default. For a quick check, `docker compose exec backend ./gradlew solve -Ptarget=1000` plays one game to 1000 points.

The rules of the game had to be measured, because the API documentation barely describes them. That research is in [docs/RESEARCH.md](docs/RESEARCH.md).

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

Press **Start a game**, then either take jobs yourself or press **Autoplay**. Autoplay runs until the dragon dies, or until you press **Stop**. The **Autoplay to** field starts at 0, which means no target, and the score is shown on its own. A target above 0 makes it a controlled run: autoplay stops at that score, and the score is shown against it with a progress bar.

### Without the UI

The solver plays until the dragon dies. About a quarter of games keep up with their buying and go on for hours ([the item research](docs/RESEARCH.md#items)), so a controlled run can set a target score or a move limit, and each game stops at whichever comes first. Each move is logged, followed by a summary:

```bash
cd backend
./gradlew solve                    # one game
./gradlew solve -Pgames=10         # ten games
./gradlew solve -Ptarget=1000      # stop each game at 1000 points
./gradlew solve -PmaxMoves=2000    # stop each game after 2000 moves
```

With the containers running, use `docker compose exec backend ./gradlew solve` instead.

## 3. What the game does

The [API documentation](https://dragonsofmugloar.com/doc/) lists six endpoints and says almost nothing about the rules, so they were measured on the live game. [docs/RESEARCH.md](docs/RESEARCH.md) has the method, the data and the open questions. These are the rules the solver relies on:

- **A label gives the real odds**, at any level, from "Sure thing" (100%) to "Impossible" (0%). The exception is the trap, `Steal super awesome diamond …`, which never succeeds whatever its label says ([Difficulty](docs/RESEARCH.md#difficulty), [Quest types](docs/RESEARCH.md#quest-types)).
- **Traps appear only while state reputation is -10 or below**, which takes five steal wins, and stop if it recovers ([Reputation](docs/RESEARCH.md#reputation)).
- **Solving, buying and checking reputation each cost a turn**, even a refused purchase. Reading the board or the shop is free ([Turns](docs/RESEARCH.md#turns)).
- **Quests get harder with every turn, and only buying the five +2 items evenly holds that back.** A game whose buying keeps up can play on indefinitely, and one that falls behind soon has no job it can win ([Items](docs/RESEARCH.md#items)).
- **A potion gives +1 life**, with no cap seen, and each level adds about 6 gold to the reward of new jobs ([Items](docs/RESEARCH.md#items)).

## 4. How the solver plays

The strategy follows from three facts from [section 3](#3-what-the-game-does):

- **The game is a race.** Quests get harder with every turn, and only buying the +2 items evenly holds them back. A game that falls behind soon has no job it can win, and one that keeps up can play on indefinitely.
- **Score is not spent.** Score counts rewards earned, and buying items doesn't reduce it, so gold is only useful for spending.
- **A job's label gives its real odds**, except for one trap that can be recognised from its text.

So the aim is to earn as much as possible per turn and turn the gold into +2 items as fast as it comes in, healing when needed. Reaching 1000 points is a requirement, not the goal, so the solver keeps playing until the dragon dies unless a run sets a limit.

### Each turn

The solver reads the board, which is free, then makes one move, checked in this order. The first rule that applies wins:

| # | Condition | Move | Why |
| --- | --- | --- | --- |
| 1 | Below 3 lives, with at least 50 gold | Buy a healing potion | Dying ends all earning, and a life is cheap |
| 2 | Below 3 lives, with under 50 gold | Take the best job, buying nothing | Saves the gold for a potion instead of an upgrade |
| 3 | 3+ lives, with at least 350 gold, and fewer than 7 of the 10 jobs on the board are "Sure thing" | Buy a +2 level item | Keeps quests winnable, and the most levels per turn |
| 4 | Anything else | Take the best job | While the board is full of safe jobs, a job earns far more than an upgrade |

If the shop refuses a purchase, the solver takes a job in the same step, then tries again on its next turn if it still wants the item. It doesn't retry at once: a refusal costs a turn, gold and item would be the same, and only a job earns anything. Refusals are rare ([Items](docs/RESEARCH.md#items)). Failed calls are a separate case, covered in [section 5.1](#51-backend).

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

This sets the smallest reward at which a job is worth more than doing nothing. The chances are the solver's odds, measured on the first 74 games, and the full 110 games in [the difficulty research](docs/RESEARCH.md#difficulty) are within 2 points of them:

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

- **Which item:** it rotates through the five +2 items, always buying the one it has bought least, because that scored far higher than any other buying rule tested ([the item research](docs/RESEARCH.md#items)). Ties go alphabetically, so the order is `ch`, `iron`, `mtrix`, `rf`, `wingpotmax`, then `ch` again. Purchases are counted from the game's own moves, since the API doesn't list what's owned. An item missing from the `Item` catalogue is never bought.
- **Why only +2 items:** +1 is cheaper per level (100 gold against 150), but +2 uses half the turns per level, and turns are what run out. Saving up costs little, because early jobs pay so little that an early level adds only a few gold per job.
- **Why it never stops:** never stopping had by far the highest average of the stop turns tested, though not the highest median, because games that fall behind die sooner than if they had switched to potions ([Items](docs/RESEARCH.md#items)). For the brief's 1000 points the stop turn doesn't matter, because the rules only differ long after 1000.
- **Why it harvests:** once a game is well ahead, a job pays far more than an item adds, so with 7 or more "Sure thing" jobs on the board it takes the best job instead of buying. When labels slip below that, buying resumes and holds difficulty back. Early on, boards rarely have 7 "Sure thing" jobs, so this only changes games that already keep up. It has been tested on one live game so far ([Items](docs/RESEARCH.md#items)).
- **Why levels at all:** levels raise the rewards of new jobs, but the +2 items matter most because they hold back difficulty ([Items](docs/RESEARCH.md#items)).
- **Why the 350 threshold:** it's the item's price plus a 50-gold reserve, so a potion stays affordable after any upgrade.
- **Lives:** it heals back to 3 and stops.

Apart from rotating and never stopping, the upgrade rules and the choice of 3 lives showed no clear gain over the alternatives, so they were chosen for their logic and simplicity. Those comparisons came before rotating and weren't repeated with it. They're under [Earlier solvers](docs/RESEARCH.md#earlier-solvers).

### Tuning

All of these live in `backend/src/main/resources/application.yml` under `mugloar.selector` and `mugloar.shopping`, and are validated at startup:

| Setting | Value | Effect |
| --- | --- | --- |
| `selector.life-cost` | 150 | Value of a life; higher plays safer |
| `selector.min-chance-healthy`, `-wounded`, `-critical` | 0 | Optional minimum chance at 3+, 2 and 1 lives. Off, because holding back scored lower |
| `shopping.heal-below-lives` | 3 | Buy potions below this many lives |
| `shopping.gold-reserve` | 50 | Gold left over after an upgrade |
| `shopping.small-upgrades-until-turn` | 0 | Allow +1 items before this turn. 0 means never |
| `shopping.harvest-at-sure-things` | 7 | Take a job instead of an upgrade while at least this many jobs on the board are "Sure thing". 0 means always upgrade |
| `solver.target-score` | 0 | Score at which a command-line game stops (`-Ptarget`). 0 means no target |
| `solver.max-moves` | 0 | Moves after which a command-line game stops (`-PmaxMoves`). 0 means no limit |

The life cost was only tested as part of a whole strategy, so a better value may exist.

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

- **Layers.** Dependencies run one way: `web` → `game` → `solver` → `gateway` → `domain`, and `cli` → `solver`. `web`, `game` and `cli` also use `gateway` directly, for its error types and to start games, and `game` and `cli` read their settings from `config`, which wires `gateway` and `solver`. Nothing depends on a package above it, and `domain` and `util` depend on nothing, not even Spring or Jackson. Only `MugloarGateway` calls `MugloarClient`, and only `gateway` touches DTOs. A class is `public` only if another package uses it.
- **What the browser sees.** `web` sends its own records (`GameStateView`, `TurnEventView`, `QuestView`, `ShopItemView`), so the solver's memory of a game (tracked reputation and items bought) stays on the server.
- **Strategy pattern.** `QuestSelector` and `ShoppingStrategy` are interfaces; a different playing style is a new class plus one bean definition.
- **Immutable state.** `GameState` is a record; each move returns a new snapshot (`state.after(result)`).
- **One code path.** Manual moves from the UI, autoplay steps and the CLI all go through `AutoPlayer`.
- **Sessions.** Web games are kept in memory, capped at 200 with least-recently-used eviction. Each game is locked while a move runs, so two tabs cannot interleave moves.

Robustness:

- Requests to the game server are at least `mugloar.min-request-interval` apart (340 ms, about 3 per second), across all games.
- Read-only calls (board, shop) are retried on network errors and 5xx, using Spring's `RetryTemplate` (`MugloarRetry`). A move that gets no answer is **not** sent again, because it may have counted.
- After an unanswered move, the solver reads the board again and chooses afresh. The next response corrects lives, gold, score and turn. State reputation can't be corrected that way, so a steal with no answer is counted as a win, which keeps the solver clear of the trap line. Checking reputation replaces the tracked value with the real one.
- After an unanswered purchase, the solver buys nothing until a response has corrected its gold and lives, so it never buys twice on old numbers. The item is counted as bought, so the rotation moves on. If the purchase didn't count, that item simply gets bought one round later.
- A `429` means the server didn't act on the request, so any call, including a move, waits and is sent again: up to 3 times, for the server's `Retry-After` if it gives one of 5 seconds or less, otherwise for 1, 2 and then 4 seconds. If it still fails, the solver stops with `RATE_LIMITED` and the command line plays no more games.
- `410 Gone` from the game server ends the game cleanly.
- A job that expired between reading the board and taking it becomes a *skip*, and the board is read again. If the decoded id of an encrypted job is rejected, the raw id is tried once.
- If the shop can't be read, the solver tries again on each following turn, because without it there are no potions.
- After 5 failed moves in a row the solver stops, with `STALLED` on the command line and a 409 from the web autoplay step. A controlled run can also stop each game at a target score or after a number of moves; by default there is neither.
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

A job or purchase the game server didn't answer returns 200 with a `SKIP` event saying it may have counted, not an error, so the log shows what happened and the board reloads.

Java code is formatted by [Spotless](https://github.com/diffplug/spotless) with google-java-format. `./gradlew check` and `./gradlew build` fail on unformatted code, and `./gradlew spotlessApply` fixes it.

### 5.2 Frontend

Vue 3 (Composition API, `<script setup>`), TypeScript, Pinia, Vite, Vitest + Vue Test Utils.

```
src/
├── api/          typed fetch client (timeouts, id and response validation, ApiError), DTO types
├── stores/game.ts  Pinia store: all game state and actions
├── components/   StatsBar, OutcomeStrip, GameControls, QuestBoard, QuestNotice, QuestArt, ShopPanel, TurnLog, ErrorBanner,
│                 and the artwork: SunsetScene, DragonSketch, SketchFilters
└── utils/        formatting, input parsing, and the quest drawings (questArt.ts)
```

- **State management.** Components only render store state and call store actions; no component talks to the API. The store guards against double moves, keeps a capped log, and remembers the game id and target in `sessionStorage`, so reloading the page resumes the game.
- **Error handling.** Every failure ends up as one readable message in a dismissible banner. 410 switches the UI to game over, and a game the server has forgotten is dropped quietly. After a 409, a 410 or no response, the move may still have happened, so the store reloads the game, log and board from the server; after a 410 the game stays over whatever the reload says. Autoplay reads the board and shop once it stops, not every turn, to spare the public game server a request per turn, so the board is hidden and the shop shows no pick while it runs. Timeouts cover the whole response, including its body, and a response missing a field the UI reads counts as unreadable.
- **Input validation.** The autoplay target must be a whole number of 0 or more. It starts at 0, which means play until the dragon dies; the field explains what's wrong, and the store re-checks.
- **Responsive and accessible.** One column below 56rem, with no horizontal scrolling at 390px. It uses semantic landmarks and lists, labelled controls, a progress bar with ARIA values, a status region that announces moves, visible focus rings and `prefers-reduced-motion`. It uses only widely supported CSS (grid, flex, custom properties; `color-mix` has a fallback), so it should work in current Chrome, Firefox, Safari and Edge. The end-to-end tests cover their engines on Linux, but not Safari itself or a real phone.
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

`SimulatedMugloarClient` (test sources) is a seeded imitation of the game built on the rules in [section 3](#3-what-the-game-does): measured odds, traps at -10 state reputation, turn costs, levels that ease only jobs already on the board, and difficulty fitted from the games that rotated the +2 items: from turn 40 it rises with turns and falls with each round of the five +2 items. Its rewards rise 6 gold per level. `AutoPlayerTest` asserts that:

- the solver reaches 1000 points in at least 90% of 100 simulated games, and in at least 85% with 2% of calls failing
- when a move counts but its reply is lost (the move is applied, then a `503` comes back, for 5% of moves over 30 games), no purchase is refused, the +2 items stay evenly rotated, no avoidable trap is taken, and every answer that does arrive puts its gold, lives and turn back in step with the server
- until death, it never takes a trap while any other job is on the board, makes no call after the game ends, and tracks the same state reputation as the server
- of 100 simulated games until death, the share still alive at 2000 moves is within 15 points of the live share. The games that die stay within 25% of the live deaths' average score and moves, and the games still alive score within 25% of the live ones (563,079 on average in 31 simulated games, against 568,870 in 8 live games)

The simulator draws wins from the same odds the solver believes, and its curve comes from real games, so it shows the solver works in a realistic game, not that a change scores more. That still takes live games.

Other layers that cross a boundary rather than testing one class:

| Test | What it covers |
| --- | --- |
| `GameClientWireTest` | The real HTTP client as `GameConfig` builds it, over real sockets: dropped connections, timeouts, cut-off JSON, and that a move is never resent |
| `GameApiIntegrationTest` | The whole backend over real HTTP, with `StubGameServer` as the game: a full game until death, error statuses, and that upstream text doesn't reach the browser |
| `SolverProfileTest` | The application started the way `./gradlew solve` starts it, and that with its shipped defaults it plays one game until the dragon dies |
| `FrontendContractTest`, `contract.spec.ts` | Both sides against the JSON files in `contract/`, so a field added on one side fails a test |
| `RecordedQuestsTest` | 263 real jobs from the recordings, up to two of each quest type, label and encoding. All 403,700 recorded board entries passed the same checks when the sample was made |

With Docker, the `contract/` mount needs `docker compose up -d` once after updating to this version.

### Live results

Tests prove the code works, not that it scores, so every strategy change was also played on the live game. The current solver, measured on 2026-11-03:

| Run | Games | Min | Average | Max | Reached 1000 |
| --- | --- | --- | --- | --- | --- |
| Until the dragon dies, or 2000 moves (`-PmaxMoves=2000`) | 30 | 3,289 | 159,794 | 584,376 | 30 of 30 |
| Stopping at 1000 (`-Ptarget=1000`) | 10 | 1,023 | 1,094 | 1,172 | 10 of 10 |

The first run split in two, as the never-stop games in [the item research](docs/RESEARCH.md#items) did. 8 of the 30 games kept up and were still alive at the 2000-move limit, with 556,985 to 584,376 points, level about 3,700 and 3 lives. The other 22 fell behind and died after 79 to 198 moves, averaging 11,039. So the average, with a 95% range of 69,979 to 249,609, mostly counts how many games keep up, and the median is 13,221. Neither run took a trap or had a move refused or rate limited.

**The solver can miss 1000, rarely.** None of these 40 games did, but one game of an earlier solver, which differed only in when it stopped buying, died on turn 9 with 98 points. It lost three jobs in a row, labelled 79%, 79% and 84%, while holding 48 gold, 2 short of a potion. Those three losses together have a chance of about 0.7%. This happens before the first +2 item, so the buying rule can't affect it. Counting the until-death games that played these rules, 2 of 520 ended under 1000: about 0.4%, with a 95% range of about 0.1% to 1.4%.

Earlier solvers, and the rules tested along the way, are in [Earlier solvers](docs/RESEARCH.md#earlier-solvers). How the leaderboard's scores of up to about 1 billion may have been reached is in [Items](docs/RESEARCH.md#items).
