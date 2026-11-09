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
package org.jumpmind.symmetric.io.data.writer;

import java.io.StringWriter;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.DataSourceFactory;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.symmetric.io.AbstractWriterTest;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the ClickHouse writer against a real server. It is skipped unless CLICKHOUSE_URL is set, for example
 * <code>jdbc:clickhouse://localhost:8123/symds</code>, together with CLICKHOUSE_USER and CLICKHOUSE_PASSWORD. The URL is deliberately used as given, so the
 * default of turning off compression is exercised too.
 */
@EnabledIfEnvironmentVariable(named = "CLICKHOUSE_URL", matches = ".+")
public class ClickHouseDatabaseWriterTest extends AbstractWriterTest {
    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String TABLE_KEYED = "ch_keyed_" + SUFFIX;
    private static final String TABLE_NO_KEY = "ch_nokey_" + SUFFIX;
    private static final String TABLE_ALTER = "ch_alter_" + SUFFIX;
    private static final String TABLE_PURGE = "ch_purge_" + SUFFIX;

    @BeforeAll
    public static void setup() throws Exception {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_URL, System.getenv("CLICKHOUSE_URL"));
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "com.clickhouse.jdbc.ClickHouseDriver");
        properties.setProperty(DataSourceProperties.DB_POOL_USER, System.getenv("CLICKHOUSE_USER"));
        properties.setProperty(DataSourceProperties.DB_POOL_PASSWORD, System.getenv("CLICKHOUSE_PASSWORD"));
        platform = JdbcDatabasePlatformFactory.getInstance().create(DataSourceFactory.create(properties, SecurityServiceFactory.create()),
                new SqlTemplateSettings(), true, true);
    }

    @AfterAll
    public static void tearDown() {
        for (String table : new String[] { TABLE_KEYED, TABLE_NO_KEY, TABLE_ALTER, TABLE_PURGE }) {
            platform.getSqlTemplate().update("drop table if exists \"" + table + "\"");
        }
    }

    @Test
    public void testPlatformIsClickHouse() {
        Assertions.assertEquals(DatabaseNamesConstants.CLICKHOUSE, platform.getName());
        Assertions.assertNotNull(platform.getDefaultSchema());
    }

    @Test
    public void testCreateTableThenInsertUpdateDelete() {
        Table table = table(TABLE_KEYED, column("id", true, Types.INTEGER, 10, 0), column("region", true, Types.VARCHAR, 20, 0),
                column("name", false, Types.VARCHAR, 50, 0), column("amount", false, Types.DECIMAL, 10, 2),
                column("created", false, Types.TIMESTAMP, 0, 0), column("active", false, Types.BIT, 1, 0));
        table.getColumnWithName("id").setRequired(true);
        table.getColumnWithName("region").setRequired(true);
        ClickHouseDatabaseWriter writer = writer();
        load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "east", "one", "1.50", "2026-01-02 03:04:05.000000", "1" }),
                new CsvData(DataEventType.INSERT, new String[] { "2", "east", null, null, null, null }),
                new CsvData(DataEventType.INSERT, new String[] { "3", "west", "three", "3.00", "2026-02-02 03:04:05.000000", "0" }));
        Assertions.assertEquals(3, rowCount(TABLE_KEYED));
        Assertions.assertEquals(3, writer.getStatistics().values().stream().mapToLong(st -> st.get(DataWriterStatisticConstants.INSERTCOUNT)).sum());
        // The table was created from the source definition with the expected key and Nullable columns
        Table created = (Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), TABLE_KEYED, true);
        Assertions.assertNotNull(created);
        Assertions.assertEquals(2, created.getPrimaryKeyColumnCount());
        Assertions.assertTrue(created.getColumnWithName("id").isRequired());
        Assertions.assertTrue(!created.getColumnWithName("name").isRequired());
        load(writer, table,
                new CsvData(DataEventType.UPDATE, new String[] { "1", "east" }, new String[] { "1", "east", "uno", "9.25", "2026-01-02 03:04:05.000000", "1" }),
                new CsvData(DataEventType.DELETE, new String[] { "3", "west" }, null));
        List<Row> result = platform.getSqlTemplate().query("select \"id\", \"region\", \"name\", \"amount\" from \"" + TABLE_KEYED + "\" order by \"id\"");
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals("uno", result.get(0).getString("name"));
        Assertions.assertEquals("9.25", result.get(0).getString("amount"));
        Assertions.assertNull(result.get(1).getString("name"));
    }

    @Test
    public void testTimestampsAreStoredExactlyAsSent() {
        // The test JVM is deliberately not in UTC, so a conversion by the driver would show up as a shifted value
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
        String name = TABLE_KEYED + "_ts";
        try {
            Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("created", false, Types.TIMESTAMP, 26, 6),
                    column("day", false, Types.DATE, 0, 0));
            load(writer(), table, new CsvData(DataEventType.INSERT, new String[] { "1", "2026-01-02 03:04:05.123456", "2026-01-02" }),
                    new CsvData(DataEventType.INSERT, new String[] { "2", "2026-07-02 23:59:59.000000", "2026-07-02" }));
            List<Row> rows = platform.getSqlTemplate()
                    .query("select toString(\"created\") as created, toString(\"day\") as day from \"" + name + "\" order by \"id\"");
            Assertions.assertEquals("2026-01-02 03:04:05.123456", rows.get(0).getString("created"));
            Assertions.assertEquals("2026-01-02", rows.get(0).getString("day"));
            Assertions.assertEquals("2026-07-02 23:59:59.000000", rows.get(1).getString("created"));
            Table created = (Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), name, true);
            Assertions.assertEquals("DateTime64(6)", created.getColumnWithName("created").getJdbcTypeName());
        } finally {
            java.util.TimeZone.setDefault(original);
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testUpdateThatChangesThePrimaryKeyIsNotSupported() {
        String name = TABLE_KEYED + "_pk";
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("region", true, Types.VARCHAR, 20, 0),
                column("name", false, Types.VARCHAR, 50, 0));
        table.getColumnWithName("id").setRequired(true);
        table.getColumnWithName("region").setRequired(true);
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "east", "one" }));
            this.errorExpected = false;
            // The old key is the pk data and the new row has a new key, which is what the source sends
            UnsupportedOperationException error = Assertions.assertThrows(UnsupportedOperationException.class, () -> writeData(writer,
                    new TableCsvData(table, new CsvData(DataEventType.UPDATE, new String[] { "1", "east" }, new String[] { "10", "east", "one-renamed" }))));
            Assertions.assertTrue(error.getMessage().contains("ClickHouse does not support updating the primary key"), error.getMessage());
            Assertions.assertTrue(error.getMessage().contains(name), error.getMessage());
            Assertions.assertTrue(error.getMessage().contains("id"), error.getMessage());
            Assertions.assertFalse(error.getMessage().contains("region"), "only the changed key column is named: " + error.getMessage());
            // Nothing was written: the row is as it was, and no row exists under the new key
            List<Row> rows = platform.getSqlTemplate().query("select \"id\", \"region\", \"name\" from \"" + name + "\"");
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("1", rows.get(0).getString("id"));
            Assertions.assertEquals("one", rows.get(0).getString("name"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testUpdateThatKeepsThePrimaryKeyStaysAnUpdate() {
        String name = TABLE_KEYED + "_pk2";
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "one" }));
            this.errorExpected = false;
            writeData(writer, new TableCsvData(table, new CsvData(DataEventType.UPDATE, new String[] { "1" }, new String[] { "1", "uno" })));
            List<Row> rows = platform.getSqlTemplate().query("select \"id\", \"name\" from \"" + name + "\"");
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("uno", rows.get(0).getString("name"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testTableWithoutKeyIsNotTreatedAsKeyChange() {
        String name = TABLE_NO_KEY + "_u";
        Table table = table(name, column("a", false, Types.INTEGER, 10, 0), column("b", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "x" }));
            this.errorExpected = false;
            // For a table without a key the data stream names every column as the key
            Table stream = table(name, column("a", true, Types.INTEGER, 10, 0), column("b", true, Types.VARCHAR, 50, 0));
            writeData(writer, new TableCsvData(stream, new CsvData(DataEventType.UPDATE, new String[] { "1", "x" }, new String[] { "1", "y" })));
            List<Row> rows = platform.getSqlTemplate().query("select \"a\", \"b\" from \"" + name + "\"");
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("y", rows.get(0).getString("b"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    private static byte[] allByteValues() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return org.apache.commons.codec.binary.Hex.encodeHexString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).toUpperCase();
    }

    private void assertBinaryRoundTrip(org.jumpmind.db.util.BinaryEncoding encoding, String suffix) throws Exception {
        String name = TABLE_KEYED + "_bin" + suffix;
        byte[] all = allByteValues();
        byte[] big = new byte[1000000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i * 31);
        }
        java.util.function.Function<byte[], String> encode = bytes -> encoding == org.jumpmind.db.util.BinaryEncoding.HEX
                ? org.apache.commons.codec.binary.Hex.encodeHexString(bytes)
                : java.util.Base64.getEncoder().encodeToString(bytes);
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("b_binary", false, Types.BINARY, 16, 0),
                column("b_varbinary", false, Types.VARBINARY, 300, 0), column("b_blob", false, Types.BLOB, 0, 0), column("b_big", false, Types.BLOB, 0, 0),
                column("note", false, Types.VARCHAR, 50, 0));
        try {
            load(writer(), encoding, table,
                    new CsvData(DataEventType.INSERT,
                            new String[] { "1", encode.apply(java.util.Arrays.copyOf(all, 16)), encode.apply(all), encode.apply(all), encode.apply(big),
                                    "bytes" }),
                    new CsvData(DataEventType.INSERT, new String[] { "2", null, "", null, null, "empty and null" }));
            List<Row> rows = platform.getSqlTemplate().query(
                    "select \"id\", hex(\"b_binary\") as bin, hex(\"b_varbinary\") as varbin, length(\"b_blob\") as blob_len, "
                            + "hex(SHA256(\"b_blob\")) as blob_sha, length(\"b_big\") as big_len, hex(SHA256(\"b_big\")) as big_sha, \"b_varbinary\" is null as varbin_null, "
                            + "\"b_binary\" is null as bin_null from \"" + name + "\" order by \"id\"");
            Row first = rows.get(0);
            Assertions.assertEquals(org.apache.commons.codec.binary.Hex.encodeHexString(java.util.Arrays.copyOf(all, 16)).toUpperCase(), first.getString(
                    "bin"));
            Assertions.assertEquals(org.apache.commons.codec.binary.Hex.encodeHexString(all).toUpperCase(), first.getString("varbin"));
            Assertions.assertEquals("256", first.getString("blob_len"));
            Assertions.assertEquals(sha256(all), first.getString("blob_sha"));
            Assertions.assertEquals("1000000", first.getString("big_len"));
            Assertions.assertEquals(sha256(big), first.getString("big_sha"));
            Row second = rows.get(1);
            Assertions.assertEquals("0", second.getString("varbin_null"));
            Assertions.assertEquals("", second.getString("varbin"));
            Assertions.assertEquals("1", second.getString("bin_null"));
            Table created = (Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), name, true);
            Assertions.assertEquals(Types.VARBINARY, created.getColumnWithName("b_blob").getMappedTypeCode());
            Assertions.assertEquals(Types.VARCHAR, created.getColumnWithName("note").getMappedTypeCode());
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testBinaryColumnsAreStoredAsBytes_base64() throws Exception {
        assertBinaryRoundTrip(org.jumpmind.db.util.BinaryEncoding.BASE64, "_b64");
    }

    @Test
    public void testBinaryColumnsAreStoredAsBytes_hex() throws Exception {
        assertBinaryRoundTrip(org.jumpmind.db.util.BinaryEncoding.HEX, "_hex");
    }

    @Test
    public void testUpdateAndDeleteFindRowsByPrimaryKeyEvenWhenOldValuesDiffer() {
        String name = TABLE_KEYED + "_oldvals";
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("amount", false, Types.DECIMAL, 10, 2),
                column("label", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "1.50", "one" }),
                    new CsvData(DataEventType.INSERT, new String[] { "2", "2.00", "two" }));
            this.errorExpected = false;
            // The old values sent by the source do not match what the target holds, which must not stop the change from being applied
            CsvData update = new CsvData(DataEventType.UPDATE, new String[] { "1", "9.99", "uno" }, new String[] { "1", "1.5000000001", "stale" }, null);
            update.putParsedData(CsvData.PK_DATA, new String[] { "1" });
            CsvData delete = new CsvData(DataEventType.DELETE, new String[] { "2" }, null);
            delete.putParsedData(CsvData.OLD_DATA, new String[] { "2", "0.01", "other" });
            writeData(writer, new TableCsvData(table, update, delete));
            List<Row> rows = platform.getSqlTemplate().query("select \"id\", \"amount\", \"label\" from \"" + name + "\" order by \"id\"");
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals("9.99", rows.get(0).getString("amount"));
            Assertions.assertEquals("uno", rows.get(0).getString("label"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testBinaryColumnCanBeUpdated() {
        String name = TABLE_KEYED + "_binupd";
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("data", false, Types.VARBINARY, 100, 0),
                column("label", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "AAECAwQ=", "one" }));
            this.errorExpected = false;
            // 0xAABBCC00FF in base64
            CsvData update = new CsvData(DataEventType.UPDATE, new String[] { "1", "qrvMAP8=", "one" }, new String[] { "1", "AAECAwQ=", "one" }, null);
            update.putParsedData(CsvData.PK_DATA, new String[] { "1" });
            writeData(writer, new TableCsvData(table, update));
            List<Row> rows = platform.getSqlTemplate().query("select hex(\"data\") as data from \"" + name + "\"");
            Assertions.assertEquals("AABBCC00FF", rows.get(0).getString("data"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testLargeTextAndSpecialCharactersAreStoredExactly() throws Exception {
        String name = TABLE_KEYED + "_txt";
        String big = "0123456789abcdef".repeat(20000);
        String[] special = { "O'Brien \"quoted\" back\\slash \\n literal", "real\ttab and ; -- /* */ ? $1 %s ${x} <&>", "ñ é 日本語 😀 \u00e9",
                "   leading and trailing   ",
                "line1\r\nline2\nline3", "embedded\u0000nul", "", "'; drop table x; --" };
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("txt", false, Types.VARCHAR, 300, 0),
                column("big", false, Types.CLOB, 0, 0));
        try {
            CsvData[] inserts = new CsvData[special.length + 1];
            for (int i = 0; i < special.length; i++) {
                inserts[i] = new CsvData(DataEventType.INSERT, new String[] { String.valueOf(i), special[i], null });
            }
            inserts[special.length] = new CsvData(DataEventType.INSERT, new String[] { String.valueOf(special.length), null, big });
            load(writer(), table, inserts);
            List<Row> rows = platform.getSqlTemplate().query(
                    "select \"id\", hex(\"txt\") as txt_hex, length(\"big\") as big_len, hex(SHA256(\"big\")) as big_sha from \""
                            + name + "\" order by \"id\"");
            Assertions.assertEquals(special.length + 1, rows.size());
            for (int i = 0; i < special.length; i++) {
                Assertions.assertEquals(org.apache.commons.codec.binary.Hex.encodeHexString(special[i].getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .toUpperCase(),
                        rows.get(i).getString("txt_hex"), "value " + i);
            }
            Assertions.assertEquals("320000", rows.get(special.length).getString("big_len"));
            Assertions.assertEquals(sha256(big.getBytes(java.nio.charset.StandardCharsets.UTF_8)), rows.get(special.length).getString("big_sha"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testNativeUuidColumnsLoadUpdateAndDelete() {
        String name = TABLE_KEYED + "_uuid";
        String first = "123e4567-e89b-12d3-a456-426614174000";
        String second = "00000000-0000-0000-0000-000000000001";
        platform.getSqlTemplate().update("create table \"" + name
                + "\" (\"u\" UUID, \"other\" Nullable(UUID), \"label\" Nullable(String)) engine = MergeTree order by \"u\" "
                + "settings enable_block_number_column = 1, enable_block_offset_column = 1");
        try {
            Table source = table(name, column("u", true, Types.VARCHAR, 36, 0), column("other", false, Types.VARCHAR, 36, 0), column("label", false,
                    Types.VARCHAR, 50, 0));
            ClickHouseDatabaseWriter writer = writer();
            writeData(writer, new TableCsvData(source, new CsvData(DataEventType.INSERT, new String[] { first, second, "one" }),
                    new CsvData(DataEventType.INSERT, new String[] { second, null, "two" })));
            this.errorExpected = false;
            writeData(writer, new TableCsvData(source, new CsvData(DataEventType.UPDATE, new String[] { first }, new String[] { first, second, "uno" }),
                    new CsvData(DataEventType.DELETE, new String[] { second }, null)));
            List<Row> rows = platform.getSqlTemplate().query("select toString(\"u\") as u, toString(\"other\") as other, \"label\" from \"" + name + "\"");
            Assertions.assertEquals(1, rows.size());
            Assertions.assertEquals(first, rows.get(0).getString("u"));
            Assertions.assertEquals(second, rows.get(0).getString("other"));
            Assertions.assertEquals("uno", rows.get(0).getString("label"));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testUpdateAndDeleteOfMissingRowDoesNotConflict() {
        String name = TABLE_KEYED + "_m";
        Table table = table(name, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        try {
            load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "a" }));
            // ClickHouse reports 0 rows for these, so the default writer would raise a conflict for each of them
            this.errorExpected = false;
            load(writer, table, new CsvData(DataEventType.UPDATE, new String[] { "99" }, new String[] { "99", "zzz" }),
                    new CsvData(DataEventType.DELETE, new String[] { "98" }, null));
            Assertions.assertEquals(1, rowCount(name));
        } finally {
            platform.getSqlTemplate().update("drop table if exists \"" + name + "\"");
        }
    }

    @Test
    public void testTableWithoutKeyAndNullableColumns() {
        Table table = table(TABLE_NO_KEY, column("a", false, Types.INTEGER, 10, 0), column("b", false, Types.VARCHAR, 50, 0));
        load(writer(), table, new CsvData(DataEventType.INSERT, new String[] { "1", "x" }),
                new CsvData(DataEventType.INSERT, new String[] { "2", null }));
        Assertions.assertEquals(2, rowCount(TABLE_NO_KEY));
    }

    @Test
    public void testReloadClearRunsAsTruncate() {
        Table table = table(TABLE_PURGE, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "a" }), new CsvData(DataEventType.INSERT, new String[] { "2", "b" }));
        Assertions.assertEquals(2, rowCount(TABLE_PURGE));
        this.errorExpected = false;
        writeData(writer, new TableCsvData(table, new CsvData(DataEventType.SQL, new String[] { "delete from \"" + TABLE_PURGE + "\"" })));
        Assertions.assertEquals(0, rowCount(TABLE_PURGE));
    }

    @Test
    public void testAddModifyAndDropColumn() {
        this.errorExpected = false;
        Table table = table(TABLE_ALTER, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        ClickHouseDatabaseWriter writer = writer();
        load(writer, table, new CsvData(DataEventType.INSERT, new String[] { "1", "a" }));
        Table added = table(TABLE_ALTER, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0),
                column("extra", false, Types.INTEGER, 10, 0));
        load(writer, added, new CsvData(DataEventType.INSERT, new String[] { "2", "b", "7" }));
        Assertions.assertNotNull(((Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), TABLE_ALTER, true)).getColumnWithName("extra"));
        Assertions.assertEquals(2, rowCount(TABLE_ALTER));
        Table modified = table(TABLE_ALTER, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0),
                column("extra", false, Types.BIGINT, 19, 0));
        load(writer, modified, new CsvData(DataEventType.INSERT, new String[] { "3", "c", "8589934592" }));
        Assertions.assertEquals(3, rowCount(TABLE_ALTER));
        Assertions.assertEquals(Types.BIGINT,
                ((Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), TABLE_ALTER, true)).getColumnWithName("extra").getMappedTypeCode());
        Table dropped = table(TABLE_ALTER, column("id", true, Types.INTEGER, 10, 0), column("name", false, Types.VARCHAR, 50, 0));
        load(writer, dropped, new CsvData(DataEventType.INSERT, new String[] { "4", "d" }));
        Assertions.assertNull(((Table) platform.getRelationFromCache(null, platform.getDefaultSchema(), TABLE_ALTER, true)).getColumnWithName("extra"));
        Assertions.assertEquals(4, rowCount(TABLE_ALTER));
    }

    private ClickHouseDatabaseWriter writer() {
        return new ClickHouseDatabaseWriter(platform, platform, "sym", null, writerSettings);
    }

    /**
     * The source sends the table definition as a CREATE event in a batch of its own ahead of the data, which is what makes the target create or alter the
     * table.
     */
    private long load(ClickHouseDatabaseWriter writer, Table table, CsvData... data) {
        return load(writer, org.jumpmind.db.util.BinaryEncoding.BASE64, table, data);
    }

    private long load(ClickHouseDatabaseWriter writer, org.jumpmind.db.util.BinaryEncoding encoding, Table table, CsvData... data) {
        Database database = new Database();
        database.addTable(table);
        StringWriter xml = new StringWriter();
        DatabaseXmlUtil.write(database, xml);
        TableCsvData create = new TableCsvData(table, new CsvData(DataEventType.CREATE, new String[] { xml.toString() }));
        // One call per batch, as the engine opens and closes the writer once for each batch
        writeData(writer, create);
        return writeData(writer, encoding, new TableCsvData(table, data));
    }

    private static int rowCount(String table) {
        return platform.getSqlTemplate().queryForInt("select count(*) from \"" + table + "\"");
    }

    private static Column column(String name, boolean primaryKey, int type, int size, int scale) {
        return new Column(name, primaryKey, type, size, scale);
    }

    private static Table table(String name, Column... columns) {
        Table table = new Table(name);
        for (Column column : columns) {
            table.addColumn(column);
        }
        return table;
    }
}
