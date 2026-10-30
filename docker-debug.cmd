@echo off
setlocal
cd /d "%~dp0"

set "IMAGE=agent-orchestrator-mcp:local"
set "CONTAINER=agent-orchestrator-mcp-debug"
if not defined HOST_PORT set "HOST_PORT=9999"

docker info >nul 2>&1
if errorlevel 1 (
    echo Docker is unavailable. Start Docker and try again. >&2
    exit /b 1
)

rem Remove the previous debug container before removing its image.
docker container inspect "%CONTAINER%" >nul 2>&1
if not errorlevel 1 (
    docker container rm --force "%CONTAINER%"
    if errorlevel 1 exit /b 1
)

docker image inspect "%IMAGE%" >nul 2>&1
if not errorlevel 1 (
    docker image rm "%IMAGE%"
    if errorlevel 1 exit /b 1
)

docker build --tag "%IMAGE%" .
if errorlevel 1 exit /b 1

rem Run attached so application logs and Ctrl+C are available.
docker run --rm --init --name "%CONTAINER%" --publish "%HOST_PORT%:9999" --env SERVER_PORT=9999 "%IMAGE%" %*
exit /b %errorlevel%
