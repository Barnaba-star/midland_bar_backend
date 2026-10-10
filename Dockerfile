# Midland Bar backend - Spring Boot 3.5 on Java 21.
# Build with Maven, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Uploads live on a volume mounted here (UPLOAD_DIR=/data/uploads/ on Railway).
RUN mkdir -p /data/uploads
# Railway bills the memory the JVM holds, and its containers allow far more
# than this app needs - with a percentage the heap grew to ~1.5 GB for one
# branch. A fixed ceiling, and G1 hands unused heap back to the system every
# minute. Raise -Xmx with the number of branches by setting JAVA_OPTS on the
# Railway service (no rebuild needed).
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xms128m -Xmx768m -XX:+UseG1GC -XX:G1PeriodicGCInterval=60000 -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
