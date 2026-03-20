package org.jumpmind.symmetric.db.mssql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MsSqlSymmetricDialectTest {
    private MsSqlSymmetricDialect dialect;
    private IParameterService parameterService;
    private IDatabasePlatform platform;

    @BeforeEach
    void setup() {
        parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(ddlBuilder.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(sqlTemplate.queryForInt(MsSqlSymmetricDialect.SQL_NOCOUNT)).thenReturn(0);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.is(ParameterConstants.MSSQL_USE_VARCHAR_FOR_LOB_IN_SYNC)).thenReturn(false);
        when(parameterService.getString(ParameterConstants.AUTO_CONFIGURE_EXTRA_TABLES)).thenReturn(null);
        dialect = new MsSqlSymmetricDialect(parameterService, platform);
    }

    private Database readSchemaWithNtypes(boolean ntypesEnabled) {
        when(parameterService.is(ParameterConstants.MSSQL_USE_NTYPES_FOR_SYNC)).thenReturn(ntypesEnabled);
        Database db = buildDatabaseWithSymTables();
        when(platform.readDatabaseFromXml(anyString(), eq(true))).thenReturn(db);
        return dialect.readSymmetricSchemaFromXml();
    }

    @Test
    void testReadSymmetricSchemaFromXml_ntypesEnabled_convertsColumnsToNtypes() {
        Database result = readSchemaWithNtypes(true);
        Table fileSnapshot = result.findTable(
                TableConstants.getTableName("sym", TableConstants.SYM_FILE_SNAPSHOT));
        assertEquals(TypeMap.NVARCHAR, fileSnapshot.getColumnWithName("file_name").getMappedType());
        assertEquals(TypeMap.NVARCHAR, fileSnapshot.getColumnWithName("relative_dir").getMappedType());
        Table symData = result.findTable(
                TableConstants.getTableName("sym", TableConstants.SYM_DATA));
        assertEquals(TypeMap.LONGNVARCHAR, symData.getColumnWithName("row_data").getMappedType());
        assertEquals(TypeMap.LONGNVARCHAR, symData.getColumnWithName("old_data").getMappedType());
        assertEquals(TypeMap.LONGNVARCHAR, symData.getColumnWithName("pk_data").getMappedType());
    }

    @Test
    void testReadSymmetricSchemaFromXml_ntypesDisabled_leavesColumnsAsVarchar() {
        Database result = readSchemaWithNtypes(false);
        Table fileSnapshot = result.findTable(
                TableConstants.getTableName("sym", TableConstants.SYM_FILE_SNAPSHOT));
        assertEquals(TypeMap.VARCHAR, fileSnapshot.getColumnWithName("file_name").getMappedType());
        assertEquals(TypeMap.VARCHAR, fileSnapshot.getColumnWithName("relative_dir").getMappedType());
        Table symData = result.findTable(
                TableConstants.getTableName("sym", TableConstants.SYM_DATA));
        assertEquals(TypeMap.LONGVARCHAR, symData.getColumnWithName("row_data").getMappedType());
    }

    private Database buildDatabaseWithSymTables() {
        Database db = new Database();
        Table symData = new Table(TableConstants.getTableName("sym", TableConstants.SYM_DATA));
        symData.addColumn(new Column("data_id", false, Types.BIGINT, 0, 0));
        symData.addColumn(new Column("row_data", false, Types.LONGVARCHAR, 0, 0));
        symData.addColumn(new Column("old_data", false, Types.LONGVARCHAR, 0, 0));
        symData.addColumn(new Column("pk_data", false, Types.LONGVARCHAR, 0, 0));
        db.addTable(symData);
        Table fileSnapshot = new Table(
                TableConstants.getTableName("sym", TableConstants.SYM_FILE_SNAPSHOT));
        fileSnapshot.addColumn(new Column("trigger_id", false, Types.VARCHAR, 50, 0));
        fileSnapshot.addColumn(new Column("router_id", false, Types.VARCHAR, 50, 0));
        fileSnapshot.addColumn(new Column("relative_dir", false, Types.VARCHAR, 255, 0));
        fileSnapshot.addColumn(new Column("file_name", false, Types.VARCHAR, 260, 0));
        db.addTable(fileSnapshot);
        return db;
    }
}
