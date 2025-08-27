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

public class IsEmptyTransformTest extends AbstractTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IsEmptyTransform isEmptyTransform;

    @BeforeEach
    void setup() {
        isEmptyTransform = new IsEmptyTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
    }

    @Test
    void testGetName() {
        assertEquals("isEmpty", isEmptyTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(isEmptyTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(isEmptyTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withDelete_returnsNewAndOldValueWhenNotEmpty() throws IgnoreColumnException, IgnoreRowException {
        NewValue newValue = NewValue.of("new");
        OldValue oldValue = OldValue.of("old");
        testTransformDelete(Expected.of(newValue, oldValue), newValue, oldValue, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withDelete_returnsExpressionWhenNewValueIsEmpty() throws IgnoreColumnException, IgnoreRowException {
        String empty = "";
        String expression = "expression";
        OldValue oldValue = OldValue.of("old");
        testTransformDelete(Expected.of(NewValue.of(expression), oldValue), NewValue.of(empty), oldValue, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withDelete_returnsNullWhenNewValueIsEmptyAndExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        OldValue oldValue = OldValue.of("old");
        testTransformDelete(Expected.of(null, oldValue), NewValue.of(""), oldValue, TransformExpression.of(null));
    }

    @Test
    void testTransform_withUpdate_returnsNewAndOldValueWhenNotEmpty() throws IgnoreColumnException, IgnoreRowException {
        NewValue newValue = NewValue.of("new");
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(newValue, oldValue), newValue, oldValue, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withUpdate_returnsExpressionWhenNewValueIsEmpty() throws IgnoreColumnException, IgnoreRowException {
        String empty = "";
        String expression = "expression";
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(NewValue.of(expression), oldValue), NewValue.of(empty), oldValue, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withUpdate_returnsNullWhenNewValueIsEmptyAndExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(null, oldValue), NewValue.of(""), oldValue, TransformExpression.of(null));
    }

    @Test
    void testTransform_withInsert_returnsNewValueWhenNotEmpty() throws IgnoreColumnException, IgnoreRowException {
        NewValue newValue = NewValue.of("new");
        testTransformInsert(Expected.of(newValue, null), newValue, null, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withInsert_returnsExpressionWhenNewValueIsEmpty() throws IgnoreColumnException, IgnoreRowException {
        String empty = "";
        String expression = "expression";
        testTransformInsert(Expected.of(NewValue.of(expression), null), NewValue.of(empty), null, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withInsert_returnsNullWhenNewValueIsEmptyAndExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformInsert(Expected.of(null, null), NewValue.of(""), null, TransformExpression.of(null));
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

    private void testTransform(Expected expected,
            DataEventType eventType,
            NewValue newValue,
            OldValue oldValue, TransformExpression transformExpression) throws IgnoreColumnException, IgnoreRowException {
        when(transformColumn.getTransformExpression()).thenReturn(transformExpression.get());
        when(transformedData.getSourceDmlType()).thenReturn(eventType);
        NewAndOldValue result = isEmptyTransform.transform(
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
