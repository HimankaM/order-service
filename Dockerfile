# ---- Build Stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Run Stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security (principle of least privilege)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/order-service-1.0.0.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8082/api/orders/health || exit 1

ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "app.jar"]
