package com.gate.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.SessionRepository;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SessionIdleTimeoutTest {
    @Autowired SessionRepository sessions;
    @Test void newSessionUsesThirtyMinuteIdleTimeout(){
        var session=sessions.createSession();
        assertEquals(Duration.ofMinutes(30),session.getMaxInactiveInterval());
    }
    @Test void idleForTwentyNineMinutesKeepsLogin(){
        var session=sessions.createSession();session.setAttribute("userId",123L);
        session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(29)));
        sessions.save(session);
        try{assertEquals(Long.valueOf(123L),(Long)sessions.findById(session.getId()).getAttribute("userId"));}
        finally{sessions.deleteById(session.getId());}
    }
    @Test void idleForThirtyOneMinutesExpiresLogin(){
        var session=sessions.createSession();session.setAttribute("userId",123L);
        session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(31)));
        sessions.save(session);
        try{assertNull(sessions.findById(session.getId()));}
        finally{sessions.deleteById(session.getId());}
    }
    @Test void newAccessMovesTheIdleDeadline(){
        var session=sessions.createSession();session.setAttribute("userId",123L);
        session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(29)));sessions.save(session);
        try{
            var loaded=sessions.findById(session.getId());assertNotNull(loaded);
            loaded.setLastAccessedTime(Instant.now());sessions.save(loaded);
            assertTrue(sessions.findById(session.getId()).getLastAccessedTime().isAfter(Instant.now().minusSeconds(5)));
        }finally{sessions.deleteById(session.getId());}
    }
}
