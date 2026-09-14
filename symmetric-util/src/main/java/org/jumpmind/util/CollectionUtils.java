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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CollectionUtils {
    public static <T> Map<String, T> toMap(String[] keyNames, T[] values) {
        if (values != null && keyNames != null && values.length >= keyNames.length) {
            Map<String, T> map = new HashMap<String, T>(keyNames.length);
            for (int i = 0; i < keyNames.length; i++) {
                map.put(keyNames[i], values[i]);
            }
            return map;
        } else {
            return new HashMap<String, T>(0);
        }
    }

    public static Map<String, String> toMap(Properties properties) {
        Map<String, String> map = new HashMap<String, String>(properties.size());
        for (Object key : properties.keySet()) {
            map.put((String) key, (String) properties.get(key));
        }
        return map;
    }

    /**
     * Copies the specified range of the specified array into a new array. The initial index of the range (<tt>from</tt>) must lie between zero and
     * <tt>original.length</tt>, inclusive. The value at <tt>original[from]</tt> is placed into the initial element of the copy (unless
     * <tt>from == original.length</tt> or <tt>from == to</tt>). Values from subsequent elements in the original array are placed into subsequent elements in
     * the copy. The final index of the range (<tt>to</tt>), which must be greater than or equal to <tt>from</tt>, may be greater than <tt>original.length</tt>,
     * in which case <tt>null</tt> is placed in all elements of the copy whose index is greater than or equal to <tt>original.length - from</tt>. The length of
     * the returned array will be <tt>to - from</tt>.
     * <p>
     * The resulting array is of exactly the same class as the original array.
     *
     * @param original
     *            the array from which a range is to be copied
     * @param from
     *            the initial index of the range to be copied, inclusive
     * @param to
     *            the final index of the range to be copied, exclusive. (This index may lie outside the array.)
     * @return a new array containing the specified range from the original array, truncated or padded with nulls to obtain the required length
     * @throws ArrayIndexOutOfBoundsException
     *             if <tt>from &lt; 0</tt> or <tt>from &gt; original.length()</tt>
     * @throws IllegalArgumentException
     *             if <tt>from &gt; to</tt>
     * @throws NullPointerException
     *             if <tt>original</tt> is null
     * @since 1.6
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] copyOfRange(T[] original, int from, int to) {
        return copyOfRange(original, from, to, (Class<T[]>) original.getClass());
    }

    /**
     * Copies the specified range of the specified array into a new array. The initial index of the range (<tt>from</tt>) must lie between zero and
     * <tt>original.length</tt>, inclusive. The value at <tt>original[from]</tt> is placed into the initial element of the copy (unless
     * <tt>from == original.length</tt> or <tt>from == to</tt>). Values from subsequent elements in the original array are placed into subsequent elements in
     * the copy. The final index of the range (<tt>to</tt>), which must be greater than or equal to <tt>from</tt>, may be greater than <tt>original.length</tt>,
     * in which case <tt>null</tt> is placed in all elements of the copy whose index is greater than or equal to <tt>original.length - from</tt>. The length of
     * the returned array will be <tt>to - from</tt>. The resulting array is of the class <tt>newType</tt>.
     *
     * @param original
     *            the array from which a range is to be copied
     * @param from
     *            the initial index of the range to be copied, inclusive
     * @param to
     *            the final index of the range to be copied, exclusive. (This index may lie outside the array.)
     * @param newType
     *            the class of the copy to be returned
     * @return a new array containing the specified range from the original array, truncated or padded with nulls to obtain the required length
     * @throws ArrayIndexOutOfBoundsException
     *             if <tt>from &lt; 0</tt> or <tt>from &gt; original.length()</tt>
     * @throws IllegalArgumentException
     *             if <tt>from &gt; to</tt>
     * @throws NullPointerException
     *             if <tt>original</tt> is null
     * @throws ArrayStoreException
     *             if an element copied from <tt>original</tt> is not of a runtime type that can be stored in an array of class <tt>newType</tt>.
     * @since 1.6
     */
    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOfRange(U[] original, int from, int to, Class<? extends T[]> newType) {
        int newLength = to - from;
        if (newLength < 0)
            throw new IllegalArgumentException(from + " > " + to);
        T[] copy = ((Object) newType == (Object) Object[].class)
                ? (T[]) new Object[newLength]
                : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, from, copy, 0,
                Math.min(original.length - from, newLength));
        return copy;
    }

    public static <T> T[] add(T[] one, T[] two) {
        int size = one.length + two.length;
        T[] copy = copyOf(one, size);
        for (int i = one.length; i < size; i++) {
            copy[i] = two[i - one.length];
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] copyOf(T[] original, int newLength) {
        return (T[]) copyOf(original, newLength, original.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
        T[] copy = ((Object) newType == (Object) Object[].class)
                ? (T[]) new Object[newLength]
                : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0,
                Math.min(original.length, newLength));
        return copy;
    }

    public static String toCommaSeparatedValues(Collection<?> list) {
        StringBuilder csv = new StringBuilder();
        if (list != null) {
            Object[] array = list.toArray(new Object[list.size()]);
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    csv.append(",");
                }
                if (array[i] != null) {
                    csv.append(array[i]);
                }
            }
        }
        return csv.toString();
    }
}
