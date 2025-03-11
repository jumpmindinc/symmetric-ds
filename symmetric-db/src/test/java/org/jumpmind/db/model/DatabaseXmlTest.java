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
package org.jumpmind.db.model;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DatabaseXmlTest {
    @Test
    public void testSymmetricSchemaIsValidAgainstDtd() throws ParserConfigurationException, SAXException, IOException {
        String symmetricSchemaXmlLocation = "/symmetric-ds/symmetric-core/src/main/resources/symmetric-schema.xml";
        String symmetricDtdLocation = "/symmetric-ds/symmetric-assemble/database.dtd";
        String repositoryMarker = "\\symmetric-ds\\";
        String symmetricSchemaXmlPath = "";
        String symmetricDtdPath = "";
        try {
            String currentDirectory = Paths.get("").toAbsolutePath().toString();
            String[] paths = currentDirectory.split(repositoryMarker);
            // System.out.println("testSymmetricSchemaIsValidAgainstDtd - Started. Directory=" + currentDirectory + "; Project=" +projectDirectoryMarker);
            assertEquals(2, paths.length);
            symmetricSchemaXmlPath = paths[0] + symmetricSchemaXmlLocation;
            symmetricDtdPath = paths[0] + symmetricDtdLocation;
            File symmetricSchemaXmlFile = new File(symmetricSchemaXmlPath);
            File symmetricDtdFile = new File(symmetricDtdPath);
            // System.out.println("testSymmetricSchemaIsValidAgainstDtd - absolutePath=" + absolutePath);
            assertTrue(symmetricSchemaXmlFile.exists());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
            // System.out.println("testSymmetricSchemaIsValidAgainstDtd - XML is valid against the DTD. Path=" + absolutePath);
        } catch (Exception e) {
            System.out.println("testSymmetricSchemaIsValidAgainstDtd - Validation error: " + e.getMessage() + "; Path=" + symmetricSchemaXmlPath);
            e.printStackTrace();
        }
        // assertTrue(list.toString(), list.indexOf(t4) < list.indexOf(t3));
    }
}
