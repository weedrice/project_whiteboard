FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
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

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads /app/logs \
    && chown -R app:app /app

COPY --from=build /workspace/build/libs/app.jar ./app.jar

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
