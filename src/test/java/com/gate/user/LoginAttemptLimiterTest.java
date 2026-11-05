package com.gate.user;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoginAttemptLimiterTest {
    @Autowired LoginAttemptLimiter limiter;
    @Autowired StringRedisTemplate redis;
    @Test void sixthAttemptIsBlockedAndSuccessfulLoginResetsAccountCounter(){
        String ip="test-"+UUID.randomUUID();String email="limit@example.invalid";
        try{
            for(int i=0;i<5;i++)assertTrue(limiter.allow(email,ip));
            assertFalse(limiter.allow(email,ip));
            long ttl=redis.getExpire(limiter.accountKey(email,ip));assertTrue(ttl>0&&ttl<=600);
            limiter.success(email,ip);assertTrue(limiter.allow(email,ip));
        }finally{redis.delete(List.of(limiter.ipKey(ip),limiter.accountKey(email,ip)));}
    }
    @Test void concurrentAttemptsCannotBypassAccountLimit() throws Exception {
        String ip="test-"+UUID.randomUUID();String email="concurrent@example.invalid";
        try(var pool=Executors.newFixedThreadPool(12)){
            var start=new CountDownLatch(1);var calls=new ArrayList<Future<Boolean>>();
            for(int i=0;i<12;i++)calls.add(pool.submit(()->{start.await();return limiter.allow(email,ip);}));
            start.countDown();int accepted=0;for(var call:calls)if(call.get(10,TimeUnit.SECONDS))accepted++;
            assertEquals(5,accepted);
        }finally{redis.delete(List.of(limiter.ipKey(ip),limiter.accountKey(email,ip)));}
    }
    @Test void changingAccountsCannotBypassAddressLimit(){
        String ip="test-"+UUID.randomUUID();var keys=new ArrayList<String>();keys.add(limiter.ipKey(ip));
        try{
            for(int i=0;i<31;i++){
                String email="user-"+i+"@example.invalid";keys.add(limiter.accountKey(email,ip));
                assertEquals(i<30,limiter.allow(email,ip));
            }
        }finally{redis.delete(keys);}
    }
}
