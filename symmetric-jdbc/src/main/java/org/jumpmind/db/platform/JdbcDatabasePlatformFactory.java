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

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.reflect.Constructor;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.ase.AseDatabasePlatform;
import org.jumpmind.db.platform.cassandra.CassandraPlatform;
import org.jumpmind.db.platform.db2.Db2DatabasePlatform;
import org.jumpmind.db.platform.derby.DerbyDatabasePlatform;
import org.jumpmind.db.platform.firebird.FirebirdDatabasePlatform;
import org.jumpmind.db.platform.firebird.FirebirdDialect1DatabasePlatform;
import org.jumpmind.db.platform.generic.GenericJdbcDatabasePlatform;
import org.jumpmind.db.platform.greenplum.GreenplumPlatform;
import org.jumpmind.db.platform.h2.H2DatabasePlatform;
import org.jumpmind.db.platform.hana.HanaDatabasePlatform;
import org.jumpmind.db.platform.hbase.HbasePlatform;
import org.jumpmind.db.platform.hsqldb.HsqlDbDatabasePlatform;
import org.jumpmind.db.platform.hsqldb2.HsqlDb2DatabasePlatform;
import org.jumpmind.db.platform.informix.InformixDatabasePlatform;
import org.jumpmind.db.platform.ingres.IngresDatabasePlatform;
import org.jumpmind.db.platform.interbase.InterbaseDatabasePlatform;
import org.jumpmind.db.platform.kafka.KafkaPlatform;
import org.jumpmind.db.platform.mariadb.MariaDBDatabasePlatform;
import org.jumpmind.db.platform.mysql.MySqlDatabasePlatform;
import org.jumpmind.db.platform.nuodb.NuoDbDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSql95DatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlVariantDetector;
import org.jumpmind.db.platform.raima.RaimaDatabasePlatform;
import org.jumpmind.db.platform.redshift.RedshiftDatabasePlatform;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhere12DatabasePlatform;
import org.jumpmind.db.platform.sqlanywhere.SqlAnywhereDatabasePlatform;
import org.jumpmind.db.platform.sqlite.SqliteDatabasePlatform;
import org.jumpmind.db.platform.voltdb.VoltDbDatabasePlatform;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * A factory of {@link IDatabasePlatform} instances based on a case
 * insensitive database name. Note that this is a convenience class as the platforms
 * can also simply be created via their constructors.
 */
public class JdbcDatabasePlatformFactory implements IDatabasePlatformFactory {
    public static final String JDBC_PREFIX = "jdbc:";
    protected static final String AWS_JDBC_WRAPPER_SUBPROTOCOL = "aws-wrapper";
    protected static final Logger log = LoggerFactory.getLogger(JdbcDatabasePlatformFactory.class);
    /* The database name -> platform map. */
    protected Map<String, Class<? extends IDatabasePlatform>> platforms = new HashMap<String, Class<? extends IDatabasePlatform>>();
    /* Maps the sub-protocol part of a jdbc connection url to platform */
    protected Map<String, Class<? extends IDatabasePlatform>> jdbcSubProtocolToPlatform = new HashMap<String, Class<? extends IDatabasePlatform>>();
    private static IDatabasePlatformFactory instance;

    protected JdbcDatabasePlatformFactory() {
        /**
         * Match on short name for utilities like dbexport
         */
        addPlatform(platforms, DatabaseNamesConstants.ASE, AseDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.CASSANDRA, CassandraPlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.DB2, Db2DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.DERBY, DerbyDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.FIREBIRD, FirebirdDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.FIREBIRD_DIALECT1, FirebirdDialect1DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.GENERIC, GenericJdbcDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.GREENPLUM, GreenplumPlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.H2, H2DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.HANA, HanaDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.HBASE, HbasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.HSQLDB, HsqlDbDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.HSQLDB2, HsqlDb2DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.INGRES, IngresDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.INFORMIX, InformixDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.INTERBASE, InterbaseDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.KAFKA, KafkaPlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.MYSQL, MySqlDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.NUODB, NuoDbDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.POSTGRESQL, PostgreSqlDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.POSTGRESQL95, PostgreSql95DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.SQLITE, SqliteDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.SQLANYWHERE, SqlAnywhere12DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.SQLANYWHERE12, SqlAnywhere12DatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.RAIMA, RaimaDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.REDSHIFT, RedshiftDatabasePlatform.class);
        addPlatform(platforms, DatabaseNamesConstants.VOLTDB, VoltDbDatabasePlatform.class);
        /**
         * Match on name + version to get a specific version
         */
        addPlatform(platforms, "HSQL Database Engine2", HsqlDb2DatabasePlatform.class);
        /**
         * Match on database product name when sub-protocol is used by different platforms
         */
        addPlatform(platforms, "Adaptive Server Enterprise", AseDatabasePlatform.class);
        addPlatform(platforms, "Adaptive Server Anywhere", SqlAnywhereDatabasePlatform.class);
        addPlatform(platforms, "SQL Anywhere", SqlAnywhereDatabasePlatform.class);
        /**
         * Matching on sub-protocol is usually enough to find platform
         */
        jdbcSubProtocolToPlatform.put(AseDatabasePlatform.JDBC_SUBPROTOCOL, AseDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(Db2DatabasePlatform.JDBC_SUBPROTOCOL, Db2DatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(DerbyDatabasePlatform.JDBC_SUBPROTOCOL, DerbyDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(FirebirdDatabasePlatform.JDBC_SUBPROTOCOL, FirebirdDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(H2DatabasePlatform.JDBC_SUBPROTOCOL, H2DatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(HanaDatabasePlatform.JDBC_SUBPROTOCOL, HanaDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(HbasePlatform.JDBC_SUBPROTOCOL, HbasePlatform.class);
        jdbcSubProtocolToPlatform.put(HsqlDbDatabasePlatform.JDBC_SUBPROTOCOL, HsqlDbDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(InformixDatabasePlatform.JDBC_SUBPROTOCOL, InformixDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(IngresDatabasePlatform.JDBC_SUBPROTOCOL, IngresDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(InterbaseDatabasePlatform.JDBC_SUBPROTOCOL, InterbaseDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(MariaDBDatabasePlatform.JDBC_SUBPROTOCOL, MariaDBDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(MySqlDatabasePlatform.JDBC_SUBPROTOCOL, MySqlDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(NuoDbDatabasePlatform.JDBC_SUBPROTOCOL, NuoDbDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(PostgreSqlDatabasePlatform.JDBC_SUBPROTOCOL, PostgreSqlDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(SqliteDatabasePlatform.JDBC_SUBPROTOCOL, SqliteDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(SqlAnywhereDatabasePlatform.JDBC_SUBPROTOCOL, SqlAnywhereDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(RaimaDatabasePlatform.JDBC_SUBPROTOCOL, RaimaDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(RedshiftDatabasePlatform.JDBC_SUBPROTOCOL, RedshiftDatabasePlatform.class);
        jdbcSubProtocolToPlatform.put(VoltDbDatabasePlatform.JDBC_SUBPROTOCOL, VoltDbDatabasePlatform.class);
    }

    public static synchronized IDatabasePlatformFactory getInstance() {
        if (instance == null) {
            instance = AppUtils.newInstance(IDatabasePlatformFactory.class, JdbcDatabasePlatformFactory.class);
        }
        return instance;
    }

    @Override
    public synchronized IDatabasePlatform create(DataSource dataSource, SqlTemplateSettings settings, boolean delimitedIdentifierMode,
            boolean caseSensitive)
            throws DdlException {
        return create(dataSource, settings, delimitedIdentifierMode, caseSensitive, false, false);
    }

    /*
     * Creates a new platform for the specified database. Note that this method installs the data source in the returned platform instance.
     *
     * @param dataSource The data source for the database
     * 
     * @param log The logger that the platform should use
     * 
     * @return The platform or <code>null</code> if the database is not supported
     */
    @Override
    public synchronized IDatabasePlatform create(DataSource dataSource, SqlTemplateSettings settings, boolean delimitedIdentifierMode,
            boolean caseSensitive, boolean isLoadOnly, boolean isLogBased)
            throws DdlException {
        if (isLoadOnly) {
            TypedProperties properties = settings.getProperties();
            String dbUrl = properties.get(DataSourceProperties.DB_POOL_URL);
            String dbDriver = properties.get(DataSourceProperties.DB_POOL_DRIVER);
            if (dbUrl != null && dbUrl.startsWith("cassandra://")) {
                return new CassandraPlatform(settings, dbUrl.substring(12));
            } else if (dbDriver != null && dbDriver.contains("kafka")) {
                return new KafkaPlatform(settings);
            }
        }
        // connects to the database and uses actual metadata info to get db name
        // and version to determine platform
        DatabaseVersion nameVersion = determineDatabaseNameVersionSubprotocol(dataSource);
        Class<? extends IDatabasePlatform> clazz = findPlatformClass(nameVersion);
        try {
            Constructor<? extends IDatabasePlatform> construtor = clazz.getConstructor(DataSource.class, SqlTemplateSettings.class);
            IDatabasePlatform platform = construtor.newInstance(dataSource, settings);
            platform.setDatabaseVersion(nameVersion);
            log.info("The IDatabasePlatform being used is " + platform.getClass().getCanonicalName());
            platform.getDdlBuilder().setDelimitedIdentifierModeOn(delimitedIdentifierMode);
            platform.getDdlBuilder().setCaseSensitive(caseSensitive);
            platform.getDdlBuilder().getDatabaseInfo().setLogBased(isLogBased);
            platform.getDdlBuilder().initCteExpression();
            return platform;
        } catch (Exception e) {
            throw new DdlException("Could not create a platform of type " + nameVersion.getName(), e);
        }
    }

    protected synchronized Class<? extends IDatabasePlatform> findPlatformClass(DatabaseVersion nameVersion) {
        Class<? extends IDatabasePlatform> platformClass = platforms.get(String.format("%s%s",
                nameVersion.getName(), nameVersion.getVersionAsString()).toLowerCase());
        if (platformClass == null) {
            platformClass = platforms.get(nameVersion.getName().toLowerCase());
        }
        if (platformClass == null) {
            platformClass = jdbcSubProtocolToPlatform.get(nameVersion.getProtocol());
        }
        if (platformClass == null) {
            log.warn("No dedicated database platform found for '{}'. "
                    + "Falling back to GenericJdbcDatabasePlatform, which has limited functionality. "
                    + "For more information contact the SymmetricDS sales team.",
                    nameVersion.getName());
            platformClass = GenericJdbcDatabasePlatform.class;
        }
        return platformClass;
    }

    @Override
    public DatabaseVersion determineDatabaseNameVersionSubprotocol(DataSource dataSource) {
        DatabaseVersion nameVersion = new DatabaseVersion();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            nameVersion.setName(metaData.getDatabaseProductName());
            nameVersion.setVersion(metaData.getDatabaseMajorVersion());
            nameVersion.setMinorVersion(metaData.getDatabaseMinorVersion());
            String url = metaData.getURL();
            if (StringUtils.isNotBlank(url) && url.length() > JDBC_PREFIX.length()) {
                url = url.substring(JDBC_PREFIX.length());
                if (url.indexOf(":") > 0) {
                    url = url.substring(0, url.indexOf(":"));
                }
            }
            nameVersion.setProtocol(url);
            determineDatabaseNameVersionSubprotocol(dataSource, connection, metaData, nameVersion);
            log.info("Detected database '" + nameVersion.getName() + "', version '" + nameVersion.getVersion() + ", minor version '" + nameVersion
                    .getMinorVersion() + "', protocol '" + nameVersion.getProtocol()
                    + "'");
        } catch (Throwable ex) {
            if (ex instanceof SQLFeatureNotSupportedException) {
                log.warn("A common JDBC feature was not supported by the database. Found while determining the database version subprotocol.");
            } else {
                throw new SqlException("Error while reading the database metadata: " + ex.getMessage(), ex);
            }
        }
        return nameVersion;
    }

    protected void determineDatabaseNameVersionSubprotocol(DataSource dataSource, Connection connection, DatabaseMetaData metaData, DatabaseVersion nameVersion)
            throws SQLException {
        boolean isPostgresProtocol = nameVersion.getProtocol().equalsIgnoreCase(PostgreSqlDatabasePlatform.JDBC_SUBPROTOCOL)
                || (AWS_JDBC_WRAPPER_SUBPROTOCOL.equalsIgnoreCase(nameVersion.getProtocol()) && "PostgreSQL".equalsIgnoreCase(nameVersion.getName()));
        if (isPostgresProtocol) {
            if (isGreenplumDatabase(connection)) {
                nameVersion.setName(DatabaseNamesConstants.GREENPLUM);
                nameVersion.setVersion(getGreenplumVersion(connection));
            } else if (PostgreSqlVariantDetector.isAuroraPostgres(connection)) {
                nameVersion.setName(DatabaseNamesConstants.AURORA_POSTGRESQL);
            } else if (metaData.getDatabaseMajorVersion() > 9 || (metaData.getDatabaseMajorVersion() == 9 && metaData.getDatabaseMinorVersion() >= 5)) {
                nameVersion.setName(DatabaseNamesConstants.POSTGRESQL95);
            }
        }
        if (nameVersion.getProtocol().equalsIgnoreCase(FirebirdDatabasePlatform.JDBC_SUBPROTOCOL)) {
            if (isFirebirdDialect1(connection)) {
                nameVersion.setName(DatabaseNamesConstants.FIREBIRD_DIALECT1);
            }
        }
        if (nameVersion.getName().equalsIgnoreCase(DatabaseNamesConstants.ORACLE)) {
            int majorVersion = Integer.valueOf(metaData.getDatabaseMajorVersion());
            int minorVersion = Integer.valueOf(metaData.getDatabaseMinorVersion());
            if (majorVersion < 23 && majorVersion > 12 || (majorVersion == 12 && minorVersion >= 2)) {
                if (isOracle122Compatible(connection)) {
                    nameVersion.setName(DatabaseNamesConstants.ORACLE122);
                }
            } else if (majorVersion >= 23) {
                if (isOracle122Compatible(connection)) {
                    nameVersion.setVersion(23);
                }
            }
        }
        if (nameVersion.getProtocol().equalsIgnoreCase(SqlAnywhereDatabasePlatform.JDBC_SUBPROTOCOL_SHORT) && nameVersion.getVersion() >= 12 && !nameVersion
                .getName().equals("Adaptive Server Enterprise")) {
            nameVersion.setName(DatabaseNamesConstants.SQLANYWHERE12);
        }
    }

    private boolean isGreenplumDatabase(Connection connection) {
        int count = 0;
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(GreenplumPlatform.SQL_GET_GREENPLUM_COUNT)) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return count > 0;
    }

    private int getGreenplumVersion(Connection connection) {
        String versionName = null;
        int productVersion = 0;
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(GreenplumPlatform.SQL_GET_GREENPLUM_VERSION)) {
            while (rs.next()) {
                versionName = rs.getString(1);
            }
            if (versionName.indexOf('.') != -1) {
                versionName = versionName.substring(0, versionName.indexOf('.'));
            }
            try {
                productVersion = Integer.parseInt(versionName);
            } catch (NumberFormatException ex) {
            }
        } catch (SQLException ex) {
        }
        return productVersion;
    }

    private boolean isFirebirdDialect1(Connection connection) {
        boolean isDialect1 = false;
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("select current_time from rdb$database")) {
            rs.next();
        } catch (SQLException ex) {
            isDialect1 = true;
        }
        if (isDialect1) {
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("select cast(1 as numeric(10,0)) from rdb$database")) {
                rs.next();
            } catch (SQLException e) {
                log.error(
                        "The client sql dialect does not match the database, which is not a supported mode.  You must add ?sql_dialect=1 to the end of the JDBC URL.");
            }
        }
        return isDialect1;
    }

    private boolean isOracle122Compatible(Connection connection) {
        boolean isOracle122 = false;
        String compatible = null;
        try (Statement s = connection.createStatement()) {
            try {
                s.executeUpdate("begin dbms_output.enable(); end;");
                s.executeUpdate("declare lver varchar(100); lcomp varchar(100);" +
                        " begin dbms_utility.db_version(lver, lcomp); dbms_output.put_line(lcomp); end;");
                String sql = "declare num integer := 1; begin dbms_output.get_lines(?, num); end;";
                try (CallableStatement call = connection.prepareCall(sql)) {
                    call.registerOutParameter(1, Types.ARRAY, "DBMSOUTPUT_LINESARRAY");
                    call.execute();
                    Array array = call.getArray(1);
                    if (array != null) {
                        String[] compatibleArray = (String[]) array.getArray();
                        if (compatibleArray != null && compatibleArray.length > 0) {
                            compatible = compatibleArray[0];
                        }
                        array.free();
                    }
                }
            } finally {
                s.executeUpdate("begin dbms_output.disable(); end;");
            }
        } catch (SQLException e) {
            log.warn("Could not check Oracle compatible parameter", e);
        }
        if (compatible != null) {
            String[] valueArr = compatible.split("\\.");
            if (valueArr != null) {
                try {
                    isOracle122 = (valueArr.length > 0 && Integer.parseInt(valueArr[0]) > 12) ||
                            (valueArr.length > 1 && Integer.parseInt(valueArr[0]) == 12 && Integer.parseInt(valueArr[1]) >= 2);
                } catch (Exception e) {
                    log.warn("Could not parse Oracle compatible version " + compatible + " because ", e.getMessage());
                }
            }
        }
        return isOracle122;
    }

    protected synchronized void addPlatform(Map<String, Class<? extends IDatabasePlatform>> platformMap, String platformName,
            Class<? extends IDatabasePlatform> platformClass) {
        if (!IDatabasePlatform.class.isAssignableFrom(platformClass)) {
            throw new IllegalArgumentException("Cannot register class " + platformClass.getName() + " because it does not implement the "
                    + IDatabasePlatform.class.getName() + " interface");
        }
        platformMap.put(platformName.toLowerCase(), platformClass);
    }

    public static boolean isJdbcUrl(String dbUrl) {
        return dbUrl != null && dbUrl.startsWith(JDBC_PREFIX);
    }
}
