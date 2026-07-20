FROM eclipse-temurin:21.0.11_10-jdk-alpine-3.23

# 设置工作目录
WORKDIR /app

# 复制 JAR 文件到容器中
COPY target/consumer-1.0.jar /app/consumer.jar

# 暴露端口
EXPOSE 8080

# 运行应用程序
CMD ["java", "-jar", "consumer.jar"]
