#!/bin/sh
#
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
#


if [[ -z "${INTERBASE_HOME}" ]]; then
        INTERBASE_HOME=/opt/interbase
fi

if [[ "${OSTYPE}" == "linux-gnu" ]]; then
        SHARED_LIB_FILE=sym_udf.so
        LD_OPTIONS="-G -lm -lc -lib_util"
elif [[ "${OSTYPE}" == "darwin"* ]]; then
        SHARED_LIB_FILE=sym_udf.dylib
        LD_OPTIONS="-lm -lc -lib_util -dylib"
else
        SHARED_LIB_FILE=sym_udf.so
        LD_OPTIONS="-G -lm -lc -lib_util"
fi

gcc -I "$INTERBASE_HOME"/include -c -O -fpic sym_udf.c
ld ${LD_OPTIONS} sym_udf.o -o ${SHARED_LIB_FILE}
cp ${SHARED_LIB_FILE} "$INTERBASE_HOME"/UDF/
