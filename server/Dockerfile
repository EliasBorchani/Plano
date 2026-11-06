# Built from the repository root: the server compiles against the shared modules.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:installDist --no-daemon -q

FROM eclipse-temurin:21-jre
COPY --from=build /src/server/build/install/server /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
