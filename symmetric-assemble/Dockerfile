# Alpine Linux with Java 21 JDK
FROM eclipse-temurin:21-jdk-alpine

# Input the server zip file used to install SymmetricDS
# This file must be in the Docker context (i.e. working directory)
ARG SERVER_ZIP

WORKDIR /app
COPY $SERVER_ZIP /app/symmetric-ds.zip
COPY README.md /app

# Install SymmetricDS
RUN unzip /app/symmetric-ds.zip
RUN rm -f /app/symmetric-ds.zip
RUN mkdir -p /opt/
RUN mv symmetric-server* /opt/symmetric-ds
RUN chmod -R u=rwX,g=,o= /opt/symmetric-ds/security

ENV SYM_HOME /opt/symmetric-ds

# Create a volume containing the engines, tmp, conf, and security directories
VOLUME /opt/symmetric-ds/engines
VOLUME /opt/symmetric-ds/tmp
VOLUME /opt/symmetric-ds/conf
VOLUME /opt/symmetric-ds/security

EXPOSE 31415
EXPOSE 31417

# Ensure Java virtual machine running SymmetricDS is PID 1:
ENTRYPOINT ["/bin/sh","-c","/opt/symmetric-ds/bin/sym","--container"]
