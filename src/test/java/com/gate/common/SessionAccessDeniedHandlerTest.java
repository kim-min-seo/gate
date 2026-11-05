package com.gate.common;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import static org.junit.jupiter.api.Assertions.*;

class SessionAccessDeniedHandlerTest {
    @Test void expiredSessionFormRedirectsWithoutExecutingAction() throws Exception {
        var request=new MockHttpServletRequest("POST","/reservations/cancel");var response=new MockHttpServletResponse();
        new SessionAccessDeniedHandler().handle(request,response,new MissingCsrfTokenException(null));
        assertEquals(302,response.getStatus());assertEquals("/login?expired",response.getRedirectedUrl());
    }
    @Test void loggedInInvalidCsrfStillReceivesForbidden() throws Exception {
        var request=new MockHttpServletRequest("POST","/reservations/cancel");request.getSession().setAttribute("userId",1L);
        var response=new MockHttpServletResponse();new SessionAccessDeniedHandler().handle(request,response,new MissingCsrfTokenException(null));
        assertEquals(403,response.getStatus());assertNull(response.getRedirectedUrl());
    }
    @Test void apiDoesNotReceiveHtmlLoginRedirect() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/reservations");var response=new MockHttpServletResponse();
        new SessionAccessDeniedHandler().handle(request,response,new MissingCsrfTokenException(null));
        assertEquals(403,response.getStatus());assertTrue(response.getContentType().startsWith("application/json"));
    }
}
