====
    Licensed to JumpMind Inc under one or more contributor
    license agreements.  See the NOTICE file distributed
    with this work for additional information regarding
    copyright ownership.  JumpMind Inc licenses this file
    to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
    (the "License"); you may not use this file except in compliance
    with the License.

    You should have received a copy of the GNU Affero General Public License,
    version 3.0 (AGPLv3) along with this library; if not, see
    <http://www.gnu.org/licenses/>.

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
====

Symmetric UDF for Interbase
----------------------------
This folder contains a user defined function (UDF) library that must be installed in the Interbase 9.0
database for SymmetricDS to work properly.  It includes the sym_escape() and sym_hex() functions used
by the database triggers to escape strings and BLOBs.

How to Install
--------------
The sym_udf library is copied to the UDF folder under the Interbase installation directory.

For Linux users:

   Copy sym_udf.so to /opt/interbase/UDF

For Windows users:

   Copy sym_udf.dll to C:\CodeGear\InterBase\UDF

When SymmetricDS starts up for the first time, it will enable the functions (using the
"create external function" command) and test that they are working.

How to Build
------------
If you want to the build the library yourself, the following files are included:

Source files:
* sym_udf.c      - The C program implementing the user defined functions
* sym_udf.h      - The header file with function declarations

For Unix users:
* build.sh       - A shell program for Unix that compiles and links the sym_udf library

For Windows users:
* sym_udf.vcproj - A project file for Microsoft Visual Studio
* sym_udf.sln    - A solution file for Microsoft Visual Studio
* sym_udf.def    - A DLL definition file for Microsoft Visual Studio

