package com.derm.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIds extends OncePerRequestFilter {
    public static String id(HttpServletRequest request) { return (String)request.getAttribute("requestId"); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        // Always generate a server identifier; untrusted headers may contain patient information.
        String id=UUID.randomUUID().toString(); request.setAttribute("requestId",id); response.setHeader("X-Request-Id",id);
        response.setHeader("X-Content-Type-Options","nosniff");
        chain.doFilter(request,response);
    }
}
