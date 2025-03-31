package org.jumpmind.db.platform.ase;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatementOptions;

public class AseDmlStatement extends DmlStatement {
    public AseDmlStatement(DmlStatementOptions options) {
        super(options);
    }

    @Override
    protected int getTypeCode(Column column, boolean isDateOverrideToTimestamp) {
        if (column.getJdbcTypeName() != null) {
            if (column.getJdbcTypeName().equalsIgnoreCase("unichar") || column.getJdbcTypeName().equalsIgnoreCase("univarchar")) {
                return Types.VARCHAR;
            } else if (column.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                return Types.BLOB;
            }
        }
        return super.getTypeCode(column, isDateOverrideToTimestamp);
    }
}
