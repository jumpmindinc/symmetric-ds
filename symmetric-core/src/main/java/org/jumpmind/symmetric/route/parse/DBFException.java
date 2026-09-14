/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.route.parse;

import java.io.PrintStream;
import java.io.PrintWriter;

public class DBFException extends Exception {
    private static final long serialVersionUID = 1L;

    public DBFException(String s) {
        this(s, null);
    }

    public DBFException(Throwable throwable) {
        this(throwable.getMessage(), throwable);
    }

    public DBFException(String s, Throwable throwable) {
        super(s);
        detail = throwable;
    }

    public String getMessage() {
        if (detail == null) {
            return super.getMessage();
        } else {
            return super.getMessage();
        }
    }

    public void printStackTrace(PrintStream printstream) {
        if (detail == null) {
            super.printStackTrace(printstream);
            return;
        }
        PrintStream printstream1 = printstream;
        printstream1.println(this);
        detail.printStackTrace(printstream);
        return;
    }

    public void printStackTrace() {
        printStackTrace(System.err);
    }

    public void printStackTrace(PrintWriter printwriter) {
        if (detail == null) {
            super.printStackTrace(printwriter);
            return;
        }
        PrintWriter printwriter1 = printwriter;
        printwriter1.println(this);
        detail.printStackTrace(printwriter);
        return;
    }

    private Throwable detail;
}
