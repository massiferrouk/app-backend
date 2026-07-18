# Dockerfile de l'API StudUp (APP-115)
# Construction en deux etapes : on compile avec le JDK, on n'embarque que le
# JRE dans l'image finale -> image plus petite et plus sure.

# ---- Etape 1 : build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# On copie d'abord le wrapper Maven et le pom pour profiter du cache Docker :
# tant que les dependances ne changent pas, cette couche n'est pas reconstruite.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Puis le code source, et on package le jar (sans relancer les tests : la CI
# les a deja executes avant le merge).
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ---- Etape 2 : runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# On recupere uniquement le jar construit a l'etape precedente.
COPY --from=build /app/target/*.jar app.jar

# Railway fournit le port via la variable PORT ; Spring l'utilise
# (server.port dans application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
