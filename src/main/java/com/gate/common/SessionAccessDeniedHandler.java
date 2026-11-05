package com.gate.common;

import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class SessionAccessDeniedHandler implements AccessDeniedHandler {
    @Override public void handle(HttpServletRequest request,HttpServletResponse response,AccessDeniedException failure) throws IOException,ServletException{
        var session=request.getSession(false);
        if(failure instanceof CsrfException && !request.getRequestURI().startsWith("/api/") && (session==null||session.getAttribute("userId")==null)){
            response.sendRedirect("/login?expired");return;
        }
        response.setStatus(403);
        response.setHeader("Cache-Control","no-store");
        if(request.getRequestURI().startsWith("/api/")){
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"요청을 확인할 수 없습니다. 로그인 상태를 확인해 주세요.\"}");
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><html lang='ko'><meta charset='UTF-8'><title>요청 확인 | Gate</title><body><h1>요청을 확인할 수 없습니다.</h1><p>오래된 화면이거나 요청 정보가 변경되었습니다. 화면을 다시 열어 진행해 주세요.</p><a href='/slots'>예약 화면으로 돌아가기</a></body></html>");
        }
    }
}
