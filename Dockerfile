# --- Etapa 1: compilar el jar dentro de Docker (no necesitas Maven para construir la imagen) ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- Etapa 2: imagen final ---
FROM eclipse-temurin:17-jre
WORKDIR /opt
ENV PORT 8080
EXPOSE 8080
COPY --from=build /build/target/*.jar /opt/app.jar
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
