FROM registry.cn-hangzhou.aliyuncs.com/jeecgdocker/mysql:8.0.19

MAINTAINER jeecgos@163.com

ENV TZ=Asia/Shanghai

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY ./tables_nacos.sql /docker-entrypoint-initdb.d

# jeecg-study：由 05-公共资源\2026103018-平台库脚本.py 从上游 jeecgboot-mysql-5.7.sql 生成
# （只改库名 jeecg-boot → jeecg-study，其余原样；上游文件保留不动）
COPY ./jeecg-study-mysql.sql /docker-entrypoint-initdb.d

COPY ./tables_xxl_job.sql /docker-entrypoint-initdb.d