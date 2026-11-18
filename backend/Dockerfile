# Azul Zulu CA 25.0.5.1+2 includes the November 2026 OpenJDK security fixes.
# Package SHA-256 values are published by api.azul.com/metadata/v1/zulu/packages/.
FROM alpine:3.24.4@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS java-base
# Optional local enterprise CA bundle is mounted only during downloads, never retained.
RUN --mount=type=secret,id=build_ca \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    apk add --no-cache ca-certificates fontconfig libstdc++ tzdata 'zlib>=1.4-r0'
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

FROM java-base AS build
ARG TARGETARCH
RUN --mount=type=secret,id=build_ca set -eu; \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    case "$TARGETARCH" in \
      amd64) java_arch=x64; java_sha=62e947b17d1191c1ab094f91e8487d1a12387f3dc8cedceff29c4c7989a66561 ;; \
      arm64) java_arch=aarch64; java_sha=0d6134ff74396b7776972686e3e848e420ff2cdf06b08560b51b84b3ca26af07 ;; \
      *) echo "Unsupported Java architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    wget -q -O /tmp/java.tar.gz "https://cdn.azul.com/zulu/bin/zulu25.38.201-ca-jdk25.0.5.1-linux_musl_${java_arch}.tar.gz"; \
    echo "$java_sha  /tmp/java.tar.gz" | sha256sum -c -; \
    mkdir -p "$JAVA_HOME"; \
    tar -xzf /tmp/java.tar.gz -C "$JAVA_HOME" --strip-components=1; \
    rm /tmp/java.tar.gz; \
    java -version


WORKDIR /workspace

# CVE-2026-114938: defense in depth, also used for native hosts awaiting a JDK update.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

COPY gradlew build.gradle settings.gradle gradle.lockfile ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src ./src
ARG SKIP_TESTS=true
ARG BUILD_COMMIT_SHA=docker
RUN export BUILD_COMMIT_SHA && if [ "$SKIP_TESTS" = "true" ]; then \
        sh ./gradlew bootJar -x test --no-daemon; \
    else \
        sh ./gradlew test bootJar --no-daemon; \
    fi

FROM java-base AS runtime
ARG TARGETARCH
RUN --mount=type=secret,id=build_ca set -eu; \
    if [ -s /run/secrets/build_ca ]; then export SSL_CERT_FILE=/run/secrets/build_ca; fi; \
    case "$TARGETARCH" in \
      amd64) java_arch=x64; java_sha=866e7af83b5cd6d0441ff48bb160c64737c05ef993743ab70d1bc185092fe5f9 ;; \
      arm64) java_arch=aarch64; java_sha=35fd9fa64d06c069a4a6bc5991a08db6fed0b738d3068ddaed5adaf26e2bf56f ;; \
      *) echo "Unsupported Java architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    wget -q -O /tmp/java.tar.gz "https://cdn.azul.com/zulu/bin/zulu25.38.201-ca-jre25.0.5.1-linux_musl_${java_arch}.tar.gz"; \
    echo "$java_sha  /tmp/java.tar.gz" | sha256sum -c -; \
    mkdir -p "$JAVA_HOME"; \
    tar -xzf /tmp/java.tar.gz -C "$JAVA_HOME" --strip-components=1; \
    rm /tmp/java.tar.gz; \
    java -version


WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads /app/logs \
    && chown -R app:app /app

COPY --from=build /workspace/build/libs/app.jar ./app.jar

EXPOSE 8080

USER app

# webp-imageio loads its JNI library from the application classpath.
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
