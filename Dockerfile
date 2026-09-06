# --- Compilation ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Les dépendances sont téléchargées avant de copier les sources : tant que le
# pom.xml ne change pas, Docker réutilise cette couche.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Exécution -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Un utilisateur sans privilège : si l'application est compromise, l'attaquant
# n'est pas root dans le conteneur.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app

USER app

# Le port 8081 par défaut sert au développement local, où 8080 est souvent pris.
# Dans un conteneur, rien ne gêne : on revient à la convention.
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
