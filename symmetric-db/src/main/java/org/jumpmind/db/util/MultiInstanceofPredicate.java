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
package org.jumpmind.db.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.function.Predicate;
import org.jumpmind.db.alter.IModelChange;

/**
 * A predicate that tests whether the object is of one of the configured types.
 * 
 * @version $Revision: $
 */
public class MultiInstanceofPredicate implements Predicate<IModelChange> {
    /** The types to check. */
    private Class<?>[] _typesToCheck;

    /**
     * Creates a new predicate.
     * 
     * @param typesToCheck
     *            The types to check
     */
    public MultiInstanceofPredicate(Class<?>[] typesToCheck) {
        _typesToCheck = typesToCheck;
    }

    /**
     * {@inheritDoc}
     */
    public boolean test(IModelChange obj) {
        if (_typesToCheck == null || _typesToCheck.length == 0) {
            return true;
        } else {
            Class<?> typeOfObj = obj.getClass();
            for (int idx = 0; idx < _typesToCheck.length; idx++) {
                if (_typesToCheck[idx].isAssignableFrom(typeOfObj)) {
                    return true;
                }
            }
            return false;
        }
    }
}
