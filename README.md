# SecureBank Customer Support Bot

A secure, conversational customer-support assistant for a retail bank, built with
Spring AI on top of an OpenRouter-hosted LLM. The product is built **incrementally**;
this repository is at **iteration 5** — **customer tiers and a tier-based authorization
step**, on top of the MCP-server architecture from iteration 4.

## Iteration 5 — scope (current)

Every customer has a **tier** — Standard, Premium, or Privileged — and every
capability-bearing endpoint in `core-banking-api` runs an **authorization step**
(`CustomerAuthorization.require(customerId, capability)`) before doing any work. The
policy is one map, checked in Java:

| Capability | Standard | Premium | Privileged |
|---|:--:|:--:|:--:|
| balance enquiry, transaction details, statement request, change of address, KYC update | ✅ | ✅ | ✅ |
| **cheque book request** (there is a fee) | ❌ | ✅ | ✅ |
| **increase credit limit** (new) | ❌ | ❌ | ✅ |

A blocked call returns `403 CAPABILITY_NOT_PERMITTED` with a message naming the tier it
needs; the specialist agent relays that to the customer. `GET
/corebank/v1/customers/{id}/entitlements` returns a customer's tier and the capabilities
it permits.

### The MCP-server architecture (unchanged since iteration 4)

Each specialist agent is an **MCP client** of its own **MCP server** (SSE); the MCP
servers are HTTP clients of the Core Banking API. Everything is a separate deployable in
one Maven monorepo.

```
Browser chat UI ─HTTP─► /api/chat ─► CoordinatorAgent ("Ava")            bot :8080
 (bot/.../static)                        │  one delegating tool call per task
                   ┌────────────────────┼──────────────────────┐
                   ▼                    ▼                       ▼
            AccountsAgent         TransactionAgent          ServiceAgent      (bot)
                   │  MCP/SSE            │  MCP/SSE                │  MCP/SSE
                   ▼                    ▼                       ▼
        accounts-mcp-server    transaction-mcp-server     service-mcp-server
              :8091                  :8092                     :8093
          balance_enquiry     transaction_details        change_of_address
                              statement_request          cheque_book_request
                                                         kyc_update
                                                         increase_credit_limit
                   └──────── CoreBankClient (RestClient) ────────┘
                                       │ HTTP  (X-Customer-Id)
                          core-banking-api  /corebank/v1/**   :8090
                          (system of record + authorization step, in-memory mock store)
```

| Module | Port | Role |
|---|---|---|
| `bot` | 8080 | chat UI, `/api/chat`, `CoordinatorAgent` + the 3 specialist agents (MCP clients) |
| `accounts-mcp-server` | 8091 | MCP server — `balance_enquiry` |
| `transaction-mcp-server` | 8092 | MCP server — `transaction_details`, `statement_request` |
| `service-mcp-server` | 8093 | MCP server — `change_of_address`, `cheque_book_request`, `kyc_update`, `increase_credit_limit` |
| `core-banking-api` | 8090 | system of record + `CustomerAuthorization`: `/corebank/v1/**`, all rules |
| `banking-commons` | — | shared lib: `CoreBankApi` wire records, `CoreBankClient`, tool helpers |

**MCP tools take `customerId` as an argument** (the protocol has no ambient session),
but the specialist agent's `CustomerScopedToolCallback` **overwrites it with the
authenticated customer on every call** — so no model ever chooses whom it acts for. All
business rules — tier authorization, account ownership, date-range limits,
account-type/status eligibility, postcode/ISO/expiry validation, credit-limit bounds —
are enforced in `core-banking-api` in Java. Real authentication is still a later
iteration; the UI supplies a `customerId` and `/api/chat` falls back to demo `CUST-1001`.

The specialist agents open their MCP connections **lazily** on first use, so the bot
(and its tests) start whether or not the MCP servers are running.

**Earlier iterations:** 1 — one agent, no tools. 2 — one agent with all six tools
calling `core-banking-api` directly. 3 — a coordinator delegating to three in-process
specialist agents. 4 — the tools moved into three MCP servers. Identity/auth, a real
datastore, and durable memory are still later iterations.

### Demo data

| Customer | Tier | Accounts |
|---|---|---|
| `CUST-1001` Priya Nair | **Privileged** | `ACC-1001-001` current (active, £2,000 credit limit), `ACC-1001-002` savings, `ACC-1001-003` current (**dormant**) |
| `CUST-1002` Tom Baker | **Premium** | `ACC-1002-001` current (active, £500 credit limit) |
| `CUST-1003` Dan Shaw | **Standard** | `ACC-1003-001` current (**dormant**) |

### Signing in

Every demo customer above is also a login — session-based (Spring Security), email as
username, and every demo account shares the one demo-only password below. Logging in is
what fixes which customer the bot acts for; nothing in the chat UI or the request body
can act for a different customer than the one you signed in as (see `SecurityConfig`).

| Username | Password |
|---|---|
| `priya.nair@example.com` | `SecureBank-Demo1` |
| `tom.baker@example.com` | `SecureBank-Demo1` |
| `dan.shaw@example.com` | `SecureBank-Demo1` |

This is a demo login only — an in-memory `UserDetailsService` seeded at startup, not
backed by anything resembling production credential storage. Swapping in a real
identity provider is the only change a later iteration needs; everything downstream
already trusts `customerId` exactly the way it does today.

## Prerequisites

- Java 25
- No local Maven install needed — use the bundled wrapper (`./mvnw`)
- An OpenAI-compatible API key (defaults target [OpenRouter](https://openrouter.ai))

## Configuring credentials

The API key is **never** stored in `application.properties` or committed to git.
Provide it one of two ways:

### Option 1 — environment variables (recommended)

```bash
export OPENAI_API_KEY=sk-or-...

# Optional overrides (defaults shown)
export OPENAI_BASE_URL=https://openrouter.ai/api/v1
export OPENAI_CHAT_MODEL=cohere/north-mini-code:free
export OPENAI_CHAT_TEMPERATURE=0.5

# Ports and wiring (defaults shown)
export SERVER_PORT=8080                              # bot
export COREBANK_API_PORT=8090                        # core-banking-api
export ACCOUNTS_MCP_PORT=8091 TRANSACTION_MCP_PORT=8092 SERVICE_MCP_PORT=8093
export COREBANK_API_BASE_URL=http://localhost:8090   # MCP servers -> core-banking-api
export ACCOUNTS_MCP_URL=http://localhost:8091        # bot -> MCP servers
export TRANSACTION_MCP_URL=http://localhost:8092
export SERVICE_MCP_URL=http://localhost:8093
```

### Option 2 — local override file

Create `bot/src/main/resources/application-local.properties` (git-ignored and
auto-imported by the bot's `application.properties`):

```properties
spring.ai.openai.api-key=sk-or-...
```

## Running

Five processes, in dependency order. The script does it for you (Ctrl-C stops all;
logs land in `./logs/`):

```bash
./run-all.sh
```

Or by hand:

```bash
./mvnw -q install -DskipTests                        # once, so modules see banking-commons
./mvnw -q -pl core-banking-api      spring-boot:run  # :8090
./mvnw -q -pl accounts-mcp-server   spring-boot:run  # :8091
./mvnw -q -pl transaction-mcp-server spring-boot:run # :8092
./mvnw -q -pl service-mcp-server    spring-boot:run  # :8093
./mvnw -q -pl bot                   spring-boot:run  # :8080
```

Then open <http://localhost:8080> and sign in as one of the [demo customers](#signing-in)
above — Spring Security's login page comes up automatically.

Example (signed in as Dan Shaw — Standard tier):

```
You: What's the balance on ACC-1003-001?
Ava: Your Everyday Current account (ACC-1003-001) balance is £12.00.

You: Order me a cheque book, and raise my credit limit to £3,000.
Ava: Cheque books are available to Premium and Privileged customers only, and
     credit-limit increases to Privileged customers only — your account is on the
     Standard tier, so I can't do either.
```

## API

Everything under `/api/**` requires an authenticated session (see
[Signing in](#signing-in)) — `POST /login` first, then send the session cookie plus the
CSRF token as an `X-XSRF-TOKEN` header (read it back out of the readable `XSRF-TOKEN`
cookie Spring Security issues; `bot/resources/static/index.html`'s script does exactly
this). No request carries a `customerId` — there is nowhere to put one; which customer
the agent acts for comes only from who's signed in.

### `POST /api/chat`

```jsonc
// request — omit conversationId on the first turn
{ "conversationId": "…optional…", "message": "What's my balance?" }

// response — send conversationId back on the next turn to keep context
{ "conversationId": "3f1c…", "reply": "Your Everyday Current balance is £2,450.75." }
```

A blank `message` returns `400`. No session returns `401` (not a login-page redirect —
this path is for `fetch()`, not browser navigation). Over the per-IP rate limit (default
20/minute, `CHAT_RATE_LIMIT_MAX_REQUESTS`/`CHAT_RATE_LIMIT_WINDOW`) returns `429`.

### `GET /api/chat/me`

```jsonc
{ "customerId": "CUST-1001", "username": "priya.nair@example.com" }
```

The signed-in customer, for the chat UI's header.

### MCP servers — `GET /sse` (+ `POST /mcp/message`)

Each MCP server speaks MCP over SSE on its own port (8091–8093). `GET /sse` opens the
event stream and hands back the message endpoint:

```bash
curl -N http://localhost:8091/sse
# event:endpoint
# data:/mcp/message?sessionId=…
```

### SecureBank Core Banking API — `core-banking-api` :8090, `/corebank/v1/**`

The system of record. Every request needs an `X-Customer-Id` header; the account is
authorized against it. Non-2xx responses carry `{ "status", "code", "message" }`.

```bash
curl -H 'X-Customer-Id: CUST-1001' \
  http://localhost:8090/corebank/v1/customers/CUST-1001/entitlements
# {"customerId":"CUST-1001","tier":"PRIVILEGED","permittedCapabilities":[...]}

curl -H 'X-Customer-Id: CUST-1001' \
  http://localhost:8090/corebank/v1/accounts/ACC-1001-001/balance   # includes creditLimit

curl -X POST -H 'X-Customer-Id: CUST-1001' -H 'Content-Type: application/json' \
  -d '{"newLimit":3000.00}' \
  http://localhost:8090/corebank/v1/accounts/ACC-1001-001/credit-limit   # Privileged only

curl -X POST -H 'X-Customer-Id: CUST-1003' -H 'Content-Type: application/json' \
  -d '{"leaves":25}' \
  http://localhost:8090/corebank/v1/accounts/ACC-1003-001/cheque-books
# 403 {"code":"CAPABILITY_NOT_PERMITTED", ...} — Standard tier
```

Representative rules: **tier authorization first** (`403 CAPABILITY_NOT_PERMITTED` —
cheque books need Premium/Privileged, credit-limit increases need Privileged); then
account must belong to the caller (`403`); transaction range ≤ 90 days (`422`); statement
period ≤ 366 days and not in the future (`422`); cheque books and credit-limit changes
only on active current accounts (`422` / `409`); a credit-limit increase must be above
the current limit and within the cap (`422`); UK postcode and 2-letter ISO country
required (`422`); KYC document type in {PASSPORT, DRIVING_LICENCE, NATIONAL_ID} and not
expired (`422`).

## Tests

```bash
./mvnw test    # 49 tests across the modules, no network, no LLM
```

- `banking-commons/CoreBankClientTest` — `MockRestServiceServer`: the client sends
  `X-Customer-Id`, deserializes success (incl. `creditLimit`), and translates error
  bodies into `CoreBankClientException`.
- `core-banking-api/AccountApiControllerTest`, `ProfileApiControllerTest` —
  `@WebMvcTest` slices: the tier authorization step, per-tier cheque-book and
  credit-limit rules, entitlements, plus all the field/date/eligibility validation.
- `bot/CustomerSupportBotApplicationTests` — Spring context loads (no network); the MCP
  connections are lazy, so no MCP server need be running.
- `bot/ChatControllerTest` — web layer with the real `SecurityConfig` imported (so the
  auth/CSRF behaviour under test is what actually runs) and a mocked `CoordinatorAgent`:
  the agent is always called with the *signed-in* customer regardless of the request
  body, unauthenticated calls get `401` not a login redirect, a missing CSRF token gets
  `403`, blank messages and rate-limit overflow are rejected.
- `bot/RateLimiterTest` — the per-key fixed-window limiter, with a fake clock (no real
  sleeping): per-key budget, window rollover, independent keys.
- `bot/PiiRedactionTest` — the free-text redaction applied before a specialist agent logs
  its delegated request (emails, UK postcodes, long digit runs).

The specialist agents' MCP plumbing and the MCP servers' tool methods are exercised by
running the system (`./run-all.sh`), not unit-tested — consistent with not unit-testing
the agents' LLM calls.

### Evals

`bot/eval/CoordinatorAgentEvalTest` checks the behaviour the system prompts promise
against the **real** LLM and the real MCP/Core-Banking stack — tier declines are relayed
truthfully rather than faked, balances are quoted not invented, money can't be moved, and
a prompt-injection attempt can't talk the tier gate open. It's excluded from `./mvnw
test` (no LLM/network in the default suite) and runs only via the `eval` profile:

```bash
./run-all.sh
export OPENAI_API_KEY=sk-or-...
./mvnw -pl bot test -Peval
```

Without `OPENAI_API_KEY` set, or with the backend stack not running, the suite skips
itself (not a failure) with a message saying which.

## Core principles (inherited — see `CLAUDE.md`)

- OpenRouter via the Spring AI OpenAI-compatible client; model & temperature overridable
  by env var.
- Secrets only via env var or the git-ignored `application-local.properties`.
- Observability: `SimpleLoggerAdvisor` logs every prompt/response; every agent (the
  coordinator and each specialist) logs its own token usage via `AgentLogging`.
- Per-conversation sliding-window chat memory on the coordinator; specialists are
  stateless per delegation.
- Guardrails, business rules, and **authorization** live in Java, not in a prompt.
  `core-banking-api` runs `CustomerAuthorization.require(...)` for the endpoint's
  `Capability` before any work, then enforces every other rule; it returns a typed
  error the MCP tools relay. The signed-in customer is carried as tool context and
  stamped onto every MCP call by `CustomerScopedToolCallback`, never a model-chosen
  argument. Agents may *explain* tier limits but never *decide* them.
