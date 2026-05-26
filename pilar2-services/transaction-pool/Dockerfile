FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
RUN cd shared && mvn clean install -q

COPY transaction-pool/pom.xml transaction-pool/pom.xml
COPY transaction-pool/src transaction-pool/src
RUN cd transaction-pool && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/transaction-pool/target/transaction-pool-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]