package com.nguyenvu.lopet.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LogFilter implements Filter {
    private static final Log log = LogFactory.getLog(LogFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServlet = (HttpServletRequest) request;
        String requestUrl = httpServlet.getRequestURI();
        String devide = httpServlet.getHeader("User-Agent");
        log.info("RequestURL: " + requestUrl + " | " + "From: " + devide);
        chain.doFilter(request, response);
    }
}
