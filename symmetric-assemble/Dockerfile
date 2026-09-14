# Licensed to JumpMind Inc under one or more contributor
# license agreements.  See the NOTICE file distributed
# with this work for additional information regarding
# copyright ownership.  JumpMind Inc licenses this file
# to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
# (the "License"); you may not use this file except in compliance
# with the License.
#
# You should have received a copy of the GNU Affero General Public License,
# version 3.0 (AGPLv3) along with this library; if not, see
# <http://www.gnu.org/licenses/>.
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

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

# Create volumes to persist container's configuration and security directories
VOLUME /opt/symmetric-ds/conf
VOLUME /opt/symmetric-ds/engines
VOLUME /opt/symmetric-ds/security

# Create volumes to persist container's temporary directories  
VOLUME /opt/symmetric-ds/tmp
VOLUME /opt/symmetric-ds/logs

# Most deployments allow only one of SymmetricDS ports (31415 for HTTP, 31417 for HTTPS) on the network:
EXPOSE 31415
EXPOSE 31417

# Ensure Java virtual machine running SymmetricDS is PID 1:
ENTRYPOINT ["/bin/sh","-c","/opt/symmetric-ds/bin/sym","--container"]
