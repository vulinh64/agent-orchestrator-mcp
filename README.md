# Agent Orchestrator MCP

## Introduction

A Spring AI MCP server that lets AI agents register, discover each other, and
exchange questions and answers while working in parallel.

For example, a frontend agent can ask a backend agent about a proposed API
contract before the application is running or an OpenAPI specification exists.
Asking and polling return immediately, so agents can continue other work while
waiting for a response.

The server provides six tools: `register`, `list_agents`, `ask_question`,
`answer_question`, `poll_question`, and `poll_answer`.

## Requirements

Choose one way to run:

| Run method | Requirements |
| --- | --- |
| Local Java | JDK 25 with `JAVA_HOME` configured; PowerShell or a POSIX shell |
| Docker | Docker Engine or Docker Desktop running Linux containers |

You also need an MCP client that supports the selected HTTP transport. All agents
must connect to the same server instance. Internet access is needed to download
Maven, dependencies, or Docker base images when they are not cached. Maven is
provided through the wrapper; a separate installation is unnecessary.

The application uses Spring Boot 4.1.2 and Spring AI 2.0.1.

## How to run

Run commands from the repository root. The default HTTP port is **9999**.

### Docker quick start

Windows PowerShell:

```powershell
.\docker-debug.cmd
```

Linux/macOS:

```sh
sh docker-debug.sh
```

The scripts rebuild `agent-orchestrator-mcp:local` and run the
`agent-orchestrator-mcp-debug` container with logs attached. They remove any
previous debug container and local image before building. Use Ctrl+C to stop;
the container is removed when it exits. Each restart clears the in-memory data.

To use another host port:

```powershell
$env:HOST_PORT = "19999"
.\docker-debug.cmd
```

```sh
HOST_PORT=19999 sh docker-debug.sh
```

Additional script arguments are passed to the application. For example:

```powershell
.\docker-debug.cmd --spring.ai.mcp.server.protocol=SSE --mcp.banner.format=TOON
```

Keep the container server port at 9999; change `HOST_PORT` to adjust the host
mapping. Host environment variables other than `HOST_PORT` are not automatically
forwarded by these scripts; use application arguments for protocol and logging.

### Local Java

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```sh
sh mvnw spring-boot:run
```

For a different port:

```powershell
$env:SERVER_PORT = "19999"
.\mvnw.cmd spring-boot:run
```

```sh
SERVER_PORT=19999 sh mvnw spring-boot:run
```

### Check the server

```powershell
Invoke-RestMethod http://127.0.0.1:9999/actuator/health
```

```sh
curl -fsS http://127.0.0.1:9999/actuator/health
```

A healthy server returns HTTP 200 with `status` equal to `UP`. Replace `9999`
throughout the examples if you selected another port.

## Connect your agents

The startup banner prints the active transport and MCP client configuration.
You can also retrieve the configuration over HTTP:

| Endpoint | Output |
| --- | --- |
| `GET /mcp/config` | JSON, the default |
| `GET /mcp/config?format=JSON` | JSON |
| `GET /mcp/config?format=TOON` | TOON as plain text |

Unsupported formats return HTTP 400. The HTTP endpoint uses the request's host,
port, and servlet context path. The startup banner uses localhost and the port
inside the running server; adjust it for Docker port mappings or remote clients.

Default JSON client configuration:

```json
{
  "mcpServers": {
    "agent-orchestrator-mcp": {
      "type": "http",
      "url": "http://127.0.0.1:9999/mcp"
    }
  }
}
```

Use this with clients that accept the `mcpServers` format. For other clients,
enter the HTTP transport and URL in their MCP settings. Configure both agents
with the same URL, then have each register separately.

### Transport and banner settings

| Environment variable | Spring property | Values | Default |
| --- | --- | --- | --- |
| `SERVER_PORT` | `server.port` | HTTP port | `9999` |
| `MCP_PROTOCOL` | `spring.ai.mcp.server.protocol` | `SSE`, `STREAMABLE`, `STATELESS` | `STATELESS` |
| `MCP_BANNER_FORMAT` | `mcp.banner.format` | `JSON`, `TOON` | `JSON` |

Select one transport per server instance:

- `STATELESS`: HTTP at `/mcp`; the default for independent requests.
- `STREAMABLE`: Streamable HTTP at `/mcp`.
- `SSE`: SSE connection at `/sse`; clients use transport type `sse`.

Custom connection paths use `spring.ai.mcp.server.sse-endpoint` for SSE or
`spring.ai.mcp.server.streamable-http.mcp-endpoint` for the HTTP transports.
The banner and configuration endpoint reflect those paths.

TOON is a display format for the client configuration. MCP wire messages and tool
responses remain JSON. Tool polls return immediately under every transport;
SSE transport connections can remain open independently of polling.

## First conversation

These are MCP tool calls, not REST endpoints. Let each agent invoke the tools
through its MCP client. Replace placeholders with the returned UUIDs.

| Step | Agent | Tool | Arguments |
| --- | --- | --- | --- |
| 1 | Frontend | `register` | `{"requestId":"frontend-session-1","preferredName":"frontend","role":"UI implementation"}` |
| 2 | Backend | `register` | `{"requestId":"backend-session-1","preferredName":"backend","role":"API implementation"}` |
| 3 | Either | `list_agents` | `{}` |
| 4 | Frontend | `ask_question` | `{"from":"<frontend UUID>","to":"<backend UUID>","requestId":"7c20b51b-06b1-4c3e-a3f8-3bd2b758365e","text":"What will GET /users return? Include status codes and an example response."}` |
| 5 | Backend | `poll_question` | `{"agentId":"<backend UUID>"}` |
| 6 | Backend | `answer_question` | `{"agentId":"<backend UUID>","questionId":"<question UUID>","text":"Proposed contract: HTTP 200 with an array of objects containing id (UUID) and displayName (string). Empty results return []."}` |
| 7 | Frontend | `poll_answer` | `{"agentId":"<frontend UUID>","questionId":"<question UUID>"}` |

Registration returns `id`, `name`, and `role`. Names and roles become lowercase
with surrounding whitespace trimmed and internal whitespace collapsed;
name collisions receive suffixes such as `backend~2`. Address agents using their
UUIDs. Asking returns the question and its UUID; save it to poll the answer.

`poll_question` returns all unanswered questions for the recipient, or `[]`.
`poll_answer` returns `[]` while waiting or a one-item list containing the answer.
Unknown agents/questions and invalid operations return explicit MCP tool errors.
Polling does not consume messages. Answered questions disappear from the pending
inbox, and answers remain available for repeated polling.

### Suggested instructions for each agent

See [EXAMPLE-PROMPT.md](EXAMPLE-PROMPT.md) for copyable collaboration instructions,
frontend/backend task examples, and guidance on polling while continuing work.

Copy this into the agent's task instructions, adapting the name and role:

```text
Use agent-orchestrator-mcp to coordinate with the other agents.
Register once with a unique requestId, your preferred name, and your role.
Save your returned UUID and use list_agents to discover collaborators.
Ask precise questions with relevant context; save each question UUID.
Continue independent work while waiting. Poll your inbox and outstanding answers
at task checkpoints, without a tight polling loop. Answer incoming questions
addressed to you. Reuse request IDs and identical arguments when retrying calls.
Use Markdown when helpful, including fenced code examples for API contracts.
Clearly distinguish proposed behavior from implemented behavior.
```

Markdown is optional; message text is stored unchanged. The server does not
schedule agents or make them poll automatically.

## Retries and limits

Registration request IDs are global strings; question request IDs are UUIDs unique per sender.
Retry with the same ID and identical data to retrieve the original entry.
Reusing an ID with conflicting data returns an error. Each question accepts one
immutable answer; repeating the same answer succeeds.

| Resource | Maximum retained entries |
| --- | --- |
| Registered agents | 256 |
| Questions, including answered questions | 8,912 |
| Answers | 8,912 |

Question and answer text is limited to 32,768 Java characters. Creating an entry
beyond a limit fails without changing state. Reaching the question limit still
allows answers to existing questions.

All state is in memory until server restart. There is no persistence, automatic
cleanup, expiration, cancellation, deregistration, or agent liveness detection.
Disconnected agents remain registered. This version assumes trusted clients;
UUIDs identify participants but do not authenticate them. Use one server instance
for agents that need to share state.

## Application logs

For the Docker debug scripts, logs are already attached. From another terminal:

```sh
docker logs --follow agent-orchestrator-mcp-debug
```

## Build and test

```powershell
.\mvnw.cmd clean package
```

```sh
sh mvnw clean package
```

Maven applies Spotify fmt formatting and runs tests before packaging the
executable JAR. Docker builds skip tests and formatting; run the Maven build separately when
validating code changes. You can also run the packaged application:

```sh
java -jar target/app.jar
```

## Troubleshooting

- **Docker unavailable:** start Docker Desktop/Engine with Linux containers.
- **Port unavailable:** use `HOST_PORT` for Docker or `SERVER_PORT` for local Java.
- **Slow localhost requests:** try `127.0.0.1` to avoid IPv6 fallback delays.
- **Agent receives no messages:** check that both agents use the same server,
  registered UUIDs, and regular polling. An empty answer poll means waiting.
- **Unknown IDs after restart:** register again; the previous in-memory state is gone.
- **Image removal fails:** an existing container may still reference the local
  image. Stop and remove that container if it is no longer needed, then rerun.

See [AGENTS.md](AGENTS.md) for repository layout and maintenance instructions.
