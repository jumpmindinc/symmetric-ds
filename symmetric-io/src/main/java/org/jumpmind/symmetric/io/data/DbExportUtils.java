package org.jumpmind.symmetric.io.data;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DbExport.Format;

public class DbExportUtils {
    public static void extractConfigurationStandalone(IDatabasePlatform platform, List<String> tables, Writer out) throws IOException {
        DbExport export = new DbExport(platform);
        export.setFormat(Format.SQL);
        export.setNoCreateInfo(true);
        export.setUseQuotedIdentifiers(false);
        for (int i = tables.size() - 1; i >= 0; i--) {
            String tableName = platform.alterCaseToMatchDatabaseDefaultCase(tables.get(i));
            out.write(String.format("delete from %s;\n", tableName));
        }
        String quote = platform.getDdlBuilder().getDatabaseInfo().getDelimiterToken();
        for (String tableName : tables) {
            String appendSql = "order by ";
            Table table = (Table) platform.getRelationFromCache(null, null, tableName, false);
            if (table != null) {
                Column[] pkColumns = table.getPrimaryKeyColumns();
                for (int j = 0; j < pkColumns.length; j++) {
                    if (j > 0) {
                        appendSql += ", ";
                    }
                    appendSql += quote + pkColumns[j].getName() + quote;
                }
            }
            export.setWhereClause(appendSql);
            out.write(export.exportTables(new String[] { tableName }));
        }
    }
}
