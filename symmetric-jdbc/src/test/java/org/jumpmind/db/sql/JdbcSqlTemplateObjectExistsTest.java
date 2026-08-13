package org.jumpmind.db.sql;

import java.sql.SQLException;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.properties.TypedProperties;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/**
 * SYM-7916. Classification of "object already exists" DDL failures.
 * <p>
 * The reach of the fix rests entirely on these message fragments for platforms whose DDL builders are not in this repository (SQL Server, Oracle), so the exact
 * vendor wording is pinned here. Error codes are per-platform and cannot be shared defaults, since the same number means different things on different vendors.
 */
class JdbcSqlTemplateObjectExistsTest {
    /** Concrete subclass so the classification logic can be exercised with no DataSource and no database. */
    private static class TestTemplate extends JdbcSqlTemplate {
        TestTemplate() {
            super(null, new SqlTemplateSettings(), null, new DatabaseInfo());
        }

        void withProperty(String key, String value) {
            TypedProperties properties = new TypedProperties();
            properties.put(key, value);
            settings.setProperties(properties);
        }

        void withErrorCodes(int... codes) {
            objectAlreadyExistsCodes = codes;
        }
    }

    private static SQLException sqlException(String message, String sqlState, int errorCode) {
        return new SQLException(message, sqlState, errorCode);
    }

    @Test
    void sqlServerIndexAlreadyExistsIsRecognised() {
        // The exact message behind SQL Server error 1913, which is what stalled replication at the reporting site.
        SQLException ex = sqlException(
                "The operation failed because an index or statistics with name 'f0101_12' already exists on table 'DB.dbo.f0101'.",
                "S0001", 1913);
        Assert.assertTrue(new TestTemplate().doesObjectAlreadyExist(ex));
    }

    @Test
    void sqlServerTableAlreadyExistsIsRecognised() {
        Assert.assertTrue(new TestTemplate().doesObjectAlreadyExist(
                sqlException("There is already an object named 'f42119' in the database.", "S0001", 2714)));
    }

    @Test
    void oracleNameAlreadyUsedIsRecognised() {
        Assert.assertTrue(new TestTemplate().doesObjectAlreadyExist(
                sqlException("ORA-00955: name is already used by an existing object", "42000", 955)));
    }

    @Test
    void mysqlDuplicateKeyNameIsRecognised() {
        Assert.assertTrue(new TestTemplate().doesObjectAlreadyExist(
                sqlException("Duplicate key name 'f42119_18'", "42000", 1061)));
    }

    @Test
    void matchingIsCaseInsensitive() {
        Assert.assertTrue(new TestTemplate().doesObjectAlreadyExist(
                sqlException("INDEX ALREADY EXISTS", "S0001", 1913)));
    }

    @Test
    void anUnrelatedFailureIsNotClassifiedAsAlreadyExisting() {
        // The tolerance must not swallow a genuine problem such as a constraint violation or a missing object.
        TestTemplate template = new TestTemplate();
        Assert.assertFalse(template.doesObjectAlreadyExist(
                sqlException("Violation of PRIMARY KEY constraint 'PK_f42119'. Cannot insert duplicate key.", "23000", 2627)));
        Assert.assertFalse(template.doesObjectAlreadyExist(
                sqlException("Invalid object name 'dbo.f42119'.", "S0002", 208)));
        Assert.assertFalse(template.doesObjectAlreadyExist(
                sqlException("Timeout expired", "HYT00", 0)));
    }

    @Test
    void aPlatformErrorCodeStillMatchesWithoutAMessageMatch() {
        // Server-localized messages defeat message matching, which is why a platform that depends on this should
        // populate the error-code list as well.
        TestTemplate template = new TestTemplate();
        template.withErrorCodes(1913);
        Assert.assertTrue(template.doesObjectAlreadyExist(
                sqlException("Die Operation ist fehlgeschlagen: Index vorhanden", "S0001", 1913)));
    }

    @Test
    void nonSqlExceptionsAreNotClassified() {
        Assert.assertFalse(new TestTemplate().doesObjectAlreadyExist(new RuntimeException("already exists")));
    }

    @Test
    void toleranceIsOnByDefault() {
        Assert.assertTrue(new TestTemplate().isTolerateObjectAlreadyExists());
    }

    @Test
    void toleranceCanBeTurnedOffByParameter() {
        TestTemplate template = new TestTemplate();
        template.withProperty(SqlConstants.TOLERATE_OBJECT_ALREADY_EXISTS_ON_DDL, "false");
        Assert.assertFalse(template.isTolerateObjectAlreadyExists());
    }

    @Test
    void toleranceStaysOnWhenTheParameterIsSetToTrue() {
        TestTemplate template = new TestTemplate();
        template.withProperty(SqlConstants.TOLERATE_OBJECT_ALREADY_EXISTS_ON_DDL, "true");
        Assert.assertTrue(template.isTolerateObjectAlreadyExists());
    }
}
