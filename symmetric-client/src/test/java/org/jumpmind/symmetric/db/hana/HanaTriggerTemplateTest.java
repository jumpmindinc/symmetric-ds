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
package org.jumpmind.symmetric.db.hana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HanaTriggerTemplateTest {
    private static final List<String> CAPTURE_TEMPLATE_NAMES = List.of("insertTriggerTemplate", "insertReloadTriggerTemplate",
            "updateTriggerTemplate", "updateReloadTriggerTemplate", "deleteTriggerTemplate");
    private ExposedHanaTriggerTemplate triggerTemplate;

    @BeforeEach
    void setUp() {
        DatabaseInfo databaseInfo = new DatabaseInfo();
        databaseInfo.setDelimiterToken("\"");
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getDefaultSchema()).thenReturn("SYM");
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        triggerTemplate = new ExposedHanaTriggerTemplate(symmetricDialect);
    }

    @Test
    void testCaptureTemplates_qualifyDataTableWithRuntimeSchema() {
        for (String templateName : CAPTURE_TEMPLATE_NAMES) {
            String ddl = triggerTemplate.resolveDefaultSchemaAndCatalog(templateName);
            assertTrue(ddl.contains("insert into \"SYM\".$(prefixName)_data"), templateName);
        }
    }

    @Test
    void testCaptureTemplates_doNotUseCatalogPlaceholder() {
        for (String templateName : CAPTURE_TEMPLATE_NAMES) {
            assertFalse(triggerTemplate.getTemplate(templateName).contains("$(defaultCatalog)"), templateName);
        }
    }

    @Test
    void testTransactionIdExpression_isQualifiedWithRuntimeSchema() {
        String resolved = triggerTemplate.resolveDefaultSchemaAndCatalogInSql("$(defaultSchema)sym_transaction_id()");
        assertEquals("\"SYM\".sym_transaction_id()", resolved);
    }

    private static class ExposedHanaTriggerTemplate extends HanaTriggerTemplate {
        ExposedHanaTriggerTemplate(ISymmetricDialect symmetricDialect) {
            super(symmetricDialect);
        }

        String getTemplate(String templateName) {
            return sqlTemplates.get(templateName);
        }

        String resolveDefaultSchemaAndCatalog(String templateName) {
            return replaceDefaultSchemaAndCatalog(getTemplate(templateName));
        }

        String resolveDefaultSchemaAndCatalogInSql(String sql) {
            return replaceDefaultSchemaAndCatalog(sql);
        }
    }
}
