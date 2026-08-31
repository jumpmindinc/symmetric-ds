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
package org.jumpmind.symmetric.io.data;

import static org.junit.Assert.*;

import org.jumpmind.symmetric.csv.CsvWriter;
import org.junit.jupiter.api.Test;

public class CsvUtilsTest {
    @Test
    public void testLastElementIsNull() {
        String[] tokens = CsvUtils.tokenizeCsvData(
                "\"01493931\",\"0\",\"01493931\",,\"UktNQzIxMAD/////AAAABXV1aWQAAAAAEG3jUZmt5UvpiUdFsbLEkJT/////AAAAA2l2AAAAABClDAHak0h0ENSr3PGH8qIU/////wAAAAVjc3VtAAAAACBjkvrLppvDkY1EbaURqm2kpvmcg/j9eUxztrCe4JHXpH0suPSRvgP6LtpJMaH1HZc=\",\"Trevor Lewis\",\"Lewis\",\"Trevorr\",,\"02\",,\"1\",\"16\",,\"0\",,\"0\",\"30683\",\"0\",\"2010-05-18 15:43:21\",\"0\",\"1987-09-24 00:00:00\",\"0\",,\"PS\",\"2009-11-14 21:49:35\",\"2009-11-14 21:49:35\",\"0023\",,\"1\",");
        String expectedNull = tokens[tokens.length - 1];
        assertNull("Expected null.  Instead received: " + expectedNull, expectedNull);
    }

    @Test
    public void testEscapeDoubledSingleQuote() {
        assertEquals("'L\\'' Hospitalet',,'277000043'\n", CsvUtils.escapeCsvData(new String[] { "L\\' Hospitalet", null, "277000043" }, '\n', '\'',
                CsvWriter.ESCAPE_MODE_DOUBLED));
    }

    @Test
    public void testLineFeedsInCsv() {
        String line = "\"test\",\"line\nfeed\"";
        String[] tokens = CsvUtils.tokenizeCsvData(line);
        assertEquals("test", tokens[0]);
        assertEquals("line\nfeed", tokens[1]);
    }

    @Test
    public void testEscapingLineFeedsInCsv() {
        String[] tokens = new String[] { "test", "line\nfeed" };
        String line = CsvUtils.escapeCsvData(tokens);
        String[] newTokens = CsvUtils.tokenizeCsvData(line);
        assertEquals(tokens[0], newTokens[0]);
        assertEquals(tokens[1], newTokens[1]);
    }
}