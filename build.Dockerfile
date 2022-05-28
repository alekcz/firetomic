FROM findepi/graalvm:java11 as builder


RUN apt update -y && apt install -y \
  curl \
  bash \
  git \
  rlwrap \
  && rm -rf /var/lib/apt/lists/* \
  && curl -O https://download.clojure.org/install/linux-install-1.11.1.1113.sh \
  && chmod +x linux-install-1.11.1.1113.sh \
  &&./linux-install-1.11.1.1113.sh

COPY . /usr/firetomic
WORKDIR /usr/firetomic
RUN clj -X:install
RUN clj -T:build uber


# use clean base image
FROM findepi/graalvm:java11

COPY --from=builder /usr/firetomic/target/firetomic-standalone.jar /firetomic-standalone.jar
COPY ./resources /resources

CMD ["java","-jar","/firetomic-standalone.jar"]
