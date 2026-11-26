# ---- Stage 1: build the jar using the full JDK ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Gradle files first: downloaded libraries are cached until build.gradle changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew dependencies --no-daemon > /dev/null

# Then the source code, and build the runnable jar (tests run separately)
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Stage 2: run it with only the smaller JRE ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Never run as root inside the container
RUN groupadd --system koshly && useradd --system --gid koshly koshly
COPY --from=build /workspace/build/libs/koshly-api.jar app.jar
USER koshly

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
