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
package org.jumpmind.symmetric;

import java.util.Arrays;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Helper class that wraps resource bundles.
 */
public class Message {
    private static Locale locale = null;
    private static String bundleName = "symmetric-messages";
    private static ResourceBundle bundle = null;
    private static String MESSAGE_KEY = "MessageKey: ";
    static {
        setLocale(Locale.getDefault());
    }

    /*
     * @see org.jumpmind.symmetric.common.logging.ILog#getMessage(java.lang.String)
     */
    public static String get(String key) {
        return get(key, (Object) null);
    }

    /*
     * @see org.jumpmind.symmetric.common.logging.ILog#getMessage(java.lang.String, java.lang.Object)
     */
    public static String get(String key, Object... args) {
        if (bundle != null) {
            try {
                if (args != null) {
                    return String.format(locale, bundle.getString(key), args);
                } else {
                    return String.format(locale, bundle.getString(key));
                }
            } catch (RuntimeException e) {
                return MESSAGE_KEY + key + ((args != null) ? Arrays.toString(args) : "");
            }
        } else {
            return MESSAGE_KEY + key + ((args != null) ? Arrays.toString(args) : "");
        }
    }

    public static boolean containsKey(String key) {
        try {
            return bundle != null && bundle.getString(key) != null;
        } catch (MissingResourceException ex) {
            return false;
        }
    }

    /*
     * 
     * 
     * @see org.jumpmind.symmetric.common.logging.ILog#getLocale()
     */
    public static Locale getLocale() {
        return locale;
    }

    /*
     * @see org.jumpmind.symmetric.common.logging.ILog#setLocale(java.util.Locale)
     */
    public static void setLocale(Locale locale) {
        Message.locale = locale;
        try {
            Message.bundle = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException e) {
            Message.bundle = null;
        }
    }

    /*
     * @see org.jumpmind.symmetric.common.logging.ILog#getBundleName()
     */
    public static String getBundleName() {
        return bundleName;
    }

    /*
     * @see org.jumpmind.symmetric.common.logging.ILog#setBundleName(java.lang.String )
     */
    public static void setBundleName(String bundleName) {
        Message.bundleName = bundleName;
        Message.bundle = ResourceBundle.getBundle(bundleName, locale);
    }
}