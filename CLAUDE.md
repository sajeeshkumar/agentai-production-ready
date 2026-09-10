# CLAUDE.md

Guidance for working in this repository.

## What this is

`SecureBank Customer Support Bot` — a conversational banking-support assistant built
with Spring AI over an OpenRouter LLM. Built incrementally.

**Iteration 5** (current): **customer tiers + a tier-based authorization step**, on the
multi-module MCP-server architecture from iteration 4. Every `CoreBankData.Customer` has
a `Tier` (STANDARD / PREMIUM / PRIVILEGED). `CustomerAuthorization.require(customerId,
capability)` runs first in every capability-bearing endpoint; the policy map gates
`CHEQUE_BOOK_REQUEST` (Premium+Privileged, there is a fee) and the new
`INCREASE_CREDIT_LIMIT` (Privileged only); a block is `403 CAPABILITY_NOT_PERMITTED`.
`GET /corebank/v1/customers/{id}/entitlements` returns tier + permitted capabilities.

The iteration-4 shape is unchanged: `CoordinatorAgent` ("Ava", renamed from
`SupportAgent`) delegates to three specialist agents; each is an **MCP client** of its
own **MCP server** (SSE); the MCP servers are HTTP clients of the Core Banking API.

```
bot :8080  CoordinatorAgent ─(in-proc tool)─► AccountsAgent/TransactionAgent/ServiceAgent
                                                     │ MCP over SSE
                                    accounts- / transaction- / service-mcp-server  :8091-8093
                                                     │ HTTP (X-Customer-Id)
                          core-banking-api  /corebank/v1/**  :8090  (+ authorization step)
```

| Module | Port | Role |
|---|---|---|
| `banking-commons` | — | `CoreBankApi` wire records, `CoreBankClient` (+ `create(...)` factory), `ToolSupport` |
| `core-banking-api` | 8090 | system of record; `CustomerAuthorization`; all business rules; `@WebMvcTest`-covered |
| `accounts-mcp-server` | 8091 | MCP server — `balance_enquiry` |
| `transaction-mcp-server` | 8092 | MCP server — `transaction_details`, `statement_request` |
| `service-mcp-server` | 8093 | MCP server — `change_of_address`, `cheque_book_request`, `kyc_update`, `increase_credit_limit` |
| `bot` | 8080 | chat UI, `/api/chat`, `CoordinatorAgent` + the 3 specialist agents (MCP clients) |

**Earlier:** 1 — one agent, no tools. 2 — one agent with all six tools calling
`core-banking-api` directly. 3 — coordinator + three *in-process* specialists. 4 — the
tools moved into three MCP servers. See `README.md` for full scope.

## Core principles (inherited from the baseline foundation — keep these)

1. **OpenRouter via Spring AI's OpenAI-compatible client.** `spring-ai-starter-model-openai`
   pointed at `https://openrouter.ai/api/v1` in the bot's `application.properties`. Base
   URL, model, temperature — and every port / MCP URL / Core Banking URL — are
   `${ENV:default}` placeholders. Never hardcode them.
2. **Secrets never in committed config.** `spring.ai.openai.api-key=${OPENAI_API_KEY}`,
   with an optional git-ignored `bot/src/main/resources/application-local.properties`
   (auto-imported) for local dev. Keep `.env` / `application-local.properties` / `logs/`
   in `.gitignore`.
3. **Observability by default.** The bot's shared `ChatClient` carries a
   `SimpleLoggerAdvisor` (`ChatClientConfig`); every agent — the coordinator and each
   specialist — logs its own token usage via `agent/AgentLogging`. Each MCP server logs
   `tool=<name> customer=<id> …`. Preserve these when adding agents or tools.
4. **Per-conversation memory.** `MessageWindowChatMemory` + `MessageChatMemoryAdvisor`
   keyed by a `conversationId`, on the coordinator only. Specialists are stateless per
   delegation. Process-local for now.
5. **Determinism (and authorization) live in Java, not the prompt.** Each
   capability-bearing endpoint calls `CustomerAuthorization.require(customerId,
   Capability.X)` as its first line — the authorization step — then enforces every other
   rule (account ownership, date-range limits, account-type/status eligibility,
   postcode/ISO/expiry validation, credit-limit bounds) and returns a typed `ApiError`.
   The MCP tools relay it; they don't re-decide. MCP tools take `customerId` as an
   argument (the protocol has no ambient session), but `CustomerScopedToolCallback` in
   the bot overwrites it with the authenticated customer on every call — no model ever
   chooses whom it acts for, and no prompt decides a tier limit (prompts may only
   *explain* one after a `403 CAPABILITY_NOT_PERMITTED`).
6. **Bundled Maven wrapper, Java 25.** Use `./mvnw`. It is a reactor build; run
   `./mvnw -q install -DskipTests` once so modules resolve `banking-commons`.
7. **Tests that need no credentials or network.** `MockRestServiceServer` test for
   `CoreBankClient` (`banking-commons`); `@WebMvcTest` slices for the Core Banking API
   (`core-banking-api`); context-load + mocked-`CoordinatorAgent` web-slice tests
   (`bot`). The bot's MCP connections are **lazy**, so its context loads with no MCP
   server running. Add fast, offline tests for new logic; don't add tests that call the
   LLM or a live MCP server.

## Layout

```
banking-commons/  inc.kodingkrafters.banking
  CoreBankApi                 wire records (nested); shared by core-banking-api and the client
  CoreBankClient              RestClient wrapper; CoreBankClient.create(baseUrl, timeouts); maps ApiError -> CoreBankClientException
  CoreBankClientException
  ToolSupport                 customerId(ToolContext) / parseDate / error / invalidDate helpers

core-banking-api/  inc.kodingkrafters.corebank        (:8090)
  CoreBankingApiApplication
  CoreBankData                in-memory seeded store (customers+Tier, accounts+creditLimit, transactions)
  Tier / Capability          STANDARD|PREMIUM|PRIVILEGED ; the enumerated capabilities
  CustomerAuthorization      the authorization step: tierOf / require(customerId, Capability) / permitted
  AccountApiController        /corebank/v1/accounts/**   (balance, transactions, statements, cheque-books, credit-limit)
  ProfileApiController        /corebank/v1/customers/**   (entitlements, address, kyc)
  CoreBankException + CoreBankApiExceptionHandler + CoreBankRules   rules -> typed ApiError

{accounts,transaction,service}-mcp-server/  inc.kodingkrafters.mcp.<name>   (:8091-8093)
  <Name>McpServerApplication  @Bean CoreBankClient (from ${corebank.api.base-url}) + @Bean ToolCallbackProvider(MethodToolCallbackProvider)
  <Name>Tools                 @Tool methods; first param is the injected customerId; call CoreBankClient
                              (service: change_of_address, cheque_book_request, kyc_update, increase_credit_limit)

bot/  inc.kodingkrafters.agents                        (:8080)
  CustomerSupportBotApplication
  config/ChatClientConfig     the shared ChatClient (+ SimpleLoggerAdvisor)
  agent/CoordinatorAgent      "Ava": system prompt + chat memory; .tools(the 3 specialist beans)
  agent/AgentLogging          shared per-agent token-usage logging
  agent/team/{AccountsAgent,TransactionAgent,ServiceAgent}   one @Tool entry point each; use their McpBackend
  agent/team/Team             package-private: pull customerId from ToolContext
  agent/mcp/McpBackend        lazy McpSyncClient over SSE -> ToolCallback[]  (one bean per server)
  agent/mcp/McpBackendsConfig  the three McpBackend beans (URLs from ${mcp.*.url})
  agent/mcp/CustomerScopedToolCallback   stamps the authenticated customerId into each MCP tool call
  web/ChatController          POST /api/chat  (reads customerId, falls back to DEMO_CUSTOMER_ID)
  web/ChatRequest, ChatResponse
  resources/static/index.html   the chat UI (demo customer selector)

run-all.sh                    starts core-banking-api, the 3 MCP servers, then the bot (Ctrl-C stops all)
```

## Build & run

```bash
./mvnw test                       # 36 tests across modules, no network, no LLM
./mvnw -q install -DskipTests     # once, so modules see banking-commons
./run-all.sh                      # all five services; http://localhost:8080 ; logs in ./logs/
```

## Conventions

- Spring Boot 4.x: web starter is `spring-boot-starter-webmvc`; `@WebMvcTest` is
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; mock beans use
  `@MockitoBean`. Boot 4 uses Jackson 3 (`tools.jackson`); don't inject a Jackson 2
  `ObjectMapper`. `CoreBankClient` decodes error bodies via
  `RestClientResponseException.getResponseBodyAs(...)` — no `ObjectMapper` dependency.
- Spring AI + MCP are all `${spring-ai.version}` (2.0.1), managed by `spring-ai-bom` in
  the parent POM. MCP server = `spring-ai-starter-mcp-server-webmvc` (SSE at `/sse`,
  messages at `/mcp/message`); MCP client = `spring-ai-starter-mcp-client` with its
  auto-config **disabled** (`spring.ai.mcp.client.enabled=false`) — the bot builds its
  own `McpSyncClient` per server in `McpBackend` so each specialist sees only its
  server's tools.
- MCP servers register tools with a `@Bean ToolCallbackProvider` wrapping the `…Tools`
  object via `MethodToolCallbackProvider.builder().toolObjects(...)`. A harmless
  `SyncMcpToolProvider: No tool methods found … []` WARN comes from the unused
  annotation-scanner path; tools are registered through `ToolCallbackConverterAutoConfiguration`.
- MCP tool methods take `customerId` as their **first** `@ToolParam` and do not use
  `ToolContext` (it does not cross the MCP boundary). The bot's `CustomerScopedToolCallback`
  fills that argument from the coordinator's tool context.
- Adding a capability to an existing specialist: add a value to `Capability`; add a
  `@Tool` method to that server's `…Tools` (customerId first); back it with a real
  `/corebank/v1` endpoint whose first line is `authorization.require(customerId,
  Capability.X)` and whose remaining rules raise `CoreBankException`; if the capability
  is tier-gated, add it to `CustomerAuthorization.POLICY`; mention it in the specialist's
  system prompt; add `@WebMvcTest` cases in `core-banking-api` (permitted tier ✅,
  blocked tier `403 CAPABILITY_NOT_PERMITTED`, validation). A whole new specialist = a
  new `*-mcp-server` module + an `agent/team/*Agent` + an `McpBackend` bean, registered
  in `CoordinatorAgent.tools(...)` and its routing prompt.
- Test ordering: `@WebMvcTest` caches one `CoreBankData` per test class, and
  credit-limit / address writes mutate it — don't assert a mutable field's exact value
  across methods (the credit-limit "applied" test asserts response fields, and the
  balance test only asserts `creditLimit` is present).
- Keep every system prompt honest about scope: the team can read balances/transactions
  and submit address/cheque-book/KYC/statement/credit-limit requests for the signed-in
  customer only, and cannot move money or act for anyone else. Tier limits are the API's
  to enforce; a prompt may explain a `CAPABILITY_NOT_PERMITTED` decline but must not
  pre-judge it. The coordinator confirms record-changing actions before delegating.
