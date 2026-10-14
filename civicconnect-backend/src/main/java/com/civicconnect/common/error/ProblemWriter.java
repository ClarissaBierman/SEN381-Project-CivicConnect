package com.civicconnect.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Writes the PED error body from servlet filters, where @RestControllerAdvice does not apply. */
@Component
public class ProblemWriter {

    private final JsonMapper json = JsonMapper.builder().build();

    public void write(HttpServletResponse response, ErrorCode code, String detail,
                      HttpServletRequest request) throws IOException {
        int status = code.status().value();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiError.of(code, status, detail, request.getRequestURI(), null));
    }
}
