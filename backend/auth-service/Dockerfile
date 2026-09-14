FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/auth-service ./backend/auth-service
WORKDIR /build/backend/auth-service
RUN mvn -B clean package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/keys && chown -R app:app /app
WORKDIR /app
COPY --from=build /build/backend/auth-service/extracted/dependencies/ ./
COPY --from=build /build/backend/auth-service/extracted/spring-boot-loader/ ./
COPY --from=build /build/backend/auth-service/extracted/snapshot-dependencies/ ./
COPY --from=build /build/backend/auth-service/extracted/application/ ./
USER app
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]