FROM mcr.microsoft.com/openjdk/jdk:11-ubuntu
VOLUME /tmp
COPY target/ehrouter2eh-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
COPY target/lib/applicationinsights-agent-3.3.1.jar applicationinsights-agent.jar
CMD java -javaagent:/applicationinsights-agent.jar -Djava.security.egd=file:/dev/./urandom -jar /app.jar