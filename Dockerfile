FROM findepi/graalvm:java11

COPY target/datahike-server-*-standalone.jar /

EXPOSE 4000

CMD ["java" "-jar" "/datahike-server-standalone.jar"]
