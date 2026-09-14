/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link LinkedHashMap} variant that stores String keys in a case-insensitive manner, for example for key-based access in a results table.
 *
 * <p>
 * Preserves the original order as well as the original casing of keys, while allowing for contains, get and remove calls with any case of key.
 *
 * <p>
 * Does <i>not</i> support <code>null</code> keys.
 *
 * @author Juergen Hoeller
 * @since 3.0
 */
public class LinkedCaseInsensitiveMap<V> extends LinkedHashMap<String, V> {
    private static final long serialVersionUID = 1L;
    private final Map<String, String> caseInsensitiveKeys;
    private final Locale locale;

    public LinkedCaseInsensitiveMap(Map<String, ? extends V> values) {
        this();
        putAll(values);
    }

    /**
     * Create a new LinkedCaseInsensitiveMap for the default Locale.
     * 
     * @see java.lang.String#toLowerCase()
     */
    public LinkedCaseInsensitiveMap() {
        this((Locale) null);
    }

    /**
     * Create a new LinkedCaseInsensitiveMap that stores lower-case keys according to the given Locale.
     * 
     * @param locale
     *            the Locale to use for lower-case conversion
     * @see java.lang.String#toLowerCase(java.util.Locale)
     */
    public LinkedCaseInsensitiveMap(Locale locale) {
        super();
        this.caseInsensitiveKeys = new HashMap<String, String>();
        this.locale = (locale != null ? locale : Locale.getDefault());
    }

    /**
     * Create a new LinkedCaseInsensitiveMap that wraps a {@link LinkedHashMap} with the given initial capacity and stores lower-case keys according to the
     * default Locale.
     * 
     * @param initialCapacity
     *            the initial capacity
     * @see java.lang.String#toLowerCase()
     */
    public LinkedCaseInsensitiveMap(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Create a new LinkedCaseInsensitiveMap that wraps a {@link LinkedHashMap} with the given initial capacity and stores lower-case keys according to the
     * given Locale.
     * 
     * @param initialCapacity
     *            the initial capacity
     * @param locale
     *            the Locale to use for lower-case conversion
     * @see java.lang.String#toLowerCase(java.util.Locale)
     */
    public LinkedCaseInsensitiveMap(int initialCapacity, Locale locale) {
        super(initialCapacity);
        this.caseInsensitiveKeys = new HashMap<String, String>(initialCapacity);
        this.locale = (locale != null ? locale : Locale.getDefault());
    }

    @Override
    public V put(String key, V value) {
        this.caseInsensitiveKeys.put(convertKey(key), key);
        if (FormatUtils.isInfamousTurkey()) {
            this.caseInsensitiveKeys.put(FormatUtils.stripTurkeyDottedI(convertKey(key)), key);
        }
        return super.put(key, value);
    }

    @Override
    public final void putAll(Map<? extends String, ? extends V> map) {
        if (map != null) {
            Set<? extends String> keys = map.keySet();
            for (String key : keys) {
                put(key, map.get(key));
            }
        }
    }

    @Override
    public boolean containsKey(Object key) {
        return (key instanceof String && this.caseInsensitiveKeys.containsKey(convertKey((String) key)));
    }

    @Override
    public V get(Object key) {
        if (key instanceof String) {
            return super.get(this.caseInsensitiveKeys.get(convertKey((String) key)));
        } else {
            return null;
        }
    }

    @Override
    public V remove(Object key) {
        if (key instanceof String) {
            return super.remove(this.caseInsensitiveKeys.remove(convertKey((String) key)));
        } else {
            return null;
        }
    }

    @Override
    public void clear() {
        this.caseInsensitiveKeys.clear();
        super.clear();
    }

    /**
     * Convert the given key to a case-insensitive key.
     * <p>
     * The default implementation converts the key to lower-case according to this Map's Locale.
     * 
     * @param key
     *            the user-specified key
     * @return the key to use for storing
     * @see java.lang.String#toLowerCase(java.util.Locale)
     */
    protected String convertKey(String key) {
        return key.toLowerCase(this.locale);
    }
}
