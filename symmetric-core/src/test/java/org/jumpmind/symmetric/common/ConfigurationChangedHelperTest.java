package org.jumpmind.symmetric.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationChangedHelperTest {

    private ConfigurationChangedHelper helper;
    private ISymmetricEngine engine;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        helper = new ConfigurationChangedHelper(engine);
    }

    @Test
    void testGetSqlStatements() {
        String script = "CREATE TABLE customers (ID INT);";
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(script), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithDelimiter() {
        String statement = "CREATE TABLE customers (ID int)-";
        String script = "delimiter -;\n" + statement;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(statement), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithComments() {
        String statement = "CREATE TABLE customers (ID int);";
        String script = "--todo:test\n" + statement;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(1, result.size());
        assertEquals(removeTrailingDelimiter(statement), result.get(0));
    }

    @Test
    void testGetSqlStatementsWithMultiple() {
        String statement0 = "CREATE TABLE customers (ID int);";
        String statement1 = "INSERT INTO items VALUES (1);";
        String script = statement0 + "\n" + statement1;
        List<String> result = helper.getSqlStatements(script);
        assertEquals(2, result.size());
        assertEquals(removeTrailingDelimiter(statement0), result.get(0));
        assertEquals(removeTrailingDelimiter(statement1), result.get(1));
    }

    private static String removeTrailingDelimiter(String input) {
        if (input != null && (input.endsWith(";") || input.endsWith("-"))) {
            return input.substring(0, input.length() - 1);
        }
        return input;
    }
}
