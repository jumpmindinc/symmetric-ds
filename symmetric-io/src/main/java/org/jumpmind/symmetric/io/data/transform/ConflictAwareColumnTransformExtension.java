package org.jumpmind.symmetric.io.data.transform;

import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConflictAwareColumnTransformExtension implements ISingleNewAndOldValueColumnTransform, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final String NAME = "NotImplemented";

    public String getName() {
        return NAME;
    }

    @Override
    public boolean isExtractColumnTransform() {
        return true;
    }

    @Override
    public boolean isLoadColumnTransform() {
        return true;
    }

    @Override
    public NewAndOldValue transform(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        String newValueAfterTransform = "";
        if (data.getSourceDmlType() == DataEventType.INSERT || data.getSourceDmlType() == DataEventType.UPDATE) {
            newValueAfterTransform = transformNewValue(platform, context, column, data, sourceValues, newValue, oldValue);
        }
        String oldValueAfterTransform = "";
        if (data.getSourceDmlType() == DataEventType.DELETE || data.getSourceDmlType() == DataEventType.UPDATE) {
            oldValueAfterTransform = transformOldValue(platform, context, column, data, sourceValues, newValue, oldValue);
        }
        return new NewAndOldValue(newValueAfterTransform, oldValueAfterTransform);
    }

    public String transformNewValue(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        String message = String.format("Not implemented for SourceDmlType=%s; column=%s", data.getSourceDmlType(), column.getSourceColumnName());
        throw new NotImplementedException(message);
    }

    public String transformOldValue(IDatabasePlatform platform, DataContext context, TransformColumn column,
            TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue)
            throws IgnoreColumnException, IgnoreRowException {
        String message = String.format("Not implemented for SourceDmlType=%s; column=%s", data.getSourceDmlType(), column.getSourceColumnName());
        throw new NotImplementedException(message);
    }
}
