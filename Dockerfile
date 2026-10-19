# ============================================================
# 后端镜像（只含 Spring Boot jar，前端由 Nginx 容器提供）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /be
# 先只拷 pom 拉依赖，利用 Docker 层缓存：代码改动不重新下载依赖
COPY hotelmanagement/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY hotelmanagement/src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    LANG=C.UTF-8
COPY --from=build /be/target/hotelmanagement-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
