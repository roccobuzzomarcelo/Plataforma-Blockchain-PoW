FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
RUN cd shared && mvn clean install -q

COPY coordinador/pom.xml coordinador/pom.xml
COPY coordinador/src coordinador/src
RUN cd coordinador && mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/coordinador/target/coordinator-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]