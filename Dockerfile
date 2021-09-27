FROM maven:3-openjdk-16

ARG VERSION=0.0.0

COPY . /app

RUN mvn -f /app versions:set -DnewVersion=$VERSION && \
    mvn -f /app clean install

ENTRYPOINT ["mvn", "-f", "/app", "spring-boot:run"]