# ============================================================
# Stage 1: Maven 构建
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 1. 先复制 pom.xml，利用 Docker layer 缓存依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B -DskipTests

# 2. 复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: JRE 运行
# ============================================================
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai

# 从构建阶段复制 jar
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
