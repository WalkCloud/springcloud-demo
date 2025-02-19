# Spring Cloud Demo说明

### 运行环境
```
OpenJDK版本：eclipse-temurin 17.0.14_7-jdk  
Spring Boot版本：3.0.2  
Spring Cloud版本：2022.0.0  
Spring Cloud For Alibaba版本：2022.0.0.0  
Nacos版本：2.2.x~2.4.x 
```


### 访问关系

浏览器访问consumer，consumer通过nacos注册中心发现和调用Provider-A和Provider-B注册的服务.


### Demo显示效果
```
Welcome to visit the microservice demo application!

Operating environment: OpenJDK 17
Microservice architecture: Spring Cloud
Registration center: Nacos

The currently accessed microservice is: consumer

Service Provider A
Service Name: provider-a
Host Name: 8c1b6a64e38c
IP Address: 172.17.0.4
Star Rating: ★★★★★★

Service Provider B
Service Name: provider-b
Host Name: c02de47ab60d
IP Address: 172.17.0.5
Star Rating: ★★★★★★★★★★
```


### 配置说明
#### 环境变量说明：  
```
NACOS_SERVER_ADDR: 配置nacos访问地址  
示例： NACOS_SERVER_ADDR=192.168.0.1:8848

NACOS_NAMESPACE:配置nacos命名空间  
示例：NACOS_NAMESPACE=public  

NACOS_USERNAME: 配置nacos用户名  
示例：NACOS_USERNAME=nacos

NACOS_PASSWORD: 配置nacos密码
示例：NACOS_PASSWORD=nacos
```
此demo每一个微服务都是通过nacos注册发现的，所以都需要调整以上环境变量，程序才能运行。

#### 配置文件说明：

使用Nacos配置中心进行Provider-A和Provider-B内容进行配置更新，需要确定application.yml里面的配置字段，如果需要修改配置名称，则修改字段如下：
```
  config:
    import: "optional:nacos:provider-a.yml"  # 显式导入 Nacos 配置
```
Nacos配置中心中的data-id是provider-a.yml，格式为YAML，其他默认，配置内容如下
```
star:
  color: blue  # 显示星星的颜色 red（红色）、blue（蓝色）、green（绿色）、yellow（黄色）、gold（金色）、silver（银色）、orange（橙色）、purple（紫色）、pink（粉色）、black（黑色）、white（白色）
  count: 10    # 显示星星的数量
```
【注意】：application.yml里面的import的内容和Nacos配置中心里面的Data ID一致，才能通过Nacos下发配置到应用内部。


### 容器镜像
```
consumer镜像地址：registry.cn-beijing.aliyuncs.com/walkcloud/consumer:0.0.1-sc
providera镜像地址：registry.cn-beijing.aliyuncs.com/walkcloud/providera:0.0.1-sc
providerb镜像地址：registry.cn-beijing.aliyuncs.com/walkcloud/providerb:0.0.1-sc

```
