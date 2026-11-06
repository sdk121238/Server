package com.derm.api;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

@Component
public class Json {
    public final ObjectMapper mapper;
    /** 初始化本组件所需的依赖与配置。 */
    public Json(ObjectMapper mapper) {
        this.mapper=mapper;
        mapper.getFactory().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    /** 创建空JSON对象。 */
    public ObjectNode object() { return mapper.createObjectNode(); }
    /** 创建空JSON数组。 */
    public ArrayNode array() { return mapper.createArrayNode(); }
    /** 读取持久化JSON；损坏数据视为内部错误。 */
    public JsonNode read(String value) {
        try { return mapper.readTree(value); } catch(Exception e) { throw new IllegalStateException("Invalid persisted JSON",e); }
    }
    /** 序列化对象为JSON文本，供响应及数据库存储使用。 */
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Cannot serialize JSON",e); }
    }
    /** 递归合并补丁对象；数组整体替换，显式null保留为清空值。 */
    public ObjectNode merge(ObjectNode base, JsonNode patch) {
        ObjectNode result=base.deepCopy();
        patch.fields().forEachRemaining(entry->{
            String key=entry.getKey(); JsonNode value=entry.getValue();
            if(value.isObject()) result.set(key,merge(result.path(key).isObject()?(ObjectNode)result.get(key):object(),value));
            else result.set(key,value.deepCopy());
        });
        return result;
    }
    /** 递归排序对象键，使内容哈希不受字段顺序影响。 */
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
