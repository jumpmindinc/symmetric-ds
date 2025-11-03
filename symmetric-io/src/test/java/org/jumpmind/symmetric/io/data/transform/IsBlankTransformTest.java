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

public class IsBlankTransformTest extends AbstractTransformTest {
    private IDatabasePlatform platform;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private IsBlankTransform isBlankTransform;

    @BeforeEach
    void setup() {
        isBlankTransform = new IsBlankTransform();
        platform = mock(IDatabasePlatform.class);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        transformedData = mock(TransformedData.class);
    }

    @Test
    void testGetName() {
        assertEquals("isBlank", isBlankTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(isBlankTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(isBlankTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withDelete_returnsExpressionForNewValueWhenNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        OldValue oldValue = OldValue.of("old");
        testTransformDelete(Expected.of(NewValue.of(expression), oldValue), null, oldValue, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withDelete_returnsNullForNewValueWhenExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        OldValue oldValue = OldValue.of("old");
        testTransformDelete(Expected.of(null, oldValue), null, oldValue, TransformExpression.of(null));
    }

    @Test
    void testTransform_withUpdate_returnsNewAndOldValue() throws IgnoreColumnException, IgnoreRowException {
        NewValue newValue = NewValue.of("new");
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(newValue, oldValue), newValue, oldValue, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withUpdate_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(NewValue.of(expression), oldValue), null, oldValue, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withUpdate_returnsNullWhenNewValueIsNullAndExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        OldValue oldValue = OldValue.of("old");
        testTransformUpdate(Expected.of(null, oldValue), null, oldValue, TransformExpression.of(null));
    }

    @Test
    void testTransform_withInsert_returnsNewValue() throws IgnoreColumnException, IgnoreRowException {
        NewValue newValue = NewValue.of("new");
        testTransformInsert(Expected.of(newValue, null), newValue, null, TransformExpression.of("expression"));
    }

    @Test
    void testTransform_withInsert_returnsExpressionWhenNewValueIsNull() throws IgnoreColumnException, IgnoreRowException {
        String expression = "expression";
        testTransformInsert(Expected.of(NewValue.of(expression), null), null, null, TransformExpression.of(expression));
    }

    @Test
    void testTransform_withInsert_returnsNullWhenNewValueIsNullAndExpressionIsNull() throws IgnoreColumnException, IgnoreRowException {
        testTransformInsert(Expected.of(null, null), null, null, TransformExpression.of(null));
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
        NewAndOldValue result = isBlankTransform.transform(
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
