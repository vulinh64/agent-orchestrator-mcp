# Build the application and a minimal Java runtime.
FROM maven:3.9.14-amazoncorretto-25-alpine AS build

WORKDIR /usr/src/project

COPY pom.xml ./

# Cache project and plugin dependencies independently of application sources.
RUN mvn -B dependency:go-offline

COPY src/ src/

RUN mvn -B clean package -DskipTests -Dfmt.skip=true

WORKDIR /usr/src/project/extracted
RUN jar xf ../target/app.jar

WORKDIR /usr/src/project

# Analyze application classes and dependencies, including nested Boot libraries.
RUN jdeps \
    --ignore-missing-deps \
    -q \
    --recursive \
    --multi-release 25 \
    --print-module-deps \
    --class-path 'extracted/BOOT-INF/lib/*' \
    extracted/BOOT-INF/classes \
    > deps.info || { cat deps.info; exit 1; }

# Include elliptic-curve cryptography for HTTPS connections.
RUN jlink \
    --add-modules "$(cat deps.info),jdk.crypto.ec" \
    --strip-java-debug-attributes \
    --compress 2 \
    --no-header-files \
    --no-man-pages \
    --output /jre-minimalist

FROM alpine:3.23.3 AS final

ENV JAVA_HOME=/opt/java/jre-minimalist
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN addgroup -S springgroup \
    && adduser -S springuser -G springgroup

WORKDIR /app

COPY --from=build /jre-minimalist "${JAVA_HOME}"
COPY --from=build /usr/src/project/target/app.jar ./app.jar

USER springuser

ENTRYPOINT ["/bin/sh", "-c", "exec java \
    -XX:+UseCompactObjectHeaders \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:MaxMetaspaceSize=512m \
    -jar app.jar \"$@\"", "--"]
