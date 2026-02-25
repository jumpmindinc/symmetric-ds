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
package org.jumpmind.db.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ReleaseNotesGeneratorTest {
	
    @Test
    public void testCompareMajorMinor_EarlierVersion() throws Exception {
        assertTrue(invokeCompareMajorMinor("3.16", "3.17") < 0);
    }

    @Test
    public void testCompareMajorMinor_LaterVersion() throws Exception {
        assertTrue(invokeCompareMajorMinor("3.17", "3.16") > 0);
    }

    @Test
    public void testCompareMajorMinor_EqualVersion() throws Exception {
        assertEquals(0, invokeCompareMajorMinor("3.17", "3.17"));
    }

    @Test
    public void testCompareMajorMinor_DifferentMajor() throws Exception {
        assertTrue(invokeCompareMajorMinor("2.16", "3.16") < 0);
        assertTrue(invokeCompareMajorMinor("4.0", "3.99") > 0);
    }

    @Test
    public void testIsPatchZero_ZeroPatch() throws Exception {
        assertTrue(invokeIsPatchZero("3.17.0"));
    }

    @Test
    public void testIsPatchZero_NonZeroPatch() throws Exception {
        assertFalse(invokeIsPatchZero("3.17.2"));
        assertFalse(invokeIsPatchZero("3.16.11"));
    }

    @Test
    public void testIsPatchZero_NullVersion() throws Exception {
        assertFalse(invokeIsPatchZero(null));
    }

    @Test
    public void testIsPatchZero_TwoPartVersion() throws Exception {
        assertFalse(invokeIsPatchZero("3.17"));
    }

    @Test
    public void testForwardPortExcluded() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-1001", "Fix data loader issue", "Bug", "High",
                new String[] { "3.16.10", "3.17.0" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(0, issues.size());
    }

    @Test
    public void testForwardPortExcluded_MultipleEarlierVersions() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-1002", "Fix routing issue", "Bug", "Medium",
                new String[] { "3.15.5", "3.16.10", "3.17.0" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(0, issues.size());
    }

    @Test
    public void testBackportIncluded() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-2001", "Fix security vulnerability", "Bug", "High",
                new String[] { "3.17.2", "3.16.11" },
                new String[] { "security" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("SYM-2001", issues.get(0).getId());
        assertEquals("3.17.2", issues.get(0).getVersion());
    }

    @Test
    public void testSingleVersionIncluded() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-3001", "Add new feature", "New Feature", "Medium",
                new String[] { "3.17.0" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("SYM-3001", issues.get(0).getId());
        assertEquals("3.17.0", issues.get(0).getVersion());
    }

    @Test
    public void testSameMinorMultipleVersionsIncluded() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-3002", "Improve performance", "Improvement", "Low",
                new String[] { "3.17.0", "3.17.1" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
    }

    @Test
    public void testPatchReleaseIncluded() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-3003", "Fix null pointer", "Bug", "High",
                new String[] { "3.17.3" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("3.17.3", issues.get(0).getVersion());
    }

    @Test
    public void testMixedIssues_OnlyGenuineIncluded() throws Exception {
        List<JsonObject> issueJsons = new ArrayList<>();
        // Forward-ported: should be excluded
        issueJsons.add(buildIssueJson("SYM-4001", "Ported fix", "Bug", "Medium",
                new String[] { "3.16.10", "3.17.0" }, new String[] { "core" }));
        // Genuine new feature: should be included
        issueJsons.add(buildIssueJson("SYM-4002", "New feature", "New Feature", "High",
                new String[] { "3.17.0" }, new String[] { "core" }));
        // Backport: should be included
        issueJsons.add(buildIssueJson("SYM-4003", "Security fix", "Bug", "High",
                new String[] { "3.17.1", "3.16.11" }, new String[] { "security" }));
        // Another forward-port: should be excluded
        issueJsons.add(buildIssueJson("SYM-4004", "Another ported fix", "Improvement", "Low",
                new String[] { "3.16.9", "3.17.0" }, new String[] { "core" }));
        JsonObject response = buildJiraResponseFromIssues(issueJsons);
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(2, issues.size());
        assertEquals("SYM-4002", issues.get(0).getId());
        assertEquals("SYM-4003", issues.get(1).getId());
    }

    @Test
    public void testParseIssue_ProComponent() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-5001", "Pro feature", "New Feature", "Medium",
                new String[] { "3.17.0" },
                new String[] { "pro" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("symmetric-pro", issues.get(0).getProject());
    }

    @Test
    public void testParseIssue_OsProject() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-5002", "OS bug fix", "Bug", "High",
                new String[] { "3.17.1" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("symmetric-ds", issues.get(0).getProject());
    }

    @Test
    public void testParseIssue_SecurityTag() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-5003", "Security patch", "Bug", "High",
                new String[] { "3.17.1" },
                new String[] { "security" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("security", issues.get(0).getTag());
    }

    @Test
    public void testParseIssue_PerformanceTag() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-5004", "Perf improvement", "Improvement", "Medium",
                new String[] { "3.17.0" },
                new String[] { "performance" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("performance", issues.get(0).getTag());
    }

    @Test
    public void testParseIssue_ProWithSecurityTag() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-5005", "Pro security fix", "Bug", "High",
                new String[] { "3.17.2" },
                new String[] { "pro", "security" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        assertEquals("symmetric-pro", issues.get(0).getProject());
        assertEquals("security", issues.get(0).getTag());
    }

    @Test
    public void testParseIssue_FieldValues() throws Exception {
        JsonObject response = buildJiraResponse(
                "SYM-6001", "Test summary text", "Improvement", "Low",
                new String[] { "3.17.0" },
                new String[] { "core" });
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(1, issues.size());
        Issue issue = issues.get(0);
        assertEquals("SYM-6001", issue.getId());
        assertEquals("Test summary text", issue.getSummary());
        assertEquals("Improvement", issue.getCategory());
        assertEquals("Low", issue.getPriority());
        assertEquals("3.17.0", issue.getVersion());
    }

    @Test
    public void testParseIssues_EmptyResponse() throws Exception {
        JsonObject response = new Gson().fromJson("{\"issues\":[]}", JsonObject.class);
        List<Issue> issues = invokeParseIssues("3.17", response);
        assertEquals(0, issues.size());
    }

    @Test
    public void testWriteIssuesSection_CategorizesByType() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-7001", "3.17.0", "New Feature", "Medium", "New feature", "symmetric-ds", null));
        issues.add(buildIssue("SYM-7002", "3.17.0", "Improvement", "Low", "An improvement", "symmetric-ds", null));
        issues.add(buildIssue("SYM-7003", "3.17.0", "Bug", "High", "A bug fix", "symmetric-ds", null));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeIssuesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("=== New Features"));
        assertTrue(output.contains("=== Improvements"));
        assertTrue(output.contains("=== Bug Fixes"));
        assertTrue(output.contains("SYM-7001"));
        assertTrue(output.contains("SYM-7002"));
        assertTrue(output.contains("SYM-7003"));
    }

    @Test
    public void testWriteIssuesSection_ProIssuesWrappedInTokens() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-7004", "3.17.0", "Bug", "High", "Pro bug fix", "symmetric-pro", null));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeIssuesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("ifdef::pro[]"));
        assertTrue(output.contains("endif::pro[]"));
        assertTrue(output.contains("SYM-7004"));
    }

    @Test
    public void testWriteIssuesSection_MultipleVersionsSorted() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-7005", "3.17.1", "Bug", "High", "Patch fix", "symmetric-ds", null));
        issues.add(buildIssue("SYM-7006", "3.17.0", "Bug", "Medium", "Initial fix", "symmetric-ds", null));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeIssuesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        int idx3170 = output.indexOf("*3.17.0*");
        int idx3171 = output.indexOf("*3.17.1*");
        assertTrue(idx3170 > -1);
        assertTrue(idx3171 > -1);
        assertTrue(idx3170 < idx3171);
    }

    @Test
    public void testWriteFixesSection_SecurityFixes() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-8001", "3.17.1", "Bug", "High", "XSS vulnerability", "symmetric-ds", "security"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeFixesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("=== Security Fixes"));
        assertTrue(output.contains("SYM-8001"));
        assertTrue(output.contains("XSS vulnerability"));
        assertTrue(output.contains("High"));
    }

    @Test
    public void testWriteFixesSection_PerformanceFixes() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-8002", "3.17.0", "Improvement", "Medium", "Cache optimization", "symmetric-ds", "performance"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeFixesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("=== Performance Fixes"));
        assertTrue(output.contains("SYM-8002"));
        assertTrue(output.contains("Cache optimization"));
    }

    @Test
    public void testWriteFixesSection_ProSecurityFixWrappedInTokens() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-8003", "3.17.1", "Bug", "High", "Pro security fix", "symmetric-pro", "security"));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeFixesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("ifdef::pro[]"));
        assertTrue(output.contains("SYM-8003"));
    }

    @Test
    public void testWriteFixesSection_NoTaggedIssues() {
        List<Issue> issues = new ArrayList<>();
        issues.add(buildIssue("SYM-8004", "3.17.0", "Bug", "Low", "Normal bug", "symmetric-ds", null));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeFixesSection(pw, issues);
        pw.flush();
        String output = sw.toString();
        assertFalse(output.contains("=== Security Fixes"));
        assertFalse(output.contains("=== Performance Fixes"));
        assertFalse(output.contains("SYM-8004"));
    }

    @Test
    public void testWriteFinalNotes_ContainsSections() {
        String[] sections = { "fixes.ad", "whats-new.ad", "issues.ad", "tables.ad", "parameters.ad" };
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ReleaseNotesGenerator.writeFinalNotes(pw, sections);
        pw.flush();
        String output = sw.toString();
        assertTrue(output.contains("= Release Notes"));
        assertTrue(output.contains("== Overview"));
        for (String section : sections) {
            assertTrue(output.contains(section));
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Issue> invokeParseIssues(String majorMinorVersion, JsonObject root) throws Exception {
        Method method = ReleaseNotesGenerator.class.getDeclaredMethod("parseIssues", String.class, JsonObject.class);
        method.setAccessible(true);
        return (List<Issue>) method.invoke(null, majorMinorVersion, root);
    }

    private int invokeCompareMajorMinor(String v1, String v2) throws Exception {
        Method method = ReleaseNotesGenerator.class.getDeclaredMethod("compareMajorMinor", String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, v1, v2);
    }

    private boolean invokeIsPatchZero(String version) throws Exception {
        Method method = ReleaseNotesGenerator.class.getDeclaredMethod("isPatchZero", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, version);
    }

    private JsonObject buildJiraResponse(String key, String summary, String issueType,
            String priority, String[] fixVersions, String[] components) {
        List<JsonObject> issues = new ArrayList<>();
        issues.add(buildIssueJson(key, summary, issueType, priority, fixVersions, components));
        return buildJiraResponseFromIssues(issues);
    }

    private JsonObject buildIssueJson(String key, String summary, String issueType,
            String priority, String[] fixVersions, String[] components) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"key\":\"").append(key).append("\",");
        sb.append("\"fields\":{");
        sb.append("\"summary\":\"").append(summary).append("\",");
        sb.append("\"issuetype\":{\"name\":\"").append(issueType).append("\"},");
        sb.append("\"priority\":{\"name\":\"").append(priority).append("\"},");
        sb.append("\"fixVersions\":[");
        for (int i = 0; i < fixVersions.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append("{\"name\":\"").append(fixVersions[i]).append("\"}");
        }
        sb.append("],");
        sb.append("\"components\":[");
        for (int i = 0; i < components.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append("{\"name\":\"").append(components[i]).append("\"}");
        }
        sb.append("]");
        sb.append("}}");
        return new Gson().fromJson(sb.toString(), JsonObject.class);
    }

    private JsonObject buildJiraResponseFromIssues(List<JsonObject> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"issues\":[");
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(issues.get(i).toString());
        }
        sb.append("]}");
        return new Gson().fromJson(sb.toString(), JsonObject.class);
    }

    private Issue buildIssue(String id, String version, String category, String priority,
            String summary, String project, String tag) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setVersion(version);
        issue.setCategory(category);
        issue.setPriority(priority);
        issue.setSummary(summary);
        issue.setProject(project);
        issue.setTag(tag);
        return issue;
    }
}
