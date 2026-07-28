package org.jumpmind.db.platform.sqlanywhere;

import javax.sql.DataSource;

import org.jumpmind.db.sql.SqlTemplateSettings;

public class SqlAnywhere12DatabasePlatform extends SqlAnywhereDatabasePlatform {
    public SqlAnywhere12DatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
    }

    @Override
    public String getClassName() {
        return SqlAnywhere12DatabasePlatform.class.getName();
    }
}
