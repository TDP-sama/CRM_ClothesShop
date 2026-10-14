# One image serves the whole shop: Spring Boot hosts the API and the built React app on the same origin,
# so session cookies and CSRF work exactly as in development. Configuration comes from environment variables
# (DATABASE_URL, ADMIN_EMAILS, SESSION_COOKIE_SECURE, ...); backend/.env is never copied in (.dockerignore).

# 1) Build the React app
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) Build the Spring Boot jar with the React app inside (served from classpath:/static)
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# 3) Run on a small JRE as a non-root user
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=backend /app/backend/target/shop-backend-*.jar app.jar
USER app
# Small hosting plans have ~512 MB: size the heap from the container limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
