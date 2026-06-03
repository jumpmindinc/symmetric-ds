package org.jumpmind.symmetric.io.data.transform;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class JsonColumnTransformTest {
    @Test
    public void test() {
        DataContext context = new DataContext();
        Table table = new Table();
        table.addColumns(new String[] { "id", "col0", "col1", "col2" });
        context.setRelation(table);
        CsvData data = new CsvData();
        data.putParsedData(CsvData.OLD_DATA, new String[] { "0", "old_val0", "old_val1", "old_val2" });
        context.setData(data);
        JsonColumnTransform transform = new JsonColumnTransform();
        TransformColumn noHeaderColumn = new TransformColumn();
        noHeaderColumn.setTransformExpression("\"" + JsonColumnTransform.EXCLUDE_COLUMN_NAMES + "==col0,col1\"");
        TransformColumn dataHeaderColumn = new TransformColumn();
        dataHeaderColumn.setTransformExpression("\"" + JsonColumnTransform.EXCLUDE_COLUMN_NAMES + "==col0,col1\",\""
                + JsonColumnTransform.DATA_HEADER + "==data\"");
        TransformColumn idAndDataHeaderColumn = new TransformColumn();
        idAndDataHeaderColumn.setTransformExpression("\"" + JsonColumnTransform.EXCLUDE_COLUMN_NAMES + "==col0,col1\",\""
                + JsonColumnTransform.DATA_HEADER + "==data\",\"" + JsonColumnTransform.ID_HEADER + "==_id\",\""
                + JsonColumnTransform.ID_COLUMN_NAME + "==id\"");
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("id", "0");
        sourceValues.put("col0", "val0");
        sourceValues.put("col1", "val1");
        sourceValues.put("col2", "val2");
        try {
            NewAndOldValue noHeaderResult = transform.transform(null, context, noHeaderColumn, null, sourceValues, null, null);
            Assert.assertEquals("Failed to transform old data into JSON without header",
                    "{\"id\":\"0\",\"col2\":\"old_val2\"}", noHeaderResult.getOldValue());
            Assert.assertEquals("Failed to transform new data into JSON without header",
                    "{\"id\":\"0\",\"col2\":\"val2\"}", noHeaderResult.getNewValue());
            NewAndOldValue dataHeaderResult = transform.transform(null, context, dataHeaderColumn, null, sourceValues, null, null);
            Assert.assertEquals("Failed to transform old data into JSON with data header",
                    "{\"data\":{\"id\":\"0\",\"col2\":\"old_val2\"}}", dataHeaderResult.getOldValue());
            Assert.assertEquals("Failed to transform new data into JSON with data header",
                    "{\"data\":{\"id\":\"0\",\"col2\":\"val2\"}}", dataHeaderResult.getNewValue());
            NewAndOldValue idAndDataHeaderResult = transform.transform(null, context, idAndDataHeaderColumn, null, sourceValues, null, null);
            Assert.assertEquals("Failed to transform old data into JSON with ID and data header",
                    "{\"_id\":\"0\",\"data\":{\"id\":\"0\",\"col2\":\"old_val2\"}}", idAndDataHeaderResult.getOldValue());
            Assert.assertEquals("Failed to transform new data into JSON with ID and data header",
                    "{\"_id\":\"0\",\"data\":{\"id\":\"0\",\"col2\":\"val2\"}}", idAndDataHeaderResult.getNewValue());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
}
