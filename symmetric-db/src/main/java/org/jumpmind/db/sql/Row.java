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
package org.jumpmind.db.sql;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.exception.ParseException;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.LinkedCaseInsensitiveMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Row extends LinkedCaseInsensitiveMap<Object> {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(Row.class);

    public Row(int numberOfColumns) {
        super(numberOfColumns);
    }

    public Row(String columnName, Object value) {
        super(1);
        put(columnName, value);
    }

    public Row(String[] names, Object[] values) {
        for (int i = 0; i < names.length && i < values.length; i++) {
            put(names[i], values[i]);
        }
    }

    public byte[] bytesValue() {
        Object obj = this.values().iterator().next();
        return toBytes(obj);
    }

    protected byte[] toBytes(Object obj) {
        if (obj != null) {
            if (obj instanceof byte[]) {
                return (byte[]) obj;
            } else if (obj instanceof Blob) {
                Blob blob = (Blob) obj;
                try {
                    return IOUtils.toByteArray(blob.getBinaryStream());
                } catch (IOException e) {
                    throw new IoException(e);
                } catch (SQLException e) {
                    throw new SqlException(e);
                }
            } else if (obj instanceof String) {
                return obj.toString().getBytes(Charset.defaultCharset());
            } else {
                throw new IllegalStateException(String.format(
                        "Cannot translate a %s into a byte[]", obj.getClass().getName()));
            }
        } else {
            return null;
        }
    }

    public Number numberValue() {
        Object obj = this.values().iterator().next();
        if (obj != null) {
            if (obj instanceof Number) {
                return (Number) obj;
            } else {
                return new BigDecimal(obj.toString());
            }
        } else {
            return null;
        }
    }

    public Date dateValue() {
        Object obj = this.values().iterator().next();
        if (obj != null) {
            if (obj instanceof Date) {
                return (Date) obj;
            } else {
                return Timestamp.valueOf(obj.toString());
            }
        } else {
            return null;
        }
    }

    public Long longValue() {
        Object obj = this.values().iterator().next();
        if (obj != null) {
            if (obj instanceof Long) {
                return (Long) obj;
            } else if (obj instanceof Double) {
                return ((Double) obj).longValue();
            } else {
                return Long.valueOf(obj.toString());
            }
        } else {
            return null;
        }
    }

    public String stringValue() {
        Object obj = this.values().iterator().next();
        if (obj != null) {
            return obj.toString();
        } else {
            return null;
        }
    }

    public String csvValue() {
        StringBuilder concatenatedRow = new StringBuilder();
        Collection<Object> objs = this.values();
        int index = 0;
        for (Object obj : objs) {
            if (index > 0) {
                concatenatedRow.append(",");
            }
            if (obj != null) {
                if (obj instanceof byte[]) {
                    concatenatedRow.append(Hex.encodeHexString((byte[]) obj));
                } else {
                    concatenatedRow.append(obj.toString());
                }
            }
            index++;
        }
        return concatenatedRow.toString();
    }

    public byte[] getBytes(String columnName) {
        Object obj = get(columnName);
        return toBytes(obj);
    }

    public String getString(String columnName) {
        return getString(columnName, true);
    }

    public String getString(String columnName, boolean checkForColumn) {
        Object obj = this.get(columnName);
        if (obj != null) {
            if (obj instanceof String) {
                return (String) obj;
            } else if (obj instanceof BigDecimal) {
                return ((BigDecimal) obj).toPlainString();
            } else if (obj instanceof byte[]) {
                return Hex.encodeHexString((byte[]) obj);
            } else {
                return obj.toString();
            }
        } else {
            if (checkForColumn) {
                checkForColumn(columnName);
            }
            return null;
        }
    }

    public int getInt(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            return Integer.parseInt(obj.toString());
        } else {
            checkForColumn(columnName);
            return 0;
        }
    }

    public Integer getInteger(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            return Integer.parseInt(obj.toString());
        } else {
            return null;
        }
    }

    public long getLong(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else if (obj instanceof String) {
            return Long.parseLong(obj.toString());
        } else {
            checkForColumn(columnName);
            return 0;
        }
    }

    public float getFloat(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Number) {
            return ((Number) obj).floatValue();
        } else if (obj instanceof String) {
            return Float.parseFloat(obj.toString());
        } else {
            checkForColumn(columnName);
            return 0;
        }
    }

    public BigDecimal getBigDecimal(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        } else if (obj instanceof String) {
            return new BigDecimal(obj.toString());
        } else if (obj instanceof Integer) {
            return new BigDecimal(((Integer) obj).intValue());
        } else {
            checkForColumn(columnName);
            return null;
        }
    }

    public boolean getBoolean(String columnName) {
        Object obj = this.get(columnName);
        if ("1".equals(obj)) {
            return true;
        } else if (obj instanceof Number) {
            int value = ((Number) obj).intValue();
            return value > 0 ? true : false;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        } else {
            checkForColumn(columnName);
            return false;
        }
    }

    public Time getTime(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Time) {
            return (Time) obj;
        } else {
            Date date = getDateTime(columnName);
            return new Time(date.getTime());
        }
    }

    public Timestamp getTimestamp(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Timestamp) {
            return (Timestamp) obj;
        } else if (obj instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) obj);
        } else if (obj instanceof LocalDate) {
            return Timestamp.valueOf(((LocalDate) obj).atStartOfDay());
        } else {
            Date date = getDateTime(columnName);
            if (date != null) {
                return new Timestamp(date.getTime());
            }
            return null;
        }
    }

    public Date getDateTime(String columnName) {
        Object obj = this.get(columnName);
        if (obj instanceof Number) {
            long value = ((Number) obj).longValue();
            return new Date(value);
        } else if (obj instanceof Date) {
            return (Date) obj;
        } else if (obj instanceof String) {
            try {
                return getDate((String) obj, FormatUtils.TIMESTAMP_PATTERNS);
            } catch (ParseException ex) {
                // on xerial sqlite jdbc dates come back as longs
                return new Date(Long.parseLong((String) obj));
            }
        } else if (obj instanceof LocalDateTime) {
            return new Date(Timestamp.valueOf((LocalDateTime) obj).getTime());
        } else if (obj instanceof LocalDate) {
            return new Date(Timestamp.valueOf(((LocalDate) obj).atStartOfDay()).getTime());
        } else {
            checkForColumn(columnName);
            return null;
        }
    }

    protected void checkForColumn(String columnName) {
        if (!containsKey(columnName)) {
            throw new ColumnNotFoundException(columnName);
        }
    }

    final private java.util.Date getDate(String value, String[] patterns) {
        int spaceIndex = value.lastIndexOf(" ");
        int fractionIndex = value.lastIndexOf(".");
        if (spaceIndex > 0 && fractionIndex > 0 && value.substring(fractionIndex, value.length()).length() > 3) {
            int plusIndex = value.lastIndexOf("+");
            int minusIndex = value.substring(fractionIndex).lastIndexOf("-");
            if (plusIndex != -1 || minusIndex != -1) {
                return FormatUtils.parseTimestampWithTimezone(value);
            }
            return Timestamp.valueOf(value);
        }
        return FormatUtils.parseDate(value, patterns);
    }

    public Object[] toArray(String[] keys) {
        Object[] values = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = get(keys[i]);
        }
        return values;
    }

    public Object[] toArray() {
        Set<String> keys = keySet();
        Object[] values = new Object[keys.size()];
        int i = 0;
        for (String key : keys) {
            values[i++] = get(key);
        }
        return values;
    }

    public String[] toStringArray(String[] keys) {
        String[] values = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            values[i] = getString(keys[i]);
        }
        return values;
    }

    public long getLength() {
        long length = 0;
        for (Map.Entry<String, Object> entry : this.entrySet()) {
            try {
                Object obj = entry.getValue();
                if (obj instanceof Blob) {
                    length += ((Blob) obj).length();
                } else if (obj instanceof Clob) {
                    length += ((Clob) obj).length();
                } else {
                    length += obj.toString().length();
                }
            } catch (SQLException se) {
                log.warn("Unable to determine length of row, failure on column " + entry.getKey(), se);
            }
        }
        return length;
    }
}
