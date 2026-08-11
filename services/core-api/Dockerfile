# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# Cache de dependências: copia só o necessário para resolver deps primeiro.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:resolve
# Agora o código-fonte.
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/core-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
