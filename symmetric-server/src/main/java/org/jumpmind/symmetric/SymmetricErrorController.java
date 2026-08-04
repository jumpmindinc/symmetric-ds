package org.jumpmind.symmetric;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class SymmetricErrorController implements ErrorController {
    private static final String PATH = "/error";

    @RequestMapping(
            value = PATH,
            method = {
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.HEAD })
    public String error(HttpServletRequest request, HttpServletResponse response) {
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        return response.getStatus() + (errorMessage != null ? " " + errorMessage.toString() : "");
    }
}
