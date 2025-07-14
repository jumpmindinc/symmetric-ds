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
setlocal
call "%~dp0\setenv.bat"
cd /d "%SYM_HOME%"
"%SYM_JAVA%" -cp "%CLASSPATH%" %SYM_OPTIONS% org.jumpmind.symmetric.SymmetricLauncher %*
endlocal
