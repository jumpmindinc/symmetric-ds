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
package org.jumpmind.symmetric.io.data.transform;

import java.util.Objects;

public abstract class AbstractTransformTest {
    /**
     * A utility class for helping with unit testing. Using static type to improve readability since there are many Strings involved with transforms. Can be
     * helpful in testing along side NewAndOldValue type.
     */
    static final class NewValue {
        private final String newValue;

        private NewValue(String newValue) {
            this.newValue = newValue;
        }

        protected static NewValue of(String newValue) {
            return new NewValue(newValue);
        }

        protected String get() {
            return this.newValue;
        }
    }

    /**
     * A utility class for helping with unit testing. Using static type to improve readability since there are many Strings involved with transforms. Can be
     * helpful in testing along side NewAndOldValue type.
     */
    static final class OldValue {
        private final String old;

        private OldValue(String old) {
            this.old = old;
        }

        protected static OldValue of(String old) {
            return new OldValue(old);
        }

        protected String get() {
            return this.old;
        }
    }

    /**
     * A utility class for helping with unit testing. Using static type to improve readability since there are many Strings involved with transforms.
     */
    static final class TransformExpression {
        private final String expression;

        private TransformExpression(String expression) {
            this.expression = expression;
        }

        protected static TransformExpression of(String expression) {
            return new TransformExpression(expression);
        }

        protected String get() {
            return this.expression;
        }
    }

    /**
     * A utility class for helping with unit testing. Replaces use for the testing framework assertEquals method, using a static type.
     */
    static final class Expected {
        private final NewValue newValue;
        private final OldValue oldValue;

        private Expected(NewValue newValue, OldValue oldValue) {
            this.newValue = newValue;
            this.oldValue = oldValue;
        }

        protected static Expected of(NewValue newValue, OldValue oldValue) {
            return new Expected(newValue, oldValue);
        }

        protected void assertMatches(String actualNewValue, String actualOldValue) {
            String expectedNew = newValue == null ? null : newValue.get();
            String expectedOld = oldValue == null ? null : oldValue.get();
            if (!Objects.equals(expectedNew, actualNewValue)) {
                throw new AssertionError(
                        "Expected new value <" + expectedNew + "> but was <" + actualNewValue + ">");
            }
            if (!Objects.equals(expectedOld, actualOldValue)) {
                throw new AssertionError(
                        "Expected old value <" + expectedOld + "> but was <" + actualOldValue + ">");
            }
        }
    }
}
