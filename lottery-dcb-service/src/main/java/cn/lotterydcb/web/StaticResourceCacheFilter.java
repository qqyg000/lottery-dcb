package cn.lotterydcb.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StaticResourceCacheFilter extends OncePerRequestFilter {

    private static final String NO_CACHE = "no-store, no-cache, must-revalidate, max-age=0";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean frontendResource = isFrontendResource(request);
        if (frontendResource) {
            disableCache(response);
        }
        filterChain.doFilter(request, response);
        if (frontendResource && !response.isCommitted()) {
            disableCache(response);
        }
    }

    private boolean isFrontendResource(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/favicon.svg")
                || path.startsWith("/assets/");
    }

    private void disableCache(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
    }
}
