FROM mcr.microsoft.com/openjdk/jdk:11-ubuntu
VOLUME /tmp
WORKDIR /app
COPY target/ehrouter2eh-1.0-SNAPSHOT-jar-with-dependencies.jar /app/app.jar
COPY target/lib/applicationinsights-agent-3.3.1.jar /app/applicationinsights-agent.jar
COPY src/main/resources/applicationinsights.json /app/applicationinsights.json
COPY src/main/resources/log4j2.xml /app/log4j2.xml
CMD java -javaagent:/applicationinsights-agent.jar -jar /app/app.jar