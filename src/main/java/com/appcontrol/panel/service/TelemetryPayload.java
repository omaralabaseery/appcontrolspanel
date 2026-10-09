package com.appcontrol.panel.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.charset.StandardCharsets;
final class TelemetryPayload {
    private TelemetryPayload() {}
    static Object normalize(JsonNode node,ObjectMapper mapper) {
        if(node==null || node.isNull()) return null;
        if(node.toString().getBytes(StandardCharsets.UTF_8).length>16384)
            throw new IllegalArgumentException("Each input/output JSON payload must be at most 16 KB");
        return scrub(mapper.convertValue(node,Object.class),0);
    }
    private static Object scrub(Object value,int depth) {
        if(depth>12) throw new IllegalArgumentException("JSON payload exceeds maximum nesting depth");
        if(value instanceof Map<?,?> map) {
            var out=new LinkedHashMap<String,Object>();
            map.forEach((key,v)->{
                String name=key.toString();String normalized=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                boolean secret=normalized.contains("password") || normalized.contains("token") || normalized.contains("secret") ||
                    Set.of("authorization","cookie","setcookie","apikey","privatekey","pin","cvv","cardnumber").contains(normalized);
                out.put(name,secret?"[REDACTED]":scrub(v,depth+1));
            });return out;
        }
        if(value instanceof List<?> list) return list.stream().map(v->scrub(v,depth+1)).toList();
        return value;
    }
}
