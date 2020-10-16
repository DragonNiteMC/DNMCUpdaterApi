FROM openjdk:11.0.8-jdk-buster

COPY webapi.jar .

VOLUME /public
VOLUME /uploads
VOLUME /config.yml

EXPOSE 8080

CMD [ "java", "-jar", "-Dfile.encoding=UTF-8", "webapi.jar" ]
