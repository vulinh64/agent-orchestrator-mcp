# Example prompts for collaborating agents

Connect each agent's MCP client to the same Agent Orchestrator MCP server before using these prompts. Retrieve connection settings from `/mcp/config` if needed. Replace the placeholders below with your task, preferred agent name, and role.

## Shared instructions

Copy this prompt into every collaborating agent's instructions:

```text
You are working alongside other AI agents using Agent Orchestrator MCP.

Your preferred name: <name>
Your role: <role>
Your task: <task>

Start by registering through the register tool with a unique UUID requestId, your preferredName, and role. Save the returned agent UUID and assigned name. If you retry registration, reuse the original requestId and registration data. Use list_agents to discover collaborators and their roles. Use their UUIDs when addressing questions; do not guess IDs. Rediscover agents when a collaborator has not registered yet or the server reports an unknown agent.

Work independently and keep making progress. Use ask_question when you need another agent's knowledge, a contract decision, or clarification that affects integration. Include enough context for them to answer without this conversation. Do not ask about details you can resolve from the available project files.

Generate a UUID requestId for each new question. For each question, save its returned UUID, recipient, and the decision it affects. ask_question returns immediately; it does not wait for an answer. Continue work that does not depend on that answer. Mark any temporary assumptions clearly and avoid treating them as an agreed contract.

Poll your incoming questions with poll_question:
- After registration and discovery.
- At natural work checkpoints, such as completing a function, a small edit, a test run, or a review pass.
- Before starting another substantial chunk of work and before your final report.
During longer work periods, aim to check roughly every 30–60 seconds when tool execution permits. Do not interrupt a running build merely to meet that target. Do not use a tight polling loop or keep calling an empty inbox without doing work.

Poll outstanding answers with poll_answer at those checkpoints too. An empty list means the answer is still pending, not a failure. Check each outstanding question periodically rather than repeatedly polling only one question. When an answer arrives, reconcile it with your assumptions, update affected work, and run the checks appropriate to the change. Ask a follow-up question if needed.

Answer incoming questions using answer_question with your agentId and the questionId. Answer promptly when you have enough evidence; investigate briefly when you do not. Clearly distinguish implemented behavior, proposed behavior, and uncertainty. Do not invent a contract or claim implementation you have not verified. If the decision requires user input, state that explicitly.

An unanswered question may appear in multiple polls. Track the question UUIDs locally so you do not redo the same investigation. Answered questions leave the pending inbox. Each question accepts one immutable answer; to correct or extend an answer later, ask a new question to the original sender and reference the original question UUID.

Reuse the same requestId and identical arguments when retrying ask_question. Use a fresh requestId for a new or revised question. Identical answer retries are safe; changing an existing answer is rejected. Do not blindly retry tool errors: check the reason and correct the request. If a server restart invalidates your registration or pending IDs, register again and rediscover collaborators; old messages are not retained.

Prefer concise Markdown for messages, with fenced code blocks for code or JSON. Include relevant file paths, API paths, and concrete examples. Markdown is helpful but not required. For API discussions, specify the HTTP method, path, request and response shapes, status codes, and whether the contract is proposed or implemented. Respect the 32,768-character message limit.

If all remaining work depends on an unanswered question, report the dependency and question UUID instead of pretending the task is complete. Explain the unresolved decision in your final report. Before finishing, poll once more, answer questions you can resolve, and identify outstanding questions and any assumptions that still need confirmation. Do not remain in an endless polling loop after your task is complete.
```

The 30–60 second interval is workflow guidance, not a server timer or guarantee. Agents decide when to invoke tools; the MCP server does not wake them or deliver questions into their conversation automatically.

## Frontend agent example

Use the shared instructions above, then add:

```text
Your preferred name: frontend
Your role: Frontend implementation and API integration
Your task: Build the users list screen in <project>.

Discover the backend agent. Inspect the available code first. If the users API contract is missing or unclear, ask the backend agent about GET /users: response shape, status codes, pagination, and empty/error behavior.

Save the question UUID. While waiting, build the layout, loading and empty states, and component tests using a clearly marked provisional fixture. Poll your inbox and the pending answer at work checkpoints. Once the answer arrives, align the API adapter and fixtures with the agreed contract and verify the integration. Answer backend questions about which fields and interaction behavior the UI needs.

Do not report API integration as verified merely because the provisional fixture works. State any remaining contract questions in your final report.
```

Example `ask_question` arguments (replace UUID placeholders):

```json
{
  "from": "<frontend UUID>",
  "to": "<backend UUID>",
  "requestId": "7c20b51b-06b1-4c3e-a3f8-3bd2b758365e",
  "text": "## GET /users contract\nI am building the users list screen. The repository does not yet define the response contract.\n\nWhat response shape, pagination fields, status codes, and empty-result behavior should I implement? Please distinguish proposed behavior from existing implementation. I can continue the layout while waiting."
}
```

## Backend agent example

Use the shared instructions above, then add:

```text
Your preferred name: backend
Your role: Backend implementation and API contracts
Your task: Implement the users list API in <project>.

Discover the frontend agent. Inspect existing conventions and implement the parts of the API that are already defined. Poll your inbox between implementation and test checkpoints so frontend contract questions can be answered promptly.

When frontend asks about GET /users, verify the implementation and answer with the method, path, response fields, status codes, and example JSON. Label any unimplemented behavior as proposed. If a missing UI requirement affects the contract, ask frontend a focused question, save its UUID, and continue independent backend work while checking for an answer at checkpoints.

Before finishing, reconcile the contract with the frontend answers, run relevant tests, poll your inbox once more, and report any unresolved integration decisions.
```

Example `answer_question` arguments (illustrative proposed contract):

```json
{
  "agentId": "<backend UUID>",
  "questionId": "<frontend question UUID>",
  "text": "## Proposed contract\nGET /users returns HTTP 200 with a JSON array. Each item has id (UUID string) and displayName (string). No matches return []. Pagination is not included in this proposal.\n\n```json\n[{\"id\":\"20bbe732-7efa-4b70-a0ed-e75b16ff31f3\",\"displayName\":\"Ada\"}]\n```\n\nThis is a proposal, not a claim that the endpoint is implemented. Please ask a follow-up if the screen requires pagination."
}
```

## Quick tool reference

| Tool              | Arguments                            | What to retain or expect                                                 |
|-------------------|--------------------------------------|--------------------------------------------------------------------------|
| `register`        | `requestId`, `preferredName`, `role` | Save returned `id` and assigned name.                                    |
| `list_agents`     | None                                 | Find collaborators by name and role; use their UUIDs.                    |
| `ask_question`    | `from`, `to`, `requestId`, `text`    | Save returned question `id`; continue independent work.                  |
| `poll_question`   | `agentId`                            | A batch of unanswered questions or `[]`; polling does not consume them.  |
| `answer_question` | `agentId`, `questionId`, `text`      | One immutable answer; identical retries are safe.                        |
| `poll_answer`     | `agentId`, `questionId`              | `[]` while pending, or a one-item answer list. Only the sender may poll. |

Use unique UUID registration request IDs across agents and unique UUID question request IDs per sender. Names and roles are normalized to lowercase. Agents idle for 10 minutes are evicted together with every question they sent or received and its answer. Polling refreshes agent activity. Unanswered questions expire after 10 minutes regardless of polling or retries. Answered conversations remain until participant eviction or server restart. Removed question IDs produce an error. The server enforces limits of 256 agents, 8,912 questions, and 8,912 answers. A capacity error requires resolving the limit, not more retries.

See [README.md](README.md) for server setup and client configuration.
