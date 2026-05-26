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
package org.jumpmind.symmetric.integrate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * An abstract class that accumulates data to publish.
 */
@ManagedResource(description = "The management interface for an xml publisher")
abstract public class AbstractXmlPublisherExtensionPoint implements IExtensionPoint,
        INodeGroupExtensionPoint, ISymmetricEngineAware, BeanNameAware {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final String XML_CACHE = "XML_CACHE_" + this.hashCode();
    private String[] nodeGroups;
    protected IPublisher publisher;
    protected Set<String> relationNamesToPublishAsGroup;
    protected String xmlTagNameToUseForGroup = "batch";
    protected List<String> groupByColumnNames;
    protected Format xmlFormat;
    protected String name;
    protected ISymmetricEngine engine;
    protected long timeBetweenStatisticsPrintTime = 300000;
    protected transient long lastStatisticsPrintTime = System.currentTimeMillis();
    protected transient long numberOfMessagesPublishedSinceLastPrintTime = 0;
    protected transient long amountOfTimeToPublishMessagesSinceLastPrintTime = 0;
    protected ITimeGenerator timeStringGenerator = new ITimeGenerator() {
        public String getTime() {
            return Long.toString(System.currentTimeMillis());
        }
    };

    public AbstractXmlPublisherExtensionPoint() {
        xmlFormat = Format.getCompactFormat();
        xmlFormat.setOmitDeclaration(true);
    }

    @Override
    public void setBeanName(String name) {
        this.name = name;
    }

    @ManagedOperation(description = "Looks up rows in the database and resends them to the publisher")
    @ManagedOperationParameters({ @ManagedOperationParameter(
            name = "args",
            description = "A pipe deliminated list of key values to use to look up the relations to resend") })
    public boolean resend(String args) {
        try {
            String[] argArray = args != null ? args.split("\\|") : new String[0];
            DataContext context = new DataContext();
            IDatabasePlatform platform = engine.getDatabasePlatform();
            for (String relationName : relationNamesToPublishAsGroup) {
                Relation relation = platform.getRelationFromCache(relationName, false);
                List<String[]> dataRowsForRelation = readData(relation, argArray);
                for (String[] values : dataRowsForRelation) {
                    Batch batch = new Batch();
                    batch.setBinaryEncoding(engine.getSymmetricDialect().getBinaryEncoding());
                    batch.setSourceNodeId("republish");
                    context.setBatch(batch);
                    CsvData data = new CsvData(DataEventType.INSERT);
                    data.putParsedData(CsvData.ROW_DATA, values);
                    Element xml = getXmlFromCache(context, context.getBatch().getBinaryEncoding(),
                            relation.getColumnNames(), data.getParsedData(CsvData.ROW_DATA),
                            relation.getPrimaryKeyColumnNames(), data.getParsedData(CsvData.PK_DATA));
                    if (xml != null) {
                        toXmlElement(data.getDataEventType(), xml, relation.getCatalog(),
                                relation.getSchema(), relation.getName(), relation.getColumnNames(),
                                data.getParsedData(CsvData.ROW_DATA),
                                relation.getPrimaryKeyColumnNames(),
                                data.getParsedData(CsvData.PK_DATA));
                    }
                }
            }
            if (doesXmlExistToPublish(context)) {
                finalizeXmlAndPublish(context);
                return true;
            } else {
                log.warn("Failed to resend message for tables {}, columns {}, and args {}",
                        relationNamesToPublishAsGroup, groupByColumnNames, args);
            }
        } catch (RuntimeException ex) {
            log.error(String.format(
                    "Failed to resend message for tables %s, columns %s, and args %s",
                    relationNamesToPublishAsGroup, groupByColumnNames, args), ex);
        }
        return false;
    }

    @ManagedAttribute(description = "A comma separated list of columns that act as the key values for the relations that will be published")
    public String getKeyColumnNames() {
        return groupByColumnNames != null ? groupByColumnNames.toString() : "";
    }

    @ManagedAttribute(description = "A comma separated list of relations that will be published")
    public String getRelationNames() {
        return relationNamesToPublishAsGroup != null ? relationNamesToPublishAsGroup.toString() : "";
    }

    protected List<String[]> readData(final Relation relation, String[] args) {
        final IDatabasePlatform platform = engine.getDatabasePlatform();
        List<String[]> rows = new ArrayList<String[]>();
        final String[] columnNames = relation.getColumnNames();
        if (columnNames != null && columnNames.length > 0) {
            StringBuilder builder = new StringBuilder("select ");
            for (int i = 0; i < columnNames.length; i++) {
                String columnName = columnNames[i];
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(columnName);
            }
            builder.append(" from ").append(relation.getName()).append(" where ");
            for (int i = 0; i < groupByColumnNames.size(); i++) {
                String columnName = groupByColumnNames.get(i);
                if (i > 0 && i < groupByColumnNames.size()) {
                    builder.append(" and ");
                }
                builder.append(columnName).append("=?");
            }
            ISqlTemplate template = platform.getSqlTemplate();
            Object[] argObjs = platform.getObjectValues(engine.getSymmetricDialect()
                    .getBinaryEncoding(), args, relation.getColumnsWithName(groupByColumnNames
                            .toArray(new String[groupByColumnNames.size()])));
            rows = template.query(builder.toString(), new ISqlRowMapper<String[]>() {
                @Override
                public String[] mapRow(Row row) {
                    return platform.getStringValues(engine.getSymmetricDialect()
                            .getBinaryEncoding(), relation.getColumns(), row, false, false);
                }
            }, argObjs);
        }
        return rows;
    }

    protected final static Namespace getXmlNamespace() {
        return Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Element> getXmlCache(Context context) {
        Map<String, Element> xmlCache = (Map<String, Element>) context.get(XML_CACHE);
        if (xmlCache == null) {
            xmlCache = new HashMap<String, Element>();
            context.put(XML_CACHE, xmlCache);
        }
        return xmlCache;
    }

    @SuppressWarnings("unchecked")
    protected boolean doesXmlExistToPublish(Context context) {
        Map<String, StringBuilder> xmlCache = (Map<String, StringBuilder>) context.get(XML_CACHE);
        return xmlCache != null && xmlCache.size() > 0;
    }

    protected void finalizeXmlAndPublish(Context context) {
        Map<String, Element> contextCache = getXmlCache(context);
        Collection<Element> buffers = contextCache.values();
        for (Iterator<Element> iterator = buffers.iterator(); iterator.hasNext();) {
            String xml = new XMLOutputter(xmlFormat).outputString(new Document(iterator.next()));
            log.debug("Sending XML to IPublisher: {}", xml);
            iterator.remove();
            long ts = System.currentTimeMillis();
            publisher.publish(context, xml);
            amountOfTimeToPublishMessagesSinceLastPrintTime += (System.currentTimeMillis() - ts);
            numberOfMessagesPublishedSinceLastPrintTime++;
        }
        if ((System.currentTimeMillis() - lastStatisticsPrintTime) > timeBetweenStatisticsPrintTime) {
            synchronized (this) {
                if ((System.currentTimeMillis() - lastStatisticsPrintTime) > timeBetweenStatisticsPrintTime) {
                    log.info(name
                            + " published "
                            + numberOfMessagesPublishedSinceLastPrintTime
                            + " messages in the last "
                            + (System.currentTimeMillis() - lastStatisticsPrintTime)
                                    / 1000
                            + " seconds.  Spent "
                            + (amountOfTimeToPublishMessagesSinceLastPrintTime / numberOfMessagesPublishedSinceLastPrintTime)
                            + "ms of publishing time per message");
                    lastStatisticsPrintTime = System.currentTimeMillis();
                    numberOfMessagesPublishedSinceLastPrintTime = 0;
                    amountOfTimeToPublishMessagesSinceLastPrintTime = 0;
                }
            }
        }
    }

    protected void toXmlElement(DataEventType dml, Element xml, String catalogName,
            String schemaName, String relationName, String[] columnNames, String[] data,
            String[] keyNames, String[] keys) {
        Element row = new Element("row");
        xml.addContent(row);
        if (StringUtils.isNotBlank(catalogName)) {
            row.setAttribute("catalog", catalogName);
        }
        if (StringUtils.isNotBlank(schemaName)) {
            row.setAttribute("schema", schemaName);
        }
        row.setAttribute("entity", relationName);
        row.setAttribute("dml", dml.getCode());
        String[] colNames = null;
        if (data == null) {
            colNames = keyNames;
            data = keys;
        } else {
            colNames = columnNames;
        }
        for (int i = 0; i < data.length; i++) {
            String col = colNames[i];
            Element dataElement = new Element("data");
            row.addContent(dataElement);
            dataElement.setAttribute("key", col);
            if (data[i] != null) {
                dataElement.setText(replaceInvalidChars(data[i]));
            } else {
                dataElement.setAttribute("nil", "true", getXmlNamespace());
            }
        }
    }

    public String replaceInvalidChars(String in) {
        if (in == null || in.equals("")) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        char current;
        for (int i = 0; i < in.length(); i++) {
            current = in.charAt(i);
            if ((current == 0x9) || (current == 0xA) || (current == 0xD) ||
                    ((current >= 0x20) && (current <= 0xD7FF)) ||
                    ((current >= 0xE000) && (current <= 0xFFFD))) {
                out.append(current);
            }
        }
        return out.toString();
    }

    /**
     * Give the opportunity for the user of this publisher to add in additional attributes. The default implementation adds in the nodeId from the
     * {@link Context}.
     * 
     * @param context
     * @param xml
     *            append XML attributes to this buffer
     */
    protected void addFormattedExtraGroupAttributes(Context context, Element xml) {
        if (context instanceof DataContext) {
            DataContext dataContext = (DataContext) context;
            xml.setAttribute("nodeid", dataContext.getBatch().getSourceNodeId());
            xml.setAttribute("batchid", Long.toString(dataContext.getBatch().getBatchId()));
        }
        if (timeStringGenerator != null) {
            xml.setAttribute("time", timeStringGenerator.getTime());
        }
    }

    protected Element getXmlFromCache(Context context, BinaryEncoding binaryEncoding,
            String[] columnNames, String[] data, String[] keyNames, String[] keys) {
        Map<String, Element> xmlCache = getXmlCache(context);
        Element xml = null;
        String txId = toXmlGroupId(columnNames, data, keyNames, keys);
        if (txId != null) {
            xml = (Element) xmlCache.get(txId);
            if (xml == null) {
                xml = new Element(xmlTagNameToUseForGroup);
                xml.addNamespaceDeclaration(getXmlNamespace());
                xml.setAttribute("id", txId);
                xml.setAttribute("binary", binaryEncoding.name());
                addFormattedExtraGroupAttributes(context, xml);
                xmlCache.put(txId, xml);
            }
        }
        return xml;
    }

    protected String toXmlGroupId(String[] columnNames, String[] data, String[] keyNames,
            String[] keys) {
        if (groupByColumnNames != null) {
            StringBuilder id = new StringBuilder();
            if (keys != null) {
                String[] columns = keyNames;
                for (String col : groupByColumnNames) {
                    int index = ArrayUtils.indexOf(columns, col, 0);
                    if (index >= 0) {
                        id.append(keys[index]);
                    } else {
                        id = new StringBuilder();
                        break;
                    }
                }
            }
            if (id.length() == 0) {
                String[] columns = columnNames;
                for (String col : groupByColumnNames) {
                    int index = ArrayUtils.indexOf(columns, col, 0);
                    if (index >= 0) {
                        id.append(data[index]);
                    } else {
                        return null;
                    }
                }
            }
            if (id.length() > 0) {
                return id.toString();
            }
        } else {
            log.warn(
                    "You did not specify 'groupByColumnNames'.  We cannot find any matches in the data to publish as XML if you don't.  You might as well turn off this filter!");
        }
        return null;
    }

    public String[] getNodeGroupIdsToApplyTo() {
        return nodeGroups;
    }

    public void setNodeGroups(String[] nodeGroups) {
        this.nodeGroups = nodeGroups;
    }

    public void setNodeGroup(String nodeGroup) {
        this.nodeGroups = new String[] { nodeGroup };
    }

    public void setPublisher(IPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Used to populate the time attribute of an XML message.
     */
    public void setTimeStringGenerator(ITimeGenerator timeStringGenerator) {
        this.timeStringGenerator = timeStringGenerator;
    }

    public void setXmlFormat(Format xmlFormat) {
        this.xmlFormat = xmlFormat;
    }

    public void setRelationNamesToPublishAsGroup(Set<String> relationNamesToPublishAsGroup) {
        this.relationNamesToPublishAsGroup = relationNamesToPublishAsGroup;
    }

    public void setRelationNameToPublish(String relationName) {
        this.relationNamesToPublishAsGroup = new HashSet<String>(1);
        this.relationNamesToPublishAsGroup.add(relationName);
    }

    public void setXmlTagNameToUseForGroup(String xmlTagNameToUseForGroup) {
        this.xmlTagNameToUseForGroup = xmlTagNameToUseForGroup;
    }

    public void setTimeBetweenStatisticsPrintTime(long timeBetweenStatisticsPrintTime) {
        this.timeBetweenStatisticsPrintTime = timeBetweenStatisticsPrintTime;
    }

    /**
     * This attribute is required. It needs to identify the columns that will be used to key on rows in the specified relations that need to be grouped together
     * in an 'XML batch.'
     */
    public void setGroupByColumnNames(List<String> groupByColumnNames) {
        this.groupByColumnNames = groupByColumnNames;
    }

    public interface ITimeGenerator {
        public String getTime();
    }

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }
}
