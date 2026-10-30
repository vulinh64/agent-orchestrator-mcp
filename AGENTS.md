# Project purpose

`agent-orchestrator-mcp` is a custom Model Context Protocol (MCP) server for AI
agents. At a high level, it will let agents register themselves and communicate
with one another through the server.

The server implements in-memory agent registration and question/answer messaging
through six Spring AI tools (default: stateless HTTP MCP endpoint `/mcp`):
`register`, `list_agents`, `ask_question`, `answer_question`, `poll_question`,
and `poll_answer`. Polls return immediately; no long polling or push delivery.
Tools use `@McpTool` and are discovered automatically by the Spring AI MCP
annotation scanner. Do not register a `MethodToolCallbackProvider` for them.
Discovery and polling advertise read-only hints; writes advertise idempotent,
non-destructive behavior. All tools operate on this server's in-memory state.

`MCP_PROTOCOL` selects `SSE`, `STREAMABLE`, or `STATELESS` (default) at startup.
`MCP_BANNER_FORMAT` selects `JSON` (default) or `TOON` for a startup connection
banner. The banner runs after server startup, using the actual bound port,
servlet context path, and configured transport endpoint. It prints a localhost
client `mcpServers` configuration; remote/Docker clients may need to adjust the
address. This format setting controls only the banner, not tool results or MCP
wire messages. Spring properties: `spring.ai.mcp.server.protocol` and
`mcp.banner.format`. SSE defaults to `/sse`; other transports default to `/mcp`.
TOON encoding uses `dev.toonformat:jtoon` after serialization through Spring's
injected `JsonMapper`; JSON and TOON share the same configuration object.

Public `GET /mcp/config` returns the client configuration, defaulting to JSON.
Use `?format=JSON` or `?format=TOON`; unsupported formats return HTTP 400.
The controller reuses `McpConnectionBanner` endpoint selection and rendering,
using the incoming request's base URL rather than the startup localhost URL.

Limits are 256 agents, 8912 questions, and 8912 answers. Creating an entry above
its limit fails without changing state. Questions and answers accept up to 32768
Java characters. All entries survive polling and remain until server restart;
there is no expiration, cancellation, deregistration, or persistence yet. Answered
questions no longer appear in `poll_question`, but their answers remain pollable.
Limits count retained entries, including answered questions.

Concurrency uses two independent `ReadWriteLock` instances through Lombok
`@Locked.Read`/`@Locked.Write`: agents/registrations and questions/answers.
`AgentRegistry` owns the registration maps and agent lock; `ConversationService`
owns question/answer maps and the conversation lock, and delegates participant
validation to the registry. `AgentTools` is a stateless MCP bridge containing
tool annotations and delegation only. Shared validation helpers stay internal
to the service package in `CoordinationValidation`.
Polls share read locks; writers exclude readers and other writers on their own
lock. Reads are concurrent, not lock-free. When both locks are needed, acquire
the conversation lock before the agent lock; never acquire them in reverse.
Keep questions and answers under the same lock for consistent pending views,
atomic request deduplication, capacity checks, and immutable answers.

Registration takes a caller-generated `requestId`, `preferredName`, and `role`;
save the returned UUID `id` and unique name. Names and roles are normalized
using `TextUtils.normalizeText` in the Agent compact constructor (root-locale
lowercase, trimmed and collapsed whitespace). Connection transport types use the
same normalization; URLs, request IDs, and message bodies retain their content.
Names may contain letters, digits, underscores,
and hyphens; collisions receive a `~2`, `~3`, etc. suffix. Agent and question IDs use `java.util.UUID`; tool callers supply UUID strings
as `from`, `to`, and `agentId`. Registration request
IDs are global strings. Question request IDs use `java.util.UUID` and are scoped to the sender. Reuse the same
request ID and identical payload on retries; conflicting reuse fails. Answers
are immutable and retryable with identical text. Poll answers by question ID:
an empty list means waiting, a one-item list means answered, and an unknown ID
produces an error. Polling questions returns all pending questions as a batch.

This is a trusted-agent coordination server with no authentication. Supplied
agent UUIDs are checked against the question sender/recipient, but are not proof
of identity. Disconnected agents remain registered until restart; registration
is not a liveness indicator. Use a single server instance for shared state.

# Stack and layout

- Java 25.
- Maven project: `com.vulinh:agent-orchestrator-mcp:1.0.0`.
- Spring Boot 4.1.2 with Spring MVC.
- Spring Boot Actuator for HTTP health checks; only `health` is exposed over HTTP.
- Spring AI 2.0.1 with `spring-ai-starter-mcp-server-webmvc`.
- Lombok configured as an annotation processor.
- Maven wrapper downloads Maven 3.10.0; the Docker builder uses Maven 3.9.14.
- `pom.xml`: dependencies, Java version, and build plugins.
- `src/main/java/com/vulinh/AgentOrchestratorMcpApplication.java`: application entry point.
- `src/main/java/com/vulinh/data/`: agent, question, and answer DTO records.
- `src/main/java/com/vulinh/config/`: Spring MCP tool configuration.
- `src/main/java/com/vulinh/service/`: MCP bridge, agent registry, conversation
  service, and shared internal validation.
- `src/main/resources/application.yaml`: application name and HTTP port.
- `Dockerfile`: application build and minimal Java runtime image.
- `docker-debug.cmd` / `docker-debug.sh`: rebuild and run locally with Docker.

Keep application components under `com.vulinh` so Spring Boot's default component
scan finds them. Use Spotify fmt with Google Java Format for Java formatting;
`format` runs automatically during local Maven builds. Docker packaging skips
formatting with `-Dfmt.skip=true`. Run `./mvnw.cmd fmt:format`
explicitly on Windows or `sh mvnw fmt:format` on POSIX. Follow the Maven layout. Annotate DTO records with Lombok `@Builder` and `@With`;
construct records through builders rather than direct `new` calls. Use
module imports (`import module java.base;`) where Java standard library imports
are needed; HTTP client tests also import `java.net.http` as a module. Tests use
Spring Boot's test starter and cover coordination rules, capacity/concurrency,
and MCP tool discovery, invocation, and errors over HTTP.

# Local development

Install JDK 25 and set `JAVA_HOME` for local Maven commands. The wrapper requires
network access on its first run and when dependencies are not cached.

From the repository root on Windows PowerShell:

```powershell
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

On a POSIX shell:

```sh
sh mvnw clean package
sh mvnw spring-boot:run
```

The application listens on port `9999` by default. `SERVER_PORT` overrides the
port for local runs. No Spring profiles are needed; do not add profile selection
to the Dockerfile or debug scripts.

Health checks are available at `http://localhost:9999/actuator/health` (adjust the
port when overriding it). A healthy application returns HTTP 200 with
`{"status":"UP"}`.

# Docker development

Start Docker with Linux container support, then run either:

```powershell
.\docker-debug.cmd
```

```sh
sh docker-debug.sh
```

Both scripts work relative to their own directory and perform this sequence:

1. Check that Docker is available.
2. Remove the previous `agent-orchestrator-mcp-debug` container if it exists.
3. Remove the `agent-orchestrator-mcp:local` image if it exists.
4. Build that image from the repository root.
5. Run the container attached, publishing host port `9999` to container port `9999`.

Logs remain visible in the terminal; use Ctrl+C to stop. The container is removed
when it exits. `HOST_PORT` overrides the host port; the scripts explicitly keep
`SERVER_PORT=9999` inside the container. Additional script arguments are forwarded
to the application. Do not override the container's server port through those
arguments without also updating its port mapping.

These scripts support debugging through application logs. They do not configure
a JVM remote debugger or publish a debugger port.

View startup and application logs using `docker logs -f <container-name>`.

Removing an image does not clear Docker's build cache. Normal layer caching stays
enabled. The builder copies `pom.xml` and runs `mvn -B dependency:go-offline`
before copying `src/`, so source edits reuse the dependency layer. Changes to
`pom.xml` invalidate dependency resolution and the subsequent build layers.
The scripts stop on build failure rather than running an older image.

# Docker design constraints

- Build with `maven:3.9.14-amazoncorretto-25-alpine`; run on `alpine:3.23.3`.
- Maven uses `<finalName>app</finalName>`. Build the executable Boot JAR with
  `mvn -B clean package -DskipTests -Dfmt.skip=true`, extract `target/app.jar` for module analysis,
  and copy it directly to `/app/app.jar` in the final image.
- Use `jdeps` on extracted application classes with Boot libraries on the
  classpath, following dependencies recursively. Passing every dependency JAR as
  a separate analysis root previously failed on an optional Swagger module.
- Use `jlink` to include the detected Java modules plus `jdk.crypto.ec` for HTTPS.
  Keep the runtime small; do not copy a full JDK into the final image.
- Run as the non-root user `springuser` from `/app`.
- This project does not need custom `spring-base-parent` or `spring-base-commons`
  downloads or manual Maven installation steps. Its Spring Boot parent resolves
  through Maven normally.
- Do not restore the external Java health check source or its `jdk.compiler`
  requirement. Actuator provides `/actuator/health`; the Dockerfile currently
  does not define a container `HEALTHCHECK` instruction.
- Keep Windows scripts as CRLF and shell scripts as LF, as specified by
  `.gitattributes`.

# Validation and maintenance

Prefer IntelliJ MCP tools whenever available for reading, searching, editing,
refactoring, inspecting, building, running tests, and debugging the project.
Use other tools when IntelliJ MCP is unavailable, lacks the required capability,
or fails; explain any relevant fallback briefly. Keep the Maven validation
requirements below even when IDE checks pass.

For Java changes, run the Maven build and add meaningful tests when introducing
behavior. For Docker changes, build the image and verify application startup on
the minimal runtime; `jdeps` cannot infer every reflective or dynamically loaded
dependency. The Docker build skips tests, so it is not a substitute for running
tests separately once tests exist.

For script changes, validate shell syntax (`sh -n docker-debug.sh`) and check both
scripts retain equivalent behavior. Run `git diff --check` before finishing.
Avoid modifying unrelated files or overwriting work already in progress. Update
this document when the setup or implemented capabilities change.
