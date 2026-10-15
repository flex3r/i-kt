FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source=https://github.com/flex3r/i-kt
WORKDIR /app
COPY build/libs/i-kt-all.jar i-kt.jar
ENV UPLOAD_DIRECTORY=/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/i-kt.jar"]
