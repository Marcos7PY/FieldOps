FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./backend/pom.xml
COPY contracts ./contracts
COPY backend/analytics-service ./backend/analytics-service
WORKDIR /build/backend/analytics-service
RUN mvn -B clean package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache gcompat && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/backend/analytics-service/extracted/dependencies/ ./
COPY --from=build /build/backend/analytics-service/extracted/spring-boot-loader/ ./
COPY --from=build /build/backend/analytics-service/extracted/snapshot-dependencies/ ./
COPY --from=build /build/backend/analytics-service/extracted/application/ ./
USER app
EXPOSE 8084
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]