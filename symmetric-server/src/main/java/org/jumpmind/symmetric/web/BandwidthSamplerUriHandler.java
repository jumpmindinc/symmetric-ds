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
package org.jumpmind.symmetric.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.BandwidthTestResults;
import org.jumpmind.util.AppUtils;

import com.google.gson.Gson;

/**
 * This uri handler streams the number of bytes requested by the sampleSize parameter.
 * 
 * @see IBandwidthService
 */
public class BandwidthSamplerUriHandler extends AbstractUriHandler {
    protected long defaultTestSlowBandwidthDelay = 0;
    protected Gson gson = new Gson();

    public BandwidthSamplerUriHandler(IParameterService parameterService, IInterceptor[] interceptors) {
        super("/bandwidth/*", parameterService, interceptors);
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        String direction = Objects.toString(req.getHeader(WebConstants.HEADER_DIRECTION), req.getParameter(WebConstants.DIRECTION));
        if (direction != null && direction.equals(WebConstants.URL_PULL)) {
            handlePull(req, res);
        } else if (direction != null && direction.equals(WebConstants.URL_PUSH)) {
            handlePush(req, res);
        } else if (!"HEAD".equals(req.getMethod())) {
            throw new IOException("Unknown direction: " + direction);
        }
    }

    private void handlePull(HttpServletRequest req, HttpServletResponse res) throws IOException {
        long testSlowBandwidthDelay = parameterService != null ? parameterService
                .getLong("test.slow.bandwidth.delay") : defaultTestSlowBandwidthDelay;
        String sampleSizeString = Objects.toString(req.getHeader(WebConstants.HEADER_SAMPLE_SIZE), req.getParameter(WebConstants.SAMPLE_SIZE));
        long sampleSize = 1000;
        try {
            sampleSize = Long.parseLong(sampleSizeString);
        } catch (Exception ex) {
            log.warn("Unable to parse sample size of {}", sampleSizeString);
        }
        ServletOutputStream os = res.getOutputStream();
        for (int i = 0; i < sampleSize; i++) {
            os.write(1);
            if (testSlowBandwidthDelay > 0) {
                AppUtils.sleep(testSlowBandwidthDelay);
            }
        }
    }

    private void handlePush(HttpServletRequest req, HttpServletResponse res) throws IOException {
        BandwidthTestResults bwtr = new BandwidthTestResults();
        bwtr.start();
        byte[] b = new byte[1024];
        int count = 0;
        InputStream inputStream = createInputStream(req);
        OutputStream outputStream = res.getOutputStream();
        while ((count = inputStream.read(b, 0, b.length)) != -1) {
            bwtr.transmitted(count);
            ;
        }
        bwtr.stop();
        log.debug(gson.toJson(bwtr));
        outputStream.write(gson.toJson(bwtr).getBytes(Charset.defaultCharset()));
    }

    protected InputStream createInputStream(HttpServletRequest req) throws IOException {
        InputStream is = null;
        String contentType = req.getHeader("Content-Type");
        boolean useCompression = contentType != null && contentType.equalsIgnoreCase("gzip");
        is = req.getInputStream();
        if (useCompression) {
            is = new GZIPInputStream(is);
        }
        return is;
    }

    public void setDefaultTestSlowBandwidthDelay(long defaultTestSlowBandwidthDelay) {
        this.defaultTestSlowBandwidthDelay = defaultTestSlowBandwidthDelay;
    }
}
