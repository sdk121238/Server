package com.derm.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;
import java.time.LocalDate;

/** Reads the same request schemas used by the client. Business invariants stay in domain services. */
@Component
public class Contract {
    public record Route(String method,String path,JsonNode operation,Pattern pattern,List<String> names) {}
    public record Match(Route route,Map<String,String> params) {}
    private final JsonNode document;
    private final List<Route> routes=new ArrayList<>();
    public Contract(Json json) throws Exception {
        try(var stream=new ClassPathResource("contract/backend-v1.openapi.json").getInputStream()) { document=json.mapper.readTree(stream); }
        document.path("paths").fields().forEachRemaining(p->p.getValue().fields().forEachRemaining(op->{
            if(!Set.of("get","post","patch","delete","put").contains(op.getKey())) return;
            List<String> names=new ArrayList<>(); Matcher m=Pattern.compile("\\{(\\w+)}").matcher(p.getKey());
            StringBuilder regex=new StringBuilder("^"); int start=0;
            while(m.find()) { regex.append(Pattern.quote(p.getKey().substring(start,m.start()))).append("([^/]+)"); names.add(m.group(1)); start=m.end(); }
            regex.append(Pattern.quote(p.getKey().substring(start))).append("$");
            routes.add(new Route(op.getKey().toUpperCase(Locale.ROOT),p.getKey(),op.getValue(),Pattern.compile(regex.toString()),names));
        }));
    }
    public Match match(String method,String path) {
        for(Route route:routes) {
            Matcher m=route.pattern.matcher(path);
            if(route.method.equals(method)&&m.matches()) {
                Map<String,String> params=new HashMap<>();
                for(int i=0;i<route.names.size();i++) { String id=m.group(i+1); uuid(id); params.put(route.names.get(i),UUID.fromString(id).toString()); }
                return new Match(route,params);
            }
        }
        throw ApiException.missing();
    }
    public void validate(String name,JsonNode value) { validate(document.path("components").path("schemas").path(name),value,"$"); }
    public JsonNode normalize(JsonNode schema,JsonNode value) {
        if(schema.has("$ref")) return normalize(document.at(schema.get("$ref").asText().substring(1)),value);
        if(value==null||value.isNull()) return value;
        if("uuid".equals(schema.path("format").asText())) return com.fasterxml.jackson.databind.node.TextNode.valueOf(UUID.fromString(value.asText()).toString());
        if(value.isObject()) {
            var result=((com.fasterxml.jackson.databind.node.ObjectNode)value).deepCopy();
            value.fields().forEachRemaining(e->{JsonNode child=schema.path("properties").get(e.getKey());if(child!=null) result.set(e.getKey(),normalize(child,e.getValue()));});return result;
        }
        if(value.isArray()) {
            var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();value.forEach(v->result.add(normalize(schema.path("items"),v)));return result;
        }
        return value;
    }
    public void validate(JsonNode schema,JsonNode value,String path) {
        if(schema.has("$ref")) { validate(document.at(schema.get("$ref").asText().substring(1)),value,path); return; }
        if(value==null||value.isNull()) { if(!schema.path("nullable").asBoolean()) fail(path); return; }
        if(schema.has("enum")) { boolean found=false; for(JsonNode choice:schema.get("enum")) if(choice.equals(value)) found=true; if(!found) fail(path); }
        switch(schema.path("type").asText()) {
            case "object" -> {
                if(!value.isObject()) fail(path);
                for(JsonNode field:schema.path("required")) if(!value.has(field.asText())) fail(path+"."+field.asText());
                value.fields().forEachRemaining(e->{
                    JsonNode child=schema.path("properties").get(e.getKey());
                    if(child==null) {
                        JsonNode additional=schema.get("additionalProperties");
                        if(additional!=null&&additional.isBoolean()&&!additional.asBoolean()) fail(path+"."+e.getKey());
                        if(additional!=null&&additional.isObject()) validate(additional,e.getValue(),path+"."+e.getKey());
                    } else validate(child,e.getValue(),path+"."+e.getKey());
                });
            }
            case "array" -> {
                if(!value.isArray()||value.size()<schema.path("minItems").asInt(0)||value.size()>schema.path("maxItems").asInt(Integer.MAX_VALUE)) fail(path);
                for(JsonNode item:value) validate(schema.path("items"),item,path+"[]");
            }
            case "string" -> {
                if(!value.isTextual()) fail(path);
                String s=value.asText(); int length=s.codePointCount(0,s.length());
                if(length<schema.path("minLength").asInt(0)||length>schema.path("maxLength").asInt(Integer.MAX_VALUE)) fail(path);
                if(schema.has("minLength")&&schema.path("minLength").asInt()>0&&s.isBlank()) fail(path);
                if(schema.has("pattern")&&!Pattern.compile(schema.get("pattern").asText()).matcher(s).find()) fail(path);
                if("uuid".equals(schema.path("format").asText())) uuid(s);
                if("date".equals(schema.path("format").asText())) { try { if(!LocalDate.parse(s).toString().equals(s)) fail(path); } catch(Exception e) { fail(path); } }
            }
            case "number","integer" -> {
                if(!value.isNumber()||!Double.isFinite(value.asDouble())||("integer".equals(schema.path("type").asText())&&(!value.isIntegralNumber()||!value.canConvertToLong()))) fail(path);
                double d=value.asDouble();
                if(schema.has("minimum")&&(d<schema.get("minimum").asDouble()||(schema.path("exclusiveMinimum").asBoolean()&&d==schema.get("minimum").asDouble()))) fail(path);
                if(schema.has("maximum")&&d>schema.get("maximum").asDouble()) fail(path);
            }
            case "boolean" -> { if(!value.isBoolean()) fail(path); }
            default -> { }
        }
    }
    private void fail(String path) { throw ApiException.invalid("字段不符合合同："+path); }
    public static void uuid(String id) { if(id==null||!id.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) throw ApiException.invalid("UUID格式不合法"); }
}
