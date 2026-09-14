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
package org.jumpmind.symmetric.web;

public class FailedEngineInfo {
    private String engineName;
    private String propertyFileName;
    private String errorMessage;
    private Throwable exception;

    public FailedEngineInfo() {
    }

    public FailedEngineInfo(String engineName, String propertyFileName, String errorMessage, Throwable exception) {
        this.engineName = engineName;
        this.propertyFileName = propertyFileName;
        this.exception = exception;
        this.errorMessage = errorMessage;
        if (errorMessage == null && exception != null) {
            StringBuilder sb = new StringBuilder("Failed to initialize engine");
            Throwable t = exception;
            do {
                sb.append(", [").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("]");
                t = t.getCause();
            } while (t != null);
            this.errorMessage = sb.toString();
        }
    }

    public FailedEngineInfo(String engineName, String propertyFileName, Throwable exception) {
        this(engineName, propertyFileName, null, exception);
    }

    public FailedEngineInfo(String engineName, String propertyFileName, String errorMessage) {
        this(engineName, propertyFileName, errorMessage, null);
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getPropertyFileName() {
        return propertyFileName;
    }

    public void setPropertyFileName(String propertyFileName) {
        this.propertyFileName = propertyFileName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }
}
