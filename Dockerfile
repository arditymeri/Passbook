# Backend image: build with a JDK, run on a JRE.
#
# The build stage carries Maven's whole downloaded repository and the JDK; the runtime stage
# carries a JRE and one jar. Keeping them separate is what makes the shipped image a few hundred
# megabytes rather than well over a gigabyte, which matters on any free tier with a disk quota.
#
# The frontend is NOT in here. It is a separate image (frontend/Dockerfile) serving static files
# and proxying /api to this one, so both answer on a single origin. Bundling the SPA into the jar
# was deliberately left out of scope in feature 021 as a packaging concern, and this keeps that
# boundary.

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY . .

# Tests are skipped on purpose: the integration-tests module needs a Docker daemon (Testcontainers),
# which is not available inside a build. CI is what runs them — see .github/workflows/ci-cd.yaml.
RUN chmod +x mvnw && ./mvnw -B --no-transfer-progress clean package -Dmaven.test.skip=true

# Wildcard rather than the literal version: the jar name changes at every release, and a hardcoded
# one turns "we bumped the version" into a build failure nobody expects.
RUN cp Launcher/target/Launcher-*-exec.jar /build/passbook.jar


FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is here solely for the healthcheck below. The alternative — bash's /dev/tcp — needs
# /bin/bash explicitly, because /bin/sh on Ubuntu is dash and does not have it, and reads as a
# puzzle at the exact moment someone is debugging why a container will not come up.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin passbook
WORKDIR /app
COPY --from=build /build/passbook.jar /app/passbook.jar
USER passbook

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes its heap from whatever the
# container is actually given, so the same image is right on a 1 GB box and a 4 GB one.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

# Spring property overrides, as -D flags. Deliberately not environment variables: several of this
# app's properties are hyphenated (app.demo-data.enabled, app.recurring.auto-post.enabled), and
# @ConditionalOnProperty against a hyphenated name is exactly where environment-variable relaxed
# binding is least predictable. A -D flag is the property name, verbatim, with nothing to guess.
ENV APP_OPTS=""

# /auth/status is one of the three endpoints SecurityConfig leaves public, so this needs no
# credentials. start-period is generous because the first boot runs Flyway migrations, and a Java
# application that is merely slow to start must not be reported as broken.
#
# Nothing gates on this — it is what makes `docker ps` answer "did the backend actually come up?"
# without reading logs, which is the question during a 90-second boot.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -fsS http://127.0.0.1:8080/api/v1/auth/status || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS $APP_OPTS -jar /app/passbook.jar"]
