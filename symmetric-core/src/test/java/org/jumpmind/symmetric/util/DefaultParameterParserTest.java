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
package org.jumpmind.symmetric.util;

import java.util.Map;

import static org.junit.Assert.*;

import org.jumpmind.properties.DefaultParameterParser;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;

public class DefaultParameterParserTest {
    @Test
    public void testParse() {
        DefaultParameterParser parser = new DefaultParameterParser("/symmetric-default.properties");
        Map<String, ParameterMetaData> metaData = parser.parse();
        assertNotNull(metaData);
        assertTrue(metaData.size() > 0);
        ParameterMetaData meta = metaData.get(ParameterConstants.PARAMETER_REFRESH_PERIOD_IN_MS);
        assertNotNull(meta);
        assertTrue(meta.getDescription().length() > 0);
        assertTrue(meta.isDatabaseOverridable());
        meta = metaData.get(ParameterConstants.NODE_GROUP_ID);
        assertNotNull(meta);
        assertTrue(meta.getDescription().length() > 0);
        assertFalse(meta.isDatabaseOverridable());
    }
}
