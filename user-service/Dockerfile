FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY emergency-request-service/pom.xml emergency-request-service/pom.xml
COPY ambulance-service/pom.xml ambulance-service/pom.xml
COPY location-service/pom.xml location-service/pom.xml
COPY matching-service/pom.xml matching-service/pom.xml
COPY hospital-service/pom.xml hospital-service/pom.xml
COPY case-service/pom.xml case-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -pl user-service -am dependency:go-offline -DskipTests -q

COPY shared/src shared/src
COPY user-service/src user-service/src
RUN mvn -pl user-service -am package -DskipTests -q

FROM eclipse-temurin:21-jdk-alpine AS jre-builder
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.management,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output /jre

FROM alpine:3.20 AS runtime
WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S mediflow \
    && adduser -S -D -H -s /sbin/nologin -G mediflow mediflow
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-builder /jre ${JAVA_HOME}
COPY --from=builder --chown=mediflow:mediflow /workspace/user-service/target/user-service-*.jar app.jar
USER mediflow

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
