# Build with `docker build -t vertx-kuickstart .`
# Run with something like this: `docker run -m512M --cpus 2 -it -p 8080:8080 --rm vertx-kuickstart`

FROM adoptopenjdk/openjdk12-openj9:alpine-slim

ENV APPLICATION_USER vertx-kuickstart
RUN adduser -D -g '' $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

COPY ./build/libs/vertx-kuickstart.jar /app/vertx-kuickstart.jar
WORKDIR /app

CMD ["java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-Xtune:virtualized", "-jar", "vertx-kuickstart.jar"]