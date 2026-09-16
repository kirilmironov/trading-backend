# ----- Build Stage -----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Копираме pom.xml и сваляме зависимостите
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копираме изходния код и компилираме JAR файла
COPY src ./src
RUN mvn clean package -DskipTests

# ----- Run Stage -----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копираме готовата програма от Build етапа
COPY --from=build /app/target/*.jar app.jar

# Отваряме порт 8080
EXPOSE 8080

# Стартираме приложението
ENTRYPOINT ["java", "-jar", "app.jar"]