FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS build

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

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads /app/logs \
    && chown -R app:app /app

COPY --from=build /workspace/build/libs/app.jar ./app.jar

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
