package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IsNullTransformTest extends AbstractTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IsNullTransform isNullTransform;

    @BeforeEach
    void setup() {
        isNullTransform = new IsNullTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
    }

    @Test
    void testGetName() {
        assertEquals("isNull", isNullTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(isNullTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(isNullTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withDelete_returnsOldValueWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String oldValue = "old";
        testTransformDelete(Expected.of(NewValue.of(oldValue), null), null, OldValue.of(oldValue), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withDelete_returnsExpressionWhenNewAndOldValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformDelete(Expected.of(NewValue.of(expression), null), null, null, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withDelete_returnsOldValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String oldValue = "old";
        testTransformDelete(Expected.of(NewValue.of(oldValue), null), NewValue.of("new"), OldValue.of(oldValue), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withDelete_returnsExpressionWhenOldValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformDelete(Expected.of(NewValue.of(expression), null), NewValue.of("new"), OldValue.of(null), TransformExpression.of(expression));
    }

    @Test
    void testTransform_withDelete_returnsEmptyWhenOldValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformDelete(Expected.of(NewValue.of(""), null), NewValue.of("new"), OldValue.of(null), TransformExpression.of(null));
    }

    @Test
    void testTransform_withUpdate_returnsNewValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String newValue = "new";
        testTransformUpdate(Expected.of(NewValue.of(newValue), null), NewValue.of(newValue), OldValue.of("old"), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withUpdate_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformUpdate(Expected.of(NewValue.of(expression), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(expression));
    }

    @Test
    void testTransform_withUpdate_returnsEmptyWhenNewValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformUpdate(Expected.of(NewValue.of(""), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(null));
    }

    @Test
    void testTransform_withInsert_returnsNewValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String newValue = "new";
        testTransformInsert(Expected.of(NewValue.of(newValue), null), NewValue.of(newValue), OldValue.of("old"), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withInsert_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformInsert(Expected.of(NewValue.of(expression), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(expression));
    }

    @Test
    void testTransform_withInsert_returnsEmptyWhenNewValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformInsert(Expected.of(NewValue.of(""), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(null));
    }

    @Test
    void testTransform_withCreate_returnsNewValueWhenNotNull() throws IgnoreColumnException, IgnoreRowException {
        String newValue = "new";
        testTransformCreate(Expected.of(NewValue.of(newValue), null), NewValue.of(newValue), OldValue.of("old"), TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withCreate_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformCreate(Expected.of(NewValue.of(expression), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(expression));
    }

    @Test
    void testTransform_withCreate_returnsEmptyWhenNewValueAndExpressionAreNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformCreate(Expected.of(NewValue.of(""), null), NewValue.of(null), OldValue.of("old"), TransformExpression.of(null));
    }

    private void testTransformDelete(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.DELETE, newValue, oldValue, transformExpression);
    }

    private void testTransformUpdate(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.UPDATE, newValue, oldValue, transformExpression);
    }

    private void testTransformInsert(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.INSERT, newValue, oldValue, transformExpression);
    }

    private void testTransformCreate(Expected expected, NewValue newValue, OldValue oldValue, TransformExpression transformExpression)
            throws IgnoreColumnException, IgnoreRowException {
        testTransform(expected, DataEventType.CREATE, newValue, oldValue, transformExpression);
    }

    private void testTransform(Expected expected,
            DataEventType eventType,
            NewValue newValue,
            OldValue oldValue, TransformExpression transformExpression) throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn(transformExpression.get());
        when(transformedData.getSourceDmlType()).thenReturn(eventType);
        NewAndOldValue result = isNullTransform.transform(
                platform,
                dataContext,
                transformColumn,
                transformedData,
                sourceValues,
                ((newValue != null) ? newValue.get() : null),
                ((oldValue != null) ? oldValue.get() : null));
        expected.assertMatches(result.newValue, result.oldValue);
    }
}
