package com.gate.user;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
public class LoginAttemptLimiter {
    private final StringRedisTemplate redis;
    private static final Duration WINDOW=Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> INCREMENT=new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);
    public LoginAttemptLimiter(StringRedisTemplate redis){this.redis=redis;}
    String ipKey(String ip){return "gate:auth:ip:"+hash(ip);}
    String accountKey(String email,String ip){return "gate:auth:account-ip:"+hash(email.toLowerCase(Locale.ROOT)+"|"+ip);}
    public boolean allow(String email,String ip){
        Long addresses=redis.execute(INCREMENT,List.of(ipKey(ip)),String.valueOf(WINDOW.toSeconds()));
        if(addresses==null||addresses>30)return false;
        Long attempts=redis.execute(INCREMENT,List.of(accountKey(email,ip)),String.valueOf(WINDOW.toSeconds()));
        return attempts!=null&&attempts<=5;
    }
    public void success(String email,String ip){redis.delete(accountKey(email,ip));}
    private static String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
