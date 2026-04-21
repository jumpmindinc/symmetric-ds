@REM
@REM Licensed to JumpMind Inc under one or more contributor
@REM license agreements.  See the NOTICE file distributed
@REM with this work for additional information regarding
@REM copyright ownership.  JumpMind Inc licenses this file
@REM to you under the GNU General Public License, version 3.0 (GPLv3)
@REM (the "License"); you may not use this file except in compliance
@REM with the License.
@REM
@REM You should have received a copy of the GNU General Public License,
@REM version 3.0 (GPLv3) along with this library; if not, see
@REM <http://www.gnu.org/licenses/>.
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM

@echo off

pushd "%~dp0.."
set SYM_HOME=%CD%
popd

set SYM_OPTIONS=-Dfile.encoding=utf-8 ^
-Duser.language=en ^
-Djava.io.tmpdir="%SYM_HOME%\tmp" ^
-Dorg.eclipse.jetty.server.Request.maxFormContentSize=800000 ^
-Dorg.eclipse.jetty.server.Request.maxFormKeys=100000 ^
-Dsym.keystore.file="%SYM_HOME%\security\keystore" ^
-Djavax.net.ssl.trustStore="%SYM_HOME%\security\cacerts" ^
-Djavax.net.ssl.keyStorePassword=changeit ^
-Dlogback.configurationFile="file:///%SYM_HOME%\conf\logback.xml" ^
-Dsun.net.client.defaultReadTimeout=300000 ^
-Dsun.net.client.defaultConnectTimeout=300000 ^
-Djava.net.preferIPv4Stack=true ^
-Dcom.ibm.as400.access.AS400.guiAvailable=false ^
-Dsymmetric.ssl.ignore.ciphers=TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA,SSL_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA,SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA ^
-XX:+HeapDumpOnOutOfMemoryError ^
-XX:HeapDumpPath="%SYM_HOME%\tmp"

set SYM_JAVA=java
if /i NOT "%JAVA_HOME%" == "" set SYM_JAVA=%JAVA_HOME%\bin\java

set CLASSPATH=%SYM_HOME%\patches;%SYM_HOME%\patches\*;%SYM_HOME%\lib\*;%SYM_HOME%\web\WEB-INF\lib\*
set PATH=%PATH%;%SYM_HOME%\lib
