FROM eclipse-temurin:17.0.14_7-jdk

# 设置工作目录
WORKDIR /app

# 复制 JAR 文件到容器中
COPY target/provider-a-1.0.jar /app/providera.jar

# 暴露端口
EXPOSE 8081

# 运行应用程序
CMD ["java", "-jar", "providera.jar"]