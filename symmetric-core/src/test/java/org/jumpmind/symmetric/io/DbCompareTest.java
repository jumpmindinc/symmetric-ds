package org.jumpmind.symmetric.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.DbCompareReport.TableReport;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.jumpmind.db.sql.SqlScript;

public class DbCompareTest {
    protected DbCompare test;
    protected DbCompareConfig config;
    protected ISymmetricDialect sourceDialect;
    protected ISymmetricDialect targetDialect;
    protected final String nonEmptyTableWithPKNoDifferences = "NONEMPTYTABLEWITHPKNODIFFERENCES";
    protected final String emptyTableWithPK = "EMPTYTABLEWITHPK";
    protected final String emptyTableWithNoPK = "EMPTYTABLEWITHNOPK";
    protected final String nonEmptyTableWithPKWithDifferences = "NONEMPTYTABLEWITHPKWITHDIFFERENCES";
    protected final String nonEmptyTableWithNoPKNoDifferences = "NONEMPTYTABLEWITHNOPKNODIFFERENCES";
    public List<String> sourceTableNames;
    public List<String> targetTableNames;
    private static boolean firstTimeSetup = true;
    protected ISymmetricEngine sourceEngine;
    protected ISymmetricEngine targetEngine;
    protected SingleConnectionDataSource sourceds;
    protected IDatabasePlatform sourcePlatform;
    protected SingleConnectionDataSource targetds;
    protected IDatabasePlatform targetPlatform;

    @BeforeEach
    public void setUp() throws Exception {
        sourceds = getDataSource("jdbc:h2:mem:sourceDatabase");
        sourcePlatform = JdbcDatabasePlatformFactory.getInstance().create(sourceds, new SqlTemplateSettings(), true, false);
        targetds = getDataSource("jdbc:h2:mem:targetDatabase");
        targetPlatform = JdbcDatabasePlatformFactory.getInstance().create(targetds, new SqlTemplateSettings(), true, false);
        if (firstTimeSetup) {
            createTables();
        }
        this.sourceEngine = mock(ISymmetricEngine.class);
        this.targetEngine = mock(ISymmetricEngine.class);
        when(sourceEngine.getDatabasePlatform()).thenReturn(sourcePlatform);
        when(targetEngine.getDatabasePlatform()).thenReturn(targetPlatform);
        this.sourceDialect = mock(ISymmetricDialect.class);
        this.targetDialect = mock(ISymmetricDialect.class);
        when(sourceEngine.getTargetDialect()).thenReturn(sourceDialect);
        when(targetEngine.getTargetDialect()).thenReturn(targetDialect);
        when(sourceEngine.getSymmetricDialect()).thenReturn(sourceDialect);
        when(targetEngine.getSymmetricDialect()).thenReturn(targetDialect);
        when(sourceDialect.getTargetPlatform()).thenReturn(sourcePlatform);
        when(targetDialect.getTargetPlatform()).thenReturn(targetPlatform);
        IParameterService ps = mock(IParameterService.class);
        when(sourceDialect.getParameterService()).thenReturn(ps);
        when(targetDialect.getParameterService()).thenReturn(ps);
        firstTimeSetup = false;
    }

    private void createTables() throws Exception {
        SqlScript script = new SqlScript(getClass().getResource("createSourceTables.sql"), sourcePlatform.getSqlTemplate());
        script.execute(true);
        script = new SqlScript(getClass().getResource("fillSourceTables.sql"), sourcePlatform.getSqlTemplate());
        script.execute(true);
        script = new SqlScript(getClass().getResource("createTargetTables.sql"), targetPlatform.getSqlTemplate());
        script.execute(true);
        script = new SqlScript(getClass().getResource("fillTargetTables.sql"), targetPlatform.getSqlTemplate());
        script.execute(true);
    }

    private SingleConnectionDataSource getDataSource(String url) throws Exception {
        Class.forName("org.h2.Driver");
        Connection c = DriverManager.getConnection(url);
        return new SingleConnectionDataSource(c, true);
    }

    private void changeTableBeingTested(String tableName) {
        this.sourceTableNames = new ArrayList<>();
        this.sourceTableNames.add(tableName);
        this.targetTableNames = new ArrayList<>();
        this.targetTableNames.add(tableName);
        this.config = new DbCompareConfig();
        this.config.setUseSymmetricConfig(false);
        this.config.setSourceTableNames(this.sourceTableNames);
        this.config.setTargetTableNames(this.targetTableNames);
        this.test = new DbCompare(sourceEngine, targetEngine, config);
    }

    @Test
    void compareTableWithPrimaryKeyColumnsInDifferentOrderNoDifferences() {
        String tableName = "PKORDERDIFFERENT";
        sourcePlatform.getSqlTemplate().update("drop table if exists " + tableName);
        targetPlatform.getSqlTemplate().update("drop table if exists " + tableName);
        sourcePlatform.getSqlTemplate().update("create table " + tableName
                + " (equipment varchar(10) not null, vehicle varchar(10) not null, seq integer, primary key (equipment, vehicle))");
        targetPlatform.getSqlTemplate().update("create table " + tableName
                + " (vehicle varchar(10) not null, equipment varchar(10) not null, seq integer, primary key (vehicle, equipment))");
        for (IDatabasePlatform platform : new IDatabasePlatform[] { sourcePlatform, targetPlatform }) {
            platform.getSqlTemplate().update("insert into " + tableName + " (equipment, vehicle, seq) values ('A', 'Z', 1)");
            platform.getSqlTemplate().update("insert into " + tableName + " (equipment, vehicle, seq) values ('B', 'Y', 2)");
            platform.getSqlTemplate().update("insert into " + tableName + " (equipment, vehicle, seq) values ('C', 'X', 3)");
        }
        changeTableBeingTested(tableName);
        TableReport reportOutput = test.compare().getTableReports().get(0);
        assertEquals(3, reportOutput.getSourceRows());
        assertEquals(3, reportOutput.getTargetRows());
        assertEquals(3, reportOutput.getMatchedRows());
        assertEquals(0, reportOutput.getDifferentRows());
        assertEquals(0, reportOutput.getMissingRows());
        assertEquals(0, reportOutput.getExtraRows());
    }

    @Test
    void targetComparisonSqlOrdersByTargetColumnsInSourcePrimaryKeyOrder() {
        changeTableBeingTested(nonEmptyTableWithPKNoDifferences);
        Table sourceTable = new Table("equipment", new Column("equipment", true), new Column("vehicle", true), new Column("seq"));
        Table targetTable = new Table("equipment", new Column("vehicle", true), new Column("equipment", true), new Column("seq"));
        DbCompareTables tables = new DbCompareTables(sourceTable, targetTable);
        tables.applyColumnMappings();
        String quote = targetPlatform.getDatabaseInfo().getDelimiterToken();
        String targetSql = test.getTargetComparisonSQL(tables, targetPlatform);
        assertTrue(targetSql.endsWith("ORDER BY " + quote + "equipment" + quote + "," + quote + "vehicle" + quote), targetSql);
    }

    @Test
    public void isUnitypeTest() {
        changeTableBeingTested(nonEmptyTableWithPKNoDifferences);
        boolean isUnitype = test.isUniType("unitext");
        assertTrue(isUnitype);
        isUnitype = test.isUniType("unichar");
        assertTrue(isUnitype);
        isUnitype = test.isUniType("univarchar");
        assertTrue(isUnitype);
        isUnitype = test.isUniType("UNIVARCHAR2S");
        assertFalse(isUnitype);
        isUnitype = test.isUniType(" unichar");
        assertFalse(isUnitype);
    }

    @Test
    public void compareNonEmptyTableWithPKNoDifferences() {
        changeTableBeingTested(nonEmptyTableWithPKNoDifferences);
        DbCompareReport report = test.compare();
        TableReport reportOutput = report.getTableReports().get(0);
        assertTrue(reportOutput.getSourceTable().toUpperCase().equals(nonEmptyTableWithPKNoDifferences.toUpperCase()));
        assertTrue(reportOutput.getTargetTable().toUpperCase().equals(nonEmptyTableWithPKNoDifferences.toUpperCase()));
        assertTrue(reportOutput.getSourceRows() == 2);
        assertTrue(reportOutput.getTargetRows() == 2);
        assertTrue(reportOutput.getMatchedRows() == 2);
        assertTrue(reportOutput.getDifferentRows() == 0);
        assertTrue(reportOutput.getMissingRows() == 0);
        assertTrue(reportOutput.getExtraRows() == 0);
        assertTrue(reportOutput.getErrorRows() == 0);
        assertTrue(reportOutput.getThrowable() == null);
    }

    @Test
    public void compareEmptyTableWithPK() {
        changeTableBeingTested(emptyTableWithPK);
        DbCompareReport report = test.compare();
        TableReport reportOutput = report.getTableReports().get(0);
        assertTrue(reportOutput.getSourceTable().toUpperCase().equals(emptyTableWithPK.toUpperCase()));
        assertTrue(reportOutput.getTargetTable().toUpperCase().equals(emptyTableWithPK.toUpperCase()));
        assertTrue(reportOutput.getSourceRows() == 0);
        assertTrue(reportOutput.getTargetRows() == 0);
        assertTrue(reportOutput.getMatchedRows() == 0);
        assertTrue(reportOutput.getDifferentRows() == 0);
        assertTrue(reportOutput.getMissingRows() == 0);
        assertTrue(reportOutput.getExtraRows() == 0);
        assertTrue(reportOutput.getErrorRows() == 0);
        assertTrue(reportOutput.getThrowable() == null);
    }

    @Test
    public void compareEmptyTableWithNoPK() {
        changeTableBeingTested(emptyTableWithNoPK);
        DbCompareReport report = test.compare();
        assertEquals(report.getTableReports(), null);
    }

    @Test
    public void compareNonEmptyTableWithNoPKNoDifferences() {
        changeTableBeingTested(nonEmptyTableWithNoPKNoDifferences);
        DbCompareReport report = test.compare();
        assertEquals(report.getTableReports(), null);
    }

    @Test
    public void compareNonEmptyTableWithPKWithDifferences() {
        changeTableBeingTested(nonEmptyTableWithPKWithDifferences);
        DbCompareReport report = test.compare();
        TableReport reportOutput = report.getTableReports().get(0);
        assertTrue(reportOutput.getSourceTable().toUpperCase().equals(nonEmptyTableWithPKWithDifferences.toUpperCase()));
        assertTrue(reportOutput.getTargetTable().toUpperCase().equals(nonEmptyTableWithPKWithDifferences.toUpperCase()));
        assertTrue(reportOutput.getSourceRows() == 2);
        assertTrue(reportOutput.getTargetRows() == 2);
        assertTrue(reportOutput.getMatchedRows() == 0);
        assertTrue(reportOutput.getDifferentRows() == 2);
        assertTrue(reportOutput.getMissingRows() == 0);
        assertTrue(reportOutput.getExtraRows() == 0);
        assertTrue(reportOutput.getErrorRows() == 0);
        assertTrue(reportOutput.getThrowable() == null);
    }
}
