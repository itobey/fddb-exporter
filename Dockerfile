FROM adoptopenjdk/openjdk11-openj9:jdk-11.0.11_9_openj9-0.26.0-alpine-slim
COPY target/fddbexporter*.jar /fddb-exporter/app.jar
EXPOSE 8080

RUN apk add --no-cache tzdata
ENV TZ=Europe/Berlin

CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -Dcom.sun.management.jmxremote -noverify ${JAVA_OPTS} -jar /fddb-exporter/app.jar
