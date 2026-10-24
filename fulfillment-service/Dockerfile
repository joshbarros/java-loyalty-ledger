FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/fulfillment-service-1.0.0.jar app.jar
RUN groupadd --system app && useradd --system --gid app --no-create-home app && chown app:app /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
