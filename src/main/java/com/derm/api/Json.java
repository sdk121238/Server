package com.derm.api;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

@Component
public class Json {
    public final ObjectMapper mapper;
    public Json(ObjectMapper mapper) {
        this.mapper=mapper;
        mapper.getFactory().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public ObjectNode object() { return mapper.createObjectNode(); }
    public ArrayNode array() { return mapper.createArrayNode(); }
    public JsonNode read(String value) {
        try { return mapper.readTree(value); } catch(Exception e) { throw new IllegalStateException("Invalid persisted JSON",e); }
    }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Cannot serialize JSON",e); }
    }
    public ObjectNode merge(ObjectNode base, JsonNode patch) {
        ObjectNode result=base.deepCopy();
        patch.fields().forEachRemaining(entry->{
            String key=entry.getKey(); JsonNode value=entry.getValue();
            if(value.isObject()) result.set(key,merge(result.path(key).isObject()?(ObjectNode)result.get(key):object(),value));
            else result.set(key,value.deepCopy());
        });
        return result;
    }
    public JsonNode canonical(JsonNode node) {
        if(node.isObject()) {
            ObjectNode out=object(); java.util.TreeMap<String,JsonNode> sorted=new java.util.TreeMap<>();
            node.fields().forEachRemaining(e->sorted.put(e.getKey(),e.getValue()));
            sorted.forEach((k,v)->out.set(k,canonical(v))); return out;
        }
        if(node.isArray()) { ArrayNode out=array(); node.forEach(v->out.add(canonical(v))); return out; }
        return node;
    }
}
