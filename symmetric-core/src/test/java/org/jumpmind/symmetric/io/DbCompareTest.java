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
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Node;
import java.util.Arrays;
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
    void sourceTableNamesOfWildcardTriggerComeFromActiveTriggerHistories() {
        changeTableBeingTested(nonEmptyTableWithPKNoDifferences);
        Trigger trigger = new Trigger("*", "default");
        trigger.setTriggerId("all");
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(sourceEngine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getActiveTriggerHistories(trigger))
                .thenReturn(Arrays.asList(history(nonEmptyTableWithPKNoDifferences), history(emptyTableWithPK)));
        assertEquals(Arrays.asList(nonEmptyTableWithPKNoDifferences, emptyTableWithPK), test.getSourceTableNames(trigger));
    }

    @Test
    void sourceTableNameOfTriggerWithoutWildcardIsItsConfiguredTable() {
        changeTableBeingTested(nonEmptyTableWithPKNoDifferences);
        assertEquals(Arrays.asList(emptyTableWithPK), test.getSourceTableNames(new Trigger(emptyTableWithPK, "default")));
    }

    @Test
    void compareUsingSymmetricConfigWithWildcardTriggerComparesItsTables() {
        Trigger trigger = new Trigger("*", "default");
        trigger.setTriggerId("all");
        TriggerRouter triggerRouter = new TriggerRouter(trigger, new Router());
        INodeService sourceNodeService = mock(INodeService.class);
        INodeService targetNodeService = mock(INodeService.class);
        when(sourceNodeService.findIdentity(true, true)).thenReturn(new Node("corp", "corp"));
        when(targetNodeService.findIdentity(true, true)).thenReturn(new Node("store", "store"));
        when(sourceEngine.getNodeService()).thenReturn(sourceNodeService);
        when(targetEngine.getNodeService()).thenReturn(targetNodeService);
        when(sourceEngine.getTablePrefix()).thenReturn("sym");
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(sourceEngine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(targetEngine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getTriggerRoutersForSourceAndTargetNodes("corp", "store")).thenReturn(Arrays.asList(triggerRouter));
        when(triggerRouterService.getActiveTriggerHistories(trigger))
                .thenReturn(Arrays.asList(history(nonEmptyTableWithPKNoDifferences), history(emptyTableWithPK)));
        when(sourceEngine.getTransformService()).thenReturn(mock(ITransformService.class));
        when(targetEngine.getTransformService()).thenReturn(mock(ITransformService.class));
        config = new DbCompareConfig();
        config.setUseSymmetricConfig(true);
        test = new DbCompare(sourceEngine, targetEngine, config);
        List<TableReport> tableReports = test.compare().getTableReports();
        assertEquals(2, tableReports.size());
        assertEquals(nonEmptyTableWithPKNoDifferences.toUpperCase(), tableReports.get(0).getSourceTable().toUpperCase());
        assertEquals(2, tableReports.get(0).getMatchedRows());
        assertEquals(emptyTableWithPK.toUpperCase(), tableReports.get(1).getSourceTable().toUpperCase());
    }

    private static TriggerHistory history(String tableName) {
        TriggerHistory history = new TriggerHistory();
        history.setSourceTableName(tableName);
        return history;
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
