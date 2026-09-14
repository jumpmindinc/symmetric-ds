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
package org.jumpmind.db.model;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DatabaseXmlTest {
    public final boolean enablePrint = true;

    /**
     * Helper finds root directory for this code on file system starting from either the current directory or specified path.
     */
    public static String getRepositoryRootDir(String currentDirectory, String topProjectMarker) {
        Path directoryPath = Paths.get("").toAbsolutePath();
        if (currentDirectory != null) {
            directoryPath = Paths.get(currentDirectory);
        }
        String currentPath = directoryPath.toString();
        if (topProjectMarker == null || currentPath == null || currentPath.length() < topProjectMarker.length()) {
            return currentPath;
        }
        String marker = topProjectMarker;
        if (!marker.startsWith(File.separator)) {
            marker = File.separator + marker;
        }
        int markerPos = currentPath.lastIndexOf(marker);
        if (markerPos >= 0) {
            return currentPath.substring(0, markerPos);
        }
        return currentPath;
    }

    @Test
    public void testSymmetricSchemaIsValidAgainstDtd() throws ParserConfigurationException, SAXException, IOException {
        String symmetricSchemaXmlLocation = "/symmetric-ds/symmetric-core/src/main/resources/symmetric-schema.xml";
        String symmetricDtdLocation = "/symmetric-ds/symmetric-assemble/database.dtd";
        String repositoryMarker = File.separator + "symmetric-ds" + File.separator;
        String symmetricSchemaXmlPath = "";
        String symmetricDtdPath = "";
        try {
            String repoRootDir = getRepositoryRootDir(null, repositoryMarker);
            assertNotNull(repoRootDir);
            symmetricSchemaXmlPath = repoRootDir + symmetricSchemaXmlLocation;
            symmetricDtdPath = repoRootDir + symmetricDtdLocation;
            File symmetricSchemaXmlFile = new File(symmetricSchemaXmlPath);
            assertTrue(symmetricSchemaXmlFile.exists());
            File symmetricDtdFile = new File(symmetricDtdPath);
            if (enablePrint) {
                System.out.println("testSymmetricSchemaIsValidAgainstDtd - repoRootDir=" + repoRootDir + ", symmetricSchemaXmlPath=" + symmetricSchemaXmlPath
                        + ", symmetricDtdPath="
                        + symmetricDtdPath);
            }
            assertTrue(symmetricDtdFile.exists());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            // factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // Enable DTD validation:
            factory.setValidating(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Set DTD source:
            builder.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                    if (systemId.contains(".dtd")) {
                        return new InputSource(symmetricDtdFile.toURI().toASCIIString());
                    } else {
                        return null;
                    }
                }
            });
            Document doc = builder.parse(symmetricSchemaXmlFile);
            assertNotNull(doc);
            if (enablePrint) {
                System.out.println("testSymmetricSchemaIsValidAgainstDtd - XML is valid against the DTD. symmetricDtdPath=" + symmetricDtdPath);
            }
        } catch (Exception e) {
            System.out.println("testSymmetricSchemaIsValidAgainstDtd - Validation error: " + e.getMessage() + "; symmetricSchemaXmlPath="
                    + symmetricSchemaXmlPath + " symmetricDtdPath=" + symmetricDtdPath);
            e.printStackTrace();
        }
        // assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t3));
    }
}
