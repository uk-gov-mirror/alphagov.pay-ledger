FROM maven:3-eclipse-temurin-26-alpine@sha256:a26a67bd0ac0a298cf0b7f68e1dcf1822678b7a851fca5d1510f7d8f243a7bb7 AS builder

WORKDIR /home/build
COPY . .

RUN ["mvn", "clean", "--no-transfer-progress", "package", "-DskipTests"]

FROM eclipse-temurin:25-jre-alpine@sha256:5fcc27581b238efbfda93da3a103f59e0b5691fe522a7ac03fe8057b0819c888 AS final

RUN ["apk", "--no-cache", "upgrade"]

ARG DNS_TTL=15

# Default to UTF-8 file.encoding
ENV LANG C.UTF-8

RUN echo networkaddress.cache.ttl=$DNS_TTL >> "$JAVA_HOME/conf/security/java.security"

COPY ./import_aws_rds_cert_bundles.sh /
RUN /import_aws_rds_cert_bundles.sh && rm /import_aws_rds_cert_bundles.sh

RUN ["apk", "add", "--no-cache", "bash"]

ENV PORT 8080
ENV ADMIN_PORT 8081

EXPOSE 8080
EXPOSE 8081

WORKDIR /app

COPY --from=builder /home/build/docker-startup.sh .
COPY --from=builder /home/build/target/*.yaml .
COPY --from=builder /home/build/target/pay-*-allinone.jar .

CMD bash ./docker-startup.sh
