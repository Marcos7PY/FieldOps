FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/api-gateway ./backend/api-gateway
WORKDIR /build/backend/api-gateway
RUN mvn -B clean package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/backend/api-gateway/extracted/dependencies/ ./
COPY --from=build /build/backend/api-gateway/extracted/spring-boot-loader/ ./
COPY --from=build /build/backend/api-gateway/extracted/snapshot-dependencies/ ./
COPY --from=build /build/backend/api-gateway/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]