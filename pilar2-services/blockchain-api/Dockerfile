FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
RUN cd shared && mvn clean install -q

COPY blockchain-api/pom.xml blockchain-api/pom.xml
COPY blockchain-api/src blockchain-api/src
RUN cd blockchain-api && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/blockchain-api/target/blockchain-api-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]