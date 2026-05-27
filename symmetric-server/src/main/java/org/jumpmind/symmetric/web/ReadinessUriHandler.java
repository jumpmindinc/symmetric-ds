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

public class ReadinessUriHandler implements IUriHandler{
	@Override
	public void handle(HttpServletRequest req, HttpServletResponse res)
			throws IOException, ServletException, FileUploadException {
		res.setContentType("application/json");
		IApplicationHealthTracker tracker = ApplicationHealthTracker.getTracker();
		if(tracker == null) {
			res.getWriter().write("{\"status\": \"READY\"}");
			return;
		}
		String response = prepareReadinessJsonRes(tracker.getEngineReadiness());
		if(response.endsWith("{\"status\": \"not ready\"}")) {
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
    
    private String prepareReadinessJsonRes(Map<String, Boolean> engineReadiness) {
    		StringBuilder response = new StringBuilder("{\"engine_details\": [");
    		
        boolean ready = ApplicationHealthTracker.getTracker().isAlive();
        for(Entry<String, Boolean> engine : engineReadiness.entrySet()) {
        		ready &= engine.getValue();
        		response.append("{\"engine_name\": \"");
        		response.append(engine.getKey());
        		response.append("\", \"status\": \"");
        		response.append(engine.getValue() ? "ready" : "not ready");
        		response.append("\"}");
        }
        response.append("],");
        response.append("{\"status\": \"").append(ready ? "ready" : "not ready").append("\"}");
        return response.toString();
    }
}
