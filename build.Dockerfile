FROM clojure:openjdk-17-slim-buster as builder


COPY . /usr/firetomic
WORKDIR /usr/firetomic
RUN clj -X:install
RUN clj -T:build uber


# use clean base image
FROM openjdk:17-slim-buster

COPY --from=builder /usr/firetomic/target/firetomic-standalone.jar /firetomic-standalone.jar
COPY ./resources /resources

CMD ["java","-jar","/firetomic-standalone.jar"]
