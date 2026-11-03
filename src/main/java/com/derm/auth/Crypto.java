package com.derm.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.derm.api.*;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class Crypto {
    private final byte[] key;
    private final SecureRandom random=new SecureRandom();
    private final Json json;
    public Crypto(@Value("${app.signing-key}")String secret,Json json) {
        if(secret.length()<32) throw new IllegalStateException("APP_SIGNING_KEY must contain at least 32 characters");
        key=secret.getBytes(StandardCharsets.UTF_8); this.json=json;
    }
    public String token() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private byte[] mac(String value) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public String sign(JsonNode payload) {
        String text=Base64.getUrlEncoder().withoutPadding().encodeToString(json.write(payload).getBytes(StandardCharsets.UTF_8));
        return text+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(text));
    }
    public JsonNode verify(String token,String kind,int errorStatus,String errorCode) {
        try {
            if(token==null||token.length()>4096) throw new IllegalArgumentException();
            String[] parts=token.split("\\.",-1);
            if(parts.length!=2||!MessageDigest.isEqual(mac(parts[0]),Base64.getUrlDecoder().decode(parts[1]))) throw new IllegalArgumentException();
            JsonNode payload=json.mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if(!kind.equals(payload.path("kind").asText())||payload.path("expiresAt").asLong()<=System.currentTimeMillis()) throw new IllegalArgumentException();
            return payload;
        } catch(Exception e) { throw new ApiException(errorStatus,errorCode,"凭证已失效，请重新申请"); }
    }
}
