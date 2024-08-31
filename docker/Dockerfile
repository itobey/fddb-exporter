FROM eclipse-temurin:21
RUN mkdir /opt/app
COPY target/fddbexporter-*.jar /opt/app/app.jar

EXPOSE 8080

ENV TZ=Europe/Berlin
CMD ["java", "-jar", "/opt/app/app.jar"]