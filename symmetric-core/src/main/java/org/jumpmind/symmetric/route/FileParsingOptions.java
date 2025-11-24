/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.route;

public class FileParsingOptions {
    private final static String ROUTER_EXPRESSION_INCLUDE_TRANSACTION_ID = "INCLUDE_TRANSACTION_ID";
    private final static String ROUTER_EXPRESSION_TAIL_FILE = "TAIL_FILE";
    private final static String ROUTER_EXPRESSION_STANDARDIZE_NAMES = "STANDARDIZE_NAMES";
    private final static String ROUTER_EXPRESSION_OVERWRITE = "OVERWRITE";
    private final static String ROUTER_EXPRESSION_SEND_DDL = "SEND_DDL";
    private boolean overwrite;
    private boolean tailFile = true;
    private boolean standardizeNames;
    private boolean sendDdl;
    private boolean includeTransactionId;

    public FileParsingOptions() {
    }

    public String format() {
        return ROUTER_EXPRESSION_STANDARDIZE_NAMES + "=" + standardizeNames + "," +
                ROUTER_EXPRESSION_OVERWRITE + "=" + overwrite + "," +
                ROUTER_EXPRESSION_TAIL_FILE + "=" + tailFile + "," +
                ROUTER_EXPRESSION_SEND_DDL + "=" + sendDdl + "," +
                ROUTER_EXPRESSION_INCLUDE_TRANSACTION_ID + "=" + includeTransactionId;
    }

    public static FileParsingOptions parse(String expression) {
        FileParsingOptions options = new FileParsingOptions();
        if (expression != null) {
            String[] keyValues = expression.split(",");
            for (int i = 0; i < keyValues.length; i++) {
                String[] keyValue = keyValues[i].split("=");
                if (keyValue.length > 1) {
                    if (ROUTER_EXPRESSION_STANDARDIZE_NAMES.equalsIgnoreCase(keyValue[0])) {
                        options.standardizeNames(Boolean.valueOf(keyValue[1]));
                    } else if (ROUTER_EXPRESSION_TAIL_FILE.equalsIgnoreCase(keyValue[0])) {
                        options.tailFile(Boolean.valueOf(keyValue[1]));
                    } else if (ROUTER_EXPRESSION_OVERWRITE.equalsIgnoreCase(keyValue[0])) {
                        options.overwrite(Boolean.valueOf(keyValue[1]));
                    } else if (ROUTER_EXPRESSION_SEND_DDL.equalsIgnoreCase(keyValue[0])) {
                        options.sendDdl(Boolean.valueOf(keyValue[1]));
                    } else if (ROUTER_EXPRESSION_INCLUDE_TRANSACTION_ID.equalsIgnoreCase(keyValue[0])) {
                        options.includeTransactionId(Boolean.valueOf(keyValue[1]));
                    }
                }
            }
            if (options.isOverwrite() && options.isTailFile()) {
                options.tailFile(false);
            }
        }
        return options;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public FileParsingOptions overwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public boolean isTailFile() {
        return tailFile;
    }

    public FileParsingOptions tailFile(boolean tailFile) {
        this.tailFile = tailFile;
        return this;
    }

    public boolean isStandardizeNames() {
        return standardizeNames;
    }

    public FileParsingOptions standardizeNames(boolean standardizeNames) {
        this.standardizeNames = standardizeNames;
        return this;
    }

    public boolean isSendDdl() {
        return sendDdl;
    }

    public FileParsingOptions sendDdl(boolean sendDdl) {
        this.sendDdl = sendDdl;
        return this;
    }

    public boolean isIncludeTransactionId() {
        return includeTransactionId;
    }

    public FileParsingOptions includeTransactionId(boolean includeTransactionId) {
        this.includeTransactionId = includeTransactionId;
        return this;
    }
}
