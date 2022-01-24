FROM gcr.io/distroless/java17-debian11

COPY target/firetomic-*-standalone.jar /

EXPOSE 3000

CMD ["/firetomic-standalone.jar"]
