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
package org.jumpmind.vaadin.ui.common;

import java.util.ArrayList;
import java.util.List;

public class Colors {
    final static String[] COLORS = { "#000000", "#0C090A", "#2C3539", "#2B1B17", "#34282C",
            "#25383C", "#3B3131", "#413839", "#3D3C3A", "#463E3F", "#4C4646", "#504A4B", "#565051",
            "#5C5858", "#625D5D", "#666362", "#6D6968", "#726E6D", "#736F6E", "#837E7C", "#848482",
            "#B6B6B4", "#D1D0CE", "#E5E4E2", "#BCC6CC", "#98AFC7", "#6D7B8D", "#657383", "#616D7E",
            "#646D7E", "#566D7E", "#737CA1", "#4863A0", "#2B547E", "#2B3856", "#151B54", "#000080",
            "#342D7E", "#15317E", "#151B8D", "#0000A0", "#0020C2", "#0041C2", "#2554C7", "#1569C7",
            "#2B60DE", "#1F45FC", "#6960EC", "#736AFF", "#357EC7", "#368BC1", "#488AC7", "#3090C7",
            "#659EC7", "#87AFC7", "#95B9C7", "#728FCE", "#2B65EC", "#306EFF", "#157DEC", "#1589FF",
            "#6495ED", "#6698FF", "#38ACEC", "#56A5EC", "#5CB3FF", "#3BB9FF", "#79BAEC", "#82CAFA",
            "#82CAFF", "#A0CFEC", "#B7CEEC", "#B4CFEC", "#C2DFFF", "#C6DEFF", "#AFDCEC", "#ADDFFF",
            "#BDEDFF", "#CFECEC", "#E0FFFF", "#EBF4FA", "#F0F8FF", "#F0FFFF", "#CCFFFF", "#93FFE8",
            "#9AFEFF", "#7FFFD4", "#00FFFF", "#7DFDFE", "#57FEFF", "#8EEBEC", "#50EBEC", "#4EE2EC",
            "#81D8D0", "#92C7C7", "#77BFC7", "#78C7C7", "#48CCCD", "#43C6DB", "#46C7C7", "#43BFC7",
            "#3EA99F", "#3B9C9C", "#438D80", "#348781", "#307D7E", "#5E7D7E", "#4C787E", "#008080",
            "#4E8975", "#78866B", "#848b79", "#617C58", "#728C00", "#667C26", "#254117", "#306754",
            "#347235", "#437C17", "#387C44", "#347C2C", "#347C17", "#348017", "#4E9258", "#6AA121",
            "#4AA02C", "#41A317", "#3EA055", "#6CBB3C", "#6CC417", "#4CC417", "#52D017", "#4CC552",
            "#54C571", "#99C68E", "#89C35C", "#85BB65", "#8BB381", "#9CB071", "#B2C248", "#9DC209",
            "#A1C935", "#7FE817", "#59E817", "#57E964", "#64E986", "#5EFB6E", "#00FF00", "#5FFB17",
            "#87F717", "#8AFB17", "#6AFB92", "#98FF98", "#B5EAAA", "#C3FDB8", "#CCFB5D", "#B1FB17",
            "#BCE954", "#EDDA74", "#EDE275", "#FFE87C", "#FFFF00", "#FFF380", "#FFFFC2", "#FFFFCC",
            "#FFF8C6", "#FFF8DC", "#F5F5DC", "#FBF6D9", "#FAEBD7", "#F7E7CE", "#FFEBCD", "#F3E5AB",
            "#ECE5B6", "#FFE5B4", "#FFDB58", "#FFD801", "#FDD017", "#EAC117", "#F2BB66", "#FBB917",
            "#FBB117", "#FFA62F", "#E9AB17", "#E2A76F", "#DEB887", "#FFCBA4", "#C9BE62", "#E8A317",
            "#EE9A4D", "#C8B560", "#D4A017", "#C2B280", "#C7A317", "#C68E17", "#B5A642", "#ADA96E",
            "#C19A6B", "#CD7F32", "#C88141", "#C58917", "#AF9B60", "#AF7817", "#B87333", "#966F33",
            "#806517", "#827839", "#827B60", "#786D5F", "#493D26", "#483C32", "#6F4E37", "#835C3B",
            "#7F5217", "#7F462C", "#C47451", "#C36241", "#C35817", "#C85A17", "#CC6600", "#E56717",
            "#E66C2C", "#F87217", "#F87431", "#E67451", "#FF8040", "#F88017", "#FF7F50", "#F88158",
            "#F9966B", "#E78A61", "#E18B6B", "#E77471", "#F75D59", "#E55451", "#E55B3C", "#FF0000",
            "#FF2400", "#F62217", "#F70D1A", "#F62817", "#E42217", "#E41B17", "#DC381F", "#C34A2C",
            "#C24641", "#C04000", "#C11B17", "#9F000F", "#990012", "#8C001A", "#954535", "#7E3517",
            "#8A4117", "#7E3817", "#800517", "#810541", "#7D0541", "#7E354D", "#7D0552", "#7F4E52",
            "#7F5A58", "#7F525D", "#B38481", "#C5908E", "#C48189", "#C48793", "#E8ADAA", "#EDC9AF",
            "#FDD7E4", "#FCDFFF", "#FFDFDD", "#FBBBB9", "#FAAFBE", "#FAAFBA", "#F9A7B0", "#E7A1B0",
            "#E799A3", "#E38AAE", "#F778A1", "#E56E94", "#F660AB", "#FC6C85", "#F6358A", "#F52887",
            "#E45E9D", "#E4287C", "#F535AA", "#FF00FF", "#E3319D", "#F433FF", "#D16587", "#C25A7C",
            "#CA226B", "#C12869", "#C12267", "#C25283", "#C12283", "#B93B8F", "#7E587E", "#571B7E",
            "#583759", "#4B0082", "#461B7E", "#4E387E", "#614051", "#5E5A80", "#6A287E", "#7D1B7E",
            "#A74AC7", "#B048B5", "#6C2DC7", "#842DCE", "#8D38C9", "#7A5DC7", "#7F38EC", "#8E35EF",
            "#893BFF", "#8467D7", "#A23BEC", "#B041FF", "#C45AEC", "#9172EC", "#9E7BFF", "#D462FF",
            "#E238EC", "#C38EC7", "#C8A2C8", "#E6A9EC", "#E0B0FF", "#C6AEC7", "#F9B7FF", "#D2B9D3",
            "#E9CFEC", "#EBDDE2", "#E3E4FA", "#FDEEF4", "#FFF5EE", "#FEFCFF" };

    public static String[] colors() {
        List<String> allColors = new ArrayList<String>();
        for (String color : COLORS) {
            allColors.add(color);
        }
        List<String> colors = new ArrayList<String>();
        int increment = 26;
        int startIndex = 100;
        while (allColors.size() > 0) {
            for (int i = startIndex; i < allColors.size(); i = i + increment) {
                colors.add(allColors.remove(i));
            }
            if (allColors.size() < increment) {
                increment = 1;
            }
            startIndex = 0;
        }
        return colors.toArray(new String[colors.size()]);
    }
}
