package com.gate.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SessionConfiguration {
    @Bean CookieSerializer cookieSerializer(Environment environment){
        var serializer=new DefaultCookieSerializer();
        serializer.setCookieName("GATE_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        // Local HTTP uses request security; deployment explicitly requires secure cookies.
        var secure=environment.getProperty("server.servlet.session.cookie.secure",Boolean.class);
        if(secure!=null)serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
