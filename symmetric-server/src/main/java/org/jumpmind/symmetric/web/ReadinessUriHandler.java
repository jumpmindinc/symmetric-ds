package org.jumpmind.symmetric.web;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.fileupload2.core.FileUploadException;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReadinessUriHandler implements IUriHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException, FileUploadException {
        res.setContentType("application/json");
        IApplicationHealthTracker tracker = ApplicationHealthTracker.getTracker();
        if (tracker == null) {
            res.getWriter().write("{\"status\": \"READY\"}");
            return;
        }
        boolean alive = tracker.isAlive();
        String response = prepareReadinessJsonRes(tracker.getEngineReadiness(), alive);
        if (response.endsWith("{\"status\": \"NOT READY\"}")) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        res.getWriter().write(response);
    }

    @Override
    public String getUriPattern() {
        return "/readiness";
    }

    @Override
    public List<IInterceptor> getInterceptors() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private String prepareReadinessJsonRes(Map<String, Boolean> engineReadiness, boolean alive) {
        StringBuilder response = new StringBuilder("{\"engine_details\": [");
        boolean ready = alive;
        for (Entry<String, Boolean> engine : engineReadiness.entrySet()) {
            ready &= engine.getValue();
            response.append("{\"engine_name\": \"");
            response.append(engine.getKey());
            response.append("\", \"status\": \"");
            response.append(engine.getValue() ? "READY" : "NOT READY");
            response.append("\"}");
        }
        response.append("],");
        response.append("{\"status\": \"").append(ready ? "READY" : "NOT READY").append("\"}");
        return response.toString();
    }
}
