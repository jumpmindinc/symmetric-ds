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
package org.jumpmind.db.platform;

import static org.junit.Assert.assertTrue;

import java.sql.Types;
import java.util.Arrays;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.ase.AseDdlBuilder;
import org.jumpmind.db.platform.db2.Db2DdlBuilder;
import org.jumpmind.db.platform.derby.DerbyDdlBuilder;
import org.jumpmind.db.platform.firebird.FirebirdDdlBuilder;
import org.jumpmind.db.platform.greenplum.GreenplumDdlBuilder;
import org.jumpmind.db.platform.h2.H2DdlBuilder;
import org.jumpmind.db.platform.hsqldb.HsqlDbDdlBuilder;
import org.jumpmind.db.platform.hsqldb2.HsqlDb2DdlBuilder;
import org.jumpmind.db.platform.informix.InformixDdlBuilder;
import org.jumpmind.db.platform.interbase.InterbaseDdlBuilder;
import org.jumpmind.db.platform.mysql.MySqlDdlBuilder;
import org.jumpmind.db.platform.nuodb.NuoDbDdlBuilder;
import org.jumpmind.db.platform.oracle.OracleDdlBuilder;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlBuilder;
import org.jumpmind.db.platform.raima.RaimaDdlBuilder;
import org.jumpmind.db.platform.redshift.RedshiftDdlBuilder;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDdlBuilder;
import org.jumpmind.db.platform.sqlite.SqliteDdlBuilder;
import org.jumpmind.db.platform.voltdb.VoltDbDdlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AbstractDdlBuilderTest {
    AbstractDdlBuilder[] ddlBuilders;
    protected DdlBuilderForeignKeySupport[] foreignKeyDdlBuilders;
    private Database database;

    public class DdlBuilderForeignKeySupport {
        private AbstractDdlBuilder ddlBuilder;
        private ForeignKeyAction[] onDeleteForeignKeyAction;
        private ForeignKeyAction[] onUpdateForeignKeyAction;
        private boolean supportsOnDelete;
        private boolean supportsOnUpdate;

        public DdlBuilderForeignKeySupport(AbstractDdlBuilder ddlBuilder, boolean supportsOnDelete, ForeignKeyAction[] onDeleteForeignKeyAction,
                boolean supportsOnUpdate, ForeignKeyAction[] onUpdateForeignKeyAction) {
            this.ddlBuilder = ddlBuilder;
            this.supportsOnDelete = supportsOnDelete;
            this.onDeleteForeignKeyAction = onDeleteForeignKeyAction;
            this.supportsOnUpdate = supportsOnUpdate;
            this.onUpdateForeignKeyAction = onUpdateForeignKeyAction;
        }

        public AbstractDdlBuilder getDdlBuilder() {
            return ddlBuilder;
        }

        public ForeignKeyAction[] getOnDeleteForeignKeyAction() {
            return onDeleteForeignKeyAction;
        }

        public ForeignKeyAction[] getOnUpdateForeignKeyAction() {
            return onUpdateForeignKeyAction;
        }

        public boolean isSupportsOnDelete() {
            return supportsOnDelete;
        }

        public boolean isSupportsOnUpdate() {
            return supportsOnUpdate;
        }
    }

    @BeforeEach
    public void setup() {
        ddlBuilders = new AbstractDdlBuilder[] { new H2DdlBuilder(), new OracleDdlBuilder(), new PostgreSqlDdlBuilder() };
        buildForeignKeyDdlBuilders();
        buildForeignKeyTables();
    }

    private void buildForeignKeyTables() {
        database = new Database();
        Column t1c1 = new Column("id1", true, Types.VARCHAR, 5, 0);
        Table t1 = new Table("t1", t1c1);
        database.addTable(t1);
        Column t2c1 = new Column("id2", true, Types.VARCHAR, 5, 0);
        Column t2c2 = new Column("id1", false, Types.VARCHAR, 5, 0);
        Table t2 = new Table("t2", t2c1, t2c2);
        ForeignKey fk2 = new ForeignKey();
        fk2.setForeignTable(t1);
        fk2.setOnDeleteAction(ForeignKeyAction.CASCADE);
        fk2.setOnUpdateAction(ForeignKeyAction.CASCADE);
        Reference r2 = new Reference(t2c2, t1c1);
        fk2.addReference(r2);
        t2.addForeignKey(fk2);
        database.addTable(t2);
        Column t3c1 = new Column("id3", true, Types.VARCHAR, 5, 0);
        Table t3 = new Table("t3", t3c1);
        database.addTable(t3);
        Column t4c1 = new Column("id4", true, Types.VARCHAR, 5, 0);
        Column t4c2 = new Column("id3", false, Types.VARCHAR, 5, 0);
        Table t4 = new Table("t4", t4c1, t4c2);
        ForeignKey fk4 = new ForeignKey();
        fk4.setForeignTable(t3);
        fk4.setOnUpdateAction(ForeignKeyAction.RESTRICT);
        fk4.setOnDeleteAction(ForeignKeyAction.RESTRICT);
        Reference r4 = new Reference(t4c2, t3c1);
        fk4.addReference(r4);
        t4.addForeignKey(fk4);
        database.addTable(t4);
        Column t5c1 = new Column("id5", true, Types.VARCHAR, 5, 0);
        Table t5 = new Table("t5", t5c1);
        database.addTable(t5);
        Column t6c1 = new Column("id6", true, Types.VARCHAR, 5, 0);
        Column t6c2 = new Column("id5", false, Types.VARCHAR, 5, 0);
        Table t6 = new Table("t6", t6c1, t6c2);
        ForeignKey fk6 = new ForeignKey();
        fk6.setForeignTable(t5);
        fk6.setOnDeleteAction(ForeignKeyAction.NOACTION);
        fk6.setOnUpdateAction(ForeignKeyAction.NOACTION);
        Reference r6 = new Reference(t6c2, t5c1);
        fk6.addReference(r6);
        t6.addForeignKey(fk6);
        database.addTable(t6);
        Column t7c1 = new Column("id7", true, Types.VARCHAR, 5, 0);
        Table t7 = new Table("t7", t7c1);
        database.addTable(t7);
        Column t8c1 = new Column("id8", true, Types.VARCHAR, 5, 0);
        Column t8c2 = new Column("id7", false, Types.VARCHAR, 5, 0);
        Table t8 = new Table("t8", t8c1, t8c2);
        ForeignKey fk8 = new ForeignKey();
        fk8.setForeignTable(t7);
        fk8.setOnDeleteAction(ForeignKeyAction.SETDEFAULT);
        fk8.setOnUpdateAction(ForeignKeyAction.SETDEFAULT);
        Reference r8 = new Reference(t8c2, t7c1);
        fk8.addReference(r8);
        t8.addForeignKey(fk8);
        database.addTable(t8);
        Column t9c1 = new Column("id9", true, Types.VARCHAR, 5, 0);
        Table t9 = new Table("t9", t9c1);
        database.addTable(t9);
        Column t10c1 = new Column("id10", true, Types.VARCHAR, 5, 0);
        Column t10c2 = new Column("id9", false, Types.VARCHAR, 5, 0);
        Table t10 = new Table("t10", t10c1, t10c2);
        ForeignKey fk10 = new ForeignKey();
        fk10.setForeignTable(t9);
        fk10.setOnDeleteAction(ForeignKeyAction.SETNULL);
        fk10.setOnUpdateAction(ForeignKeyAction.SETNULL);
        Reference r10 = new Reference(t10c2, t9c1);
        fk10.addReference(r10);
        t10.addForeignKey(fk10);
        database.addTable(t10);
    }

    protected void buildForeignKeyDdlBuilders() {
        foreignKeyDdlBuilders = new DdlBuilderForeignKeySupport[] {
                // Sybase
                new DdlBuilderForeignKeySupport(new AseDdlBuilder(),
                        false, new ForeignKeyAction[] {},
                        false, new ForeignKeyAction[] {}),
                // DB2
                new DdlBuilderForeignKeySupport(new Db2DdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION }),
                // Derby
                new DdlBuilderForeignKeySupport(new DerbyDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETNULL }),
                // Firebird
                new DdlBuilderForeignKeySupport(new FirebirdDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL }),
                // Greenplum
                new DdlBuilderForeignKeySupport(new GreenplumDdlBuilder(),
                        false, new ForeignKeyAction[] {},
                        false, new ForeignKeyAction[] {}),
                // H2
                new DdlBuilderForeignKeySupport(new H2DdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // HsqlDB
                new DdlBuilderForeignKeySupport(new HsqlDbDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // HsqlDB2
                new DdlBuilderForeignKeySupport(new HsqlDb2DdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // Informix
                new DdlBuilderForeignKeySupport(new InformixDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE },
                        false, new ForeignKeyAction[] {}),
                // Interbase
                new DdlBuilderForeignKeySupport(new InterbaseDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL }),
                // MySql
                new DdlBuilderForeignKeySupport(new MySqlDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // NuoDb
                new DdlBuilderForeignKeySupport(new NuoDbDdlBuilder(),
                        false, new ForeignKeyAction[] {},
                        false, new ForeignKeyAction[] {}),
                // Oracle
                new DdlBuilderForeignKeySupport(new OracleDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.SETNULL },
                        false, new ForeignKeyAction[] {}),
                // Postgres
                new DdlBuilderForeignKeySupport(new PostgreSqlDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // Raima
                new DdlBuilderForeignKeySupport(new RaimaDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETNULL }),
                // Redshift
                new DdlBuilderForeignKeySupport(new RedshiftDdlBuilder(),
                        false, new ForeignKeyAction[] {},
                        false, new ForeignKeyAction[] {}),
                // SqlAnywhere
                new DdlBuilderForeignKeySupport(new SqlAnywhereDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.SETDEFAULT, ForeignKeyAction.SETNULL }),
                // Sqlite
                new DdlBuilderForeignKeySupport(new SqliteDdlBuilder(),
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL },
                        true, new ForeignKeyAction[] {
                                ForeignKeyAction.CASCADE, ForeignKeyAction.RESTRICT, ForeignKeyAction.NOACTION, ForeignKeyAction.SETDEFAULT,
                                ForeignKeyAction.SETNULL }),
                // VoltDb
                new DdlBuilderForeignKeySupport(new VoltDbDdlBuilder(),
                        false, new ForeignKeyAction[] {},
                        false, new ForeignKeyAction[] {})
        };
    }

    @Test
    public void testAlterTableWithNewVarcharSizeForPlatformWithPlatformColumn() throws Exception {
        for (AbstractDdlBuilder ddlBuilder : ddlBuilders) {
            Table currentTable = new Table("Test", new Column("ID", true, Types.INTEGER, 50, 0),
                    new Column("TXT", false, Types.VARCHAR, 50, 0));
            currentTable.getColumnWithName("TXT").addPlatformColumn(
                    new PlatformColumn(DatabaseNamesConstants.H2, "VARCHAR2", 50, 0, null));
            Table desiredTable = new Table("Test", new Column("ID", true, Types.INTEGER, 50, 0),
                    new Column("TXT", false, Types.VARCHAR, 255, 0));
            String alterSql = ddlBuilder.alterTable(currentTable, desiredTable);
            assertTrue("Failed to generate an appropriate alter for the following platform: "
                    + ddlBuilder.databaseName, alterSql.contains("255"));
        }
    }

    @Test
    public void testAlterTableWithNewVarcharSizeForPlatformWithoutPlatformColumn() throws Exception {
        for (AbstractDdlBuilder ddlBuilder : ddlBuilders) {
            Table currentTable = new Table("Test", new Column("ID", true, Types.INTEGER, 50, 0),
                    new Column("TXT", false, Types.VARCHAR, 50, 0));
            Table desiredTable = new Table("Test", new Column("ID", true, Types.INTEGER, 50, 0),
                    new Column("TXT", false, Types.VARCHAR, 255, 0));
            String alterSql = ddlBuilder.alterTable(currentTable, desiredTable);
            assertTrue("Failed to generate an appropriate alter for the following platform: "
                    + ddlBuilder.databaseName, alterSql.contains("255"));
        }
    }

    @Test
    public void testForeignKeySupport() throws Exception {
        for (DdlBuilderForeignKeySupport dbfs : foreignKeyDdlBuilders) {
            String ddl = dbfs.getDdlBuilder().createTables(database, false);
            if (dbfs.isSupportsOnDelete()) {
                if (Arrays.asList(dbfs.getOnDeleteForeignKeyAction()).contains(ForeignKeyAction.CASCADE)) {
                    assertTrue("Failed to generate ON DELETE CASCADE for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON DELETE CASCADE"));
                } else {
                    assertTrue("Failed to generate ddl without ON DELETE CASCADE for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON DELETE CASCADE"));
                }
                // RESTRICT is always removed
                assertTrue("Failed to generate ddl without ON DELETE RESTRICT for the following platform: "
                        + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON DELETE RESTRICT"));
                // NO ACTION is always removed
                assertTrue("Failed to generate ddl without ON DELETE NO ACTION for the following platform: "
                        + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON DELETE NO ACTION"));
                if (Arrays.asList(dbfs.getOnDeleteForeignKeyAction()).contains(ForeignKeyAction.SETDEFAULT)) {
                    assertTrue("Failed to generate ON DELETE SET DEFAULT for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON DELETE SET DEFAULT"));
                } else {
                    assertTrue("Failed to generate ddl without ON DELETE SET DEFAULT for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON DELETE SET DEFAULT"));
                }
                if (Arrays.asList(dbfs.getOnDeleteForeignKeyAction()).contains(ForeignKeyAction.SETNULL)) {
                    assertTrue("Failed to generate ON DELETE SET NULL for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON DELETE SET NULL"));
                } else {
                    assertTrue("Failed to generate ddl without ON DELETE SET NULL for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON DELETE SET NULL"));
                }
            }
            if (dbfs.isSupportsOnUpdate()) {
                if (Arrays.asList(dbfs.getOnUpdateForeignKeyAction()).contains(ForeignKeyAction.CASCADE)) {
                    assertTrue("Failed to generate ON UPDATE CASCADE for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON UPDATE CASCADE"));
                } else {
                    assertTrue("Failed to generate ddl without ON UPDATE CASCADE for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON UPDATE CASCADE"));
                }
                // RESTRICT is always removed
                assertTrue("Failed to generate ddl without ON UPDATE RESTRICT for the following platform: "
                        + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON UPDATE RESTRICT"));
                // NO ACTION is always removed
                assertTrue("Failed to generate ddl without ON UPDATE NO ACTION for the following platform: "
                        + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON UPDATE NO ACTION"));
                if (Arrays.asList(dbfs.getOnUpdateForeignKeyAction()).contains(ForeignKeyAction.SETDEFAULT)) {
                    assertTrue("Failed to generate ON UPDATE SET DEFAULT for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON UPDATE SET DEFAULT"));
                } else {
                    assertTrue("Failed to generate ddl without ON UPDATE SET DEFAULT for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON UPDATE SET DEFAULT"));
                }
                if (Arrays.asList(dbfs.getOnUpdateForeignKeyAction()).contains(ForeignKeyAction.SETNULL)) {
                    assertTrue("Failed to generate ON UPDATE SET NULL for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, ddl.contains(" ON UPDATE SET NULL"));
                } else {
                    assertTrue("Failed to generate ddl without ON UPDATE SET NULL for the following platform: "
                            + dbfs.getDdlBuilder().databaseName, !ddl.contains("ON UPDATE SET NULL"));
                }
            }
        }
    }
}
