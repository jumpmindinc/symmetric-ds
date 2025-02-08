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
package org.jumpmind.symmetric.db.derby;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.symmetric.db.AbstractTriggerTemplate;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class DerbyTriggerTemplate extends AbstractTriggerTemplate {
    public DerbyTriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
        //@formatter:off
        emptyColumnTemplate = "''" ;
        stringColumnTemplate = "sym_escape($(tableAlias).\"$(columnName)\")" ;
        xmlColumnTemplate = null;
        arrayColumnTemplate = null;
        numberColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || rtrim(char($(tableAlias).\"$(columnName)\")) || '\"' end" ;
        datetimeColumnTemplate = "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || rtrim(char($(tableAlias).\"$(columnName)\")) || '\"' end" ;
        timeColumnTemplate = null;
        dateColumnTemplate = null;
        clobColumnTemplate = "sym_clob_to_string('\"$(columnName)\"', '$(schemaName)$(tableName)', $(primaryKeyWhereString) )" ;
        blobColumnTemplate = "sym_blob_to_string('\"$(columnName)\"', '$(schemaName)$(tableName)', $(primaryKeyWhereString) )" ;
        wrappedBlobColumnTemplate = null;
        booleanColumnTemplate = "case when $(columnName) is null then '' when $(columnName) then '1' else '0' end" ;
        triggerConcatCharacter = "||" ;
        newTriggerValue = "new" ;
        oldTriggerValue = "old" ;
        oldColumnPrefix = "" ;
        newColumnPrefix = "" ;
        otherColumnTemplate = null;

        sqlTemplates = new HashMap<String,String>();
        sqlTemplates.put("insertTriggerTemplate" ,
"CREATE TRIGGER $(triggerName)                                            \n" + 
" AFTER INSERT ON $(schemaName)$(tableName)                               \n" + 
" REFERENCING NEW AS NEW                                                  \n" + 
" FOR EACH ROW MODE DB2SQL                                                \n" + 
" call $(prefixName)_save_data(                                                   \n" +
"   case when $(syncOnInsertCondition) and $(syncOnIncomingBatchCondition) then 1 else 0 end, \n" + 
"   '$(defaultSchema)', '$(prefixName)', '$(targetTableName)',                      \n" + 
"   $(channelExpression), 'I', $(triggerHistoryId),                           \n" + 
"   $(txIdExpression),                                                    \n" + 
"   $(externalSelect),                                                    \n" + 
"   '$(columnNames)',                                                       \n" + 
"   '$(pkColumnNames)')                                                     \n" );
        
        sqlTemplates.put("updateTriggerTemplate" ,
"CREATE TRIGGER $(triggerName)                                            \n" + 
" AFTER UPDATE ON $(schemaName)$(tableName)                               \n" + 
" REFERENCING OLD AS OLD NEW AS NEW                                       \n" + 
" FOR EACH ROW MODE DB2SQL                                                \n" + 
" call $(prefixName)_save_data(                                                   \n" + 
"   case when $(syncOnUpdateCondition) and $(syncOnIncomingBatchCondition) then 1 else 0 end, \n" + 
"   '$(defaultSchema)', '$(prefixName)', '$(targetTableName)',                      \n" + 
"   $(channelExpression), 'U', $(triggerHistoryId),                           \n" + 
"   $(txIdExpression),                                                    \n" + 
"   $(externalSelect),                                                    \n" + 
"   '$(columnNames)',                                                       \n" + 
"   '$(pkColumnNames)')                                                     \n" );
        
        sqlTemplates.put("deleteTriggerTemplate" ,
"CREATE TRIGGER $(triggerName)                                            \n" + 
" AFTER DELETE ON $(schemaName)$(tableName)                               \n" + 
" REFERENCING OLD AS OLD                                                  \n" + 
" FOR EACH ROW MODE DB2SQL                                                \n" + 
" call $(prefixName)_save_data(                                                   \n" + 
"   case when $(syncOnDeleteCondition) and $(syncOnIncomingBatchCondition) then 1 else 0 end, \n" + 
"   '$(defaultSchema)', '$(prefixName)', '$(targetTableName)',                      \n" + 
"   $(channelExpression), 'D', $(triggerHistoryId),                           \n" + 
"   $(txIdExpression),                                                    \n" + 
"   $(externalSelect),                                                    \n" + 
"   '$(columnNames)',                                                       \n" + 
"   '$(pkColumnNames)')                                                     \n" );
        sqlTemplates.put("initialLoadSqlTemplate" ,
"select $(columns) from $(schemaName)$(tableName) t  where $(whereClause)     " );
        
        //@formatter:on
    }

    @Override
    protected String getPrimaryKeyWhereString(String alias, Column[] columns) {
        List<Column> columnsMinusLobs = new ArrayList<Column>();
        for (Column column : columns) {
            if (!column.isOfBinaryType()) {
                columnsMinusLobs.add(column);
            }
        }
        StringBuilder b = new StringBuilder("'");
        for (Column column : columnsMinusLobs) {
            b.append("\"").append(column.getName()).append("\"=");
            switch (column.getMappedTypeCode()) {
                case Types.BIT:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.INTEGER:
                case Types.BIGINT:
                case Types.FLOAT:
                case Types.REAL:
                case Types.DOUBLE:
                case Types.NUMERIC:
                case Types.DECIMAL:
                case Types.BOOLEAN:
                    b.append("'").append(triggerConcatCharacter);
                    b.append("rtrim(char(").append(alias).append(".\"").append(column.getName())
                            .append("\"))");
                    b.append(triggerConcatCharacter).append("'");
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.LONGVARCHAR:
                    b.append("'''").append(triggerConcatCharacter);
                    b.append(alias).append(".\"").append(column.getName()).append("\"");
                    b.append(triggerConcatCharacter).append("'''");
                    break;
                case Types.DATE:
                case Types.TIMESTAMP:
                    b.append("{ts '''").append(triggerConcatCharacter);
                    b.append("rtrim(char(").append(alias).append(".\"").append(column.getName())
                            .append("\"))");
                    b.append(triggerConcatCharacter).append("'''}");
                    break;
            }
            if (!column.equals(columnsMinusLobs.get(columnsMinusLobs.size() - 1))) {
                b.append(" and ");
            }
        }
        b.append("'");
        return b.toString();
    }
}
