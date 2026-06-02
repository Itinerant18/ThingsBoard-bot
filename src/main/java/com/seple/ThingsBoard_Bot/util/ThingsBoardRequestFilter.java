package com.seple.ThingsBoard_Bot.util;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class ThingsBoardRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String tbHost = httpRequest.getHeader("X-TB-Host");
            
            // Try to extract from X-TB-Token if host header is absent
            if (tbHost == null || tbHost.trim().isEmpty()) {
                String tbToken = httpRequest.getHeader("X-TB-Token");
                if (tbToken != null && !tbToken.trim().isEmpty()) {
                    tbHost = JwtParserUtil.extractHost(tbToken);
                }
            }

            if (tbHost != null && !tbHost.trim().isEmpty()) {
                tbHost = tbHost.trim();
                // Ensure no trailing slash
                if (tbHost.endsWith("/")) {
                    tbHost = tbHost.substring(0, tbHost.length() - 1);
                }
                log.debug("Setting request ThingsBoard host to: {}", tbHost);
                ThingsBoardRequestContext.setHost(tbHost);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            ThingsBoardRequestContext.clear();
        }
    }
}
