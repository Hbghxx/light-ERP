# 多阶段构建：构建环境与运行环境分离，运行镜像不含 Maven 与源码
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 单独下依赖，利用 Docker 层缓存：源码变动不重复下载依赖
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/erp-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
