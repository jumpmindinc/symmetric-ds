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
package org.jumpmind.properties;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultParameterParser {
    private static final String COMMENT = "#";
    private static final String COMMENT_PRIVATE = "##";
    private static final String DATABASE_OVERRIDABLE = "DatabaseOverridable:";
    private static final String TAGS = "Tags:";
    private static final String TYPE = "Type:";
    private String propertiesFilePath;
    private InputStream inputStream;
    final Logger log = LoggerFactory.getLogger(getClass());

    public DefaultParameterParser(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public DefaultParameterParser(String propertiesFilePath) {
        this.propertiesFilePath = propertiesFilePath;
    }

    public Map<String, ParameterMetaData> parse() {
        return parse(propertiesFilePath);
    }

    public Map<String, ParameterMetaData> parse(String fileName) {
        Map<String, ParameterMetaData> metaData = new TreeMap<String, DefaultParameterParser.ParameterMetaData>();
        try {
            if (inputStream == null) {
                inputStream = getClass().getResourceAsStream(fileName);
            }
            List<String> lines = IOUtils.readLines(inputStream, Charset.defaultCharset());
            boolean extraLine = false;
            ParameterMetaData currentMetaData = new ParameterMetaData();
            for (String line : lines) {
                if (extraLine) {
                    extraLine = false;
                    if (currentMetaData != null) {
                        if (line.endsWith("\\")) {
                            extraLine = true;
                            line = line.substring(0, line.length() - 1);
                        }
                        line = StringEscapeUtils.unescapeJava(line);
                        currentMetaData.setDefaultValue(currentMetaData.getDefaultValue() + line);
                    }
                    if (!extraLine) {
                        currentMetaData = new ParameterMetaData();
                    }
                } else if (line.trim().startsWith(COMMENT_PRIVATE)) {
                    // exclude private comment from documentation
                } else if (line.trim().startsWith(COMMENT) && line.length() > 1) {
                    line = line.substring(line.indexOf(COMMENT) + 1);
                    if (line.contains(DATABASE_OVERRIDABLE)) {
                        currentMetaData.setDatabaseOverridable(Boolean.parseBoolean(line.substring(
                                line.indexOf(DATABASE_OVERRIDABLE) + DATABASE_OVERRIDABLE.length())
                                .trim()));
                    } else if (line.contains(TAGS)) {
                        String[] tags = line.substring(line.indexOf(TAGS) + TAGS.length()).trim()
                                .split(",");
                        for (String tag : tags) {
                            currentMetaData.addTag(tag.trim());
                        }
                    } else if (line.contains(TYPE)) {
                        String type = line.substring(line.indexOf(TYPE) + TYPE.length());
                        currentMetaData.setType(type.trim());
                    } else {
                        currentMetaData.appendDescription(line);
                    }
                } else if (!line.trim().startsWith(COMMENT) && line.contains("=")) {
                    String key = line.substring(0, line.indexOf("="));
                    String defaultValue = line.substring(line.indexOf("=") + 1);
                    currentMetaData.setKey(key);
                    if (defaultValue.endsWith("\\")) {
                        extraLine = true;
                        defaultValue = defaultValue.substring(0, defaultValue.length() - 1);
                    }
                    defaultValue = StringEscapeUtils.unescapeJava(defaultValue);
                    currentMetaData.setDefaultValue(defaultValue);
                    metaData.put(key, currentMetaData);
                    if (!extraLine) {
                        currentMetaData = new ParameterMetaData();
                    }
                } else if (StringUtils.isBlank(line)) {
                    // reset the metadata
                    currentMetaData = new ParameterMetaData();
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
        return metaData;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: <input_properties_file> <output_docbook_file> [true|false]");
        }
        String[] paths;
        List<DefaultParameterParser> parmParsers = new ArrayList<DefaultParameterParser>();
        if (args[0].startsWith("classpath:")) {
            paths = args[0].replaceAll("classpath:", "").split(",");
            for (String path : paths) {
                parmParsers.add(new DefaultParameterParser(path));
            }
        } else {
            paths = args[0].split(",");
            for (String path : paths) {
                parmParsers.add(new DefaultParameterParser(FileUtils.openInputStream(new File(path))));
            }
        }
        File file = new File(args[1]);
        file.getParentFile().mkdirs();
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
        }
        boolean isDatabaseOverridable = Boolean.parseBoolean(args[2]);
        boolean isAsciiDocFormat = args.length > 3 && "asciidoc".equals(args[3]);
        TreeMap<String, ParameterMetaData> map = new TreeMap<String, ParameterMetaData>();
        for (DefaultParameterParser parser : parmParsers) {
            map.putAll(parser.parse());
        }
        if (!isAsciiDocFormat) {
            writer.write("<variablelist>\n");
        }
        for (ParameterMetaData parm : map.values()) {
            if ((isDatabaseOverridable && parm.isDatabaseOverridable())
                    || (!isDatabaseOverridable && !parm.isDatabaseOverridable())) {
                if (!isAsciiDocFormat) {
                    writer.write("<varlistentry>\n<term><command>" + parm.getKey()
                            + "</command></term>\n");
                    writer.write("<listitem><para>"
                            + parm.getDescription()
                            + " [ Default: "
                            + (parm.isXmlType() ? StringEscapeUtils.escapeXml10(parm
                                    .getDefaultValue()) : parm.getDefaultValue())
                            + " ]</para></listitem>\n</varlistentry>\n");
                } else {
                    writer.write(parm.getKey()
                            + ":: "
                            + parm.getDescription()
                            + "\n\n_Default:_ "
                            + (parm.isXmlType() ? "\n\n[source, xml]\n----\n" + parm
                                    .getDefaultValue() + "\n----\n\n" : (isBlank(parm.getDefaultValue()) ? "" : "_" + parm.getDefaultValue() + "_") + "\n\n"));
                }
            }
        }
        if (!isAsciiDocFormat) {
            writer.write("</variablelist>\n");
        }
        writer.close();
    }

    public static class ParameterMetaData implements Serializable {
        public static final String TYPE_BOOLEAN = "boolean";
        public static final String TYPE_INT = "integer";
        public static final String TYPE_TEXT_BOX = "textbox";
        public static final String TYPE_SQL = "sql";
        public static final String TYPE_CODE = "code";
        public static final String TYPE_XML = "xml";
        public static final String TYPE_ENCRYPTED = "encrypted";
        public static final String TAG_TRIGGER = "trigger";
        private static final long serialVersionUID = 1L;
        private String key;
        private String description;
        private Set<String> tags = new HashSet<String>();
        private boolean databaseOverridable;
        private String defaultValue;
        private String type = "";

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Set<String> getTags() {
            return tags;
        }

        public void setTags(Set<String> tags) {
            this.tags = tags;
        }

        public boolean isDatabaseOverridable() {
            return databaseOverridable;
        }

        public void setDatabaseOverridable(boolean databaseOverridable) {
            this.databaseOverridable = databaseOverridable;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public void appendDescription(String value) {
            if (description == null) {
                description = value;
            } else {
                description = description + value;
            }
        }

        public boolean isXmlType() {
            return type != null && type.equals(TYPE_XML);
        }

        public boolean isSqlType() {
            return type != null && type.equals(TYPE_SQL);
        }

        public boolean isCodeType() {
            return type != null && type.equals(TYPE_CODE);
        }

        public boolean isBooleanType() {
            return type != null && type.equals(TYPE_BOOLEAN);
        }

        public boolean isIntType() {
            return type != null && type.equals(TYPE_INT);
        }

        public boolean isTextBoxType() {
            return type != null && type.equals(TYPE_TEXT_BOX);
        }

        public boolean isEncryptedType() {
            return type != null && type.equals(TYPE_ENCRYPTED);
        }

        public void addTag(String tag) {
            tags.add(tag);
        }
    }
}
