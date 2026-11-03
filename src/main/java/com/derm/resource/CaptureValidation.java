package com.derm.resource;

import com.derm.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CaptureValidation {
    public void validate(JsonNode capture,JsonNode file) {
        JsonNode annotation=capture.path("annotation"),analysis=capture.path("analysis"),calibration=capture.path("calibration");
        double polygonArea=0;
        if(annotation.isObject()) {
            int width=file.get("width").asInt(),height=file.get("height").asInt();
            if(annotation.get("imageWidth").asLong()!=width||annotation.get("imageHeight").asLong()!=height) throw ApiException.invalid("标注尺寸与归档文件不符");
            JsonNode points=annotation.get("points");
            for(JsonNode p:points) if(p.get("x").asDouble()>width||p.get("y").asDouble()>height) throw ApiException.invalid("标注坐标超出图片范围");
            if(points.size()>=3) polygonArea=area(points);
        }
        JsonNode qc=capture.path("metadata").path("liveQc");
        if("GALLERY".equals(capture.path("source").asText())&&qc.path("liveGateExecuted").asBoolean()) throw ApiException.invalid("相册导入不能声明执行实时采集门控");
        if(analysis.path("isValid").asBoolean()) {
            if(capture.path("referenceOnly").asBoolean()||!calibration.isObject()||!annotation.isObject()||polygonArea<=1e-9||!analysis.path("areaMm2").isNumber()||analysis.get("areaMm2").asDouble()<=0) throw ApiException.invalid("有效定量结果需要合法尺度、轮廓和正面积，且不能仅作参考");
            ensureSimple(annotation.get("points"));
            double scale=calibration.get("pixelsPerMm").asDouble(),expected=polygonArea/(scale*scale),actual=analysis.get("areaMm2").asDouble();
            if(!Double.isFinite(expected)||Math.abs(actual-expected)>Math.max(0.01,expected*0.02)) throw ApiException.invalid("面积与标注和尺度不一致");
        }
    }
    private double area(JsonNode points) {
        double sum=0;
        for(int i=0;i<points.size();i++) { JsonNode a=points.get(i),b=points.get((i+1)%points.size()); sum+=a.get("x").asDouble()*b.get("y").asDouble()-b.get("x").asDouble()*a.get("y").asDouble(); }
        return Math.abs(sum)/2;
    }
    private double cross(JsonNode a,JsonNode b,JsonNode c) { return (b.get("x").asDouble()-a.get("x").asDouble())*(c.get("y").asDouble()-a.get("y").asDouble())-(b.get("y").asDouble()-a.get("y").asDouble())*(c.get("x").asDouble()-a.get("x").asDouble()); }
    private boolean on(JsonNode a,JsonNode b,JsonNode c) {
        return c.get("x").asDouble()>=Math.min(a.get("x").asDouble(),b.get("x").asDouble())&&c.get("x").asDouble()<=Math.max(a.get("x").asDouble(),b.get("x").asDouble())&&c.get("y").asDouble()>=Math.min(a.get("y").asDouble(),b.get("y").asDouble())&&c.get("y").asDouble()<=Math.max(a.get("y").asDouble(),b.get("y").asDouble());
    }
    private boolean intersects(JsonNode a,JsonNode b,JsonNode c,JsonNode d) {
        double abC=cross(a,b,c),abD=cross(a,b,d),cdA=cross(c,d,a),cdB=cross(c,d,b);
        return (abC*abD<0&&cdA*cdB<0)||(Math.abs(abC)<1e-9&&on(a,b,c))||(Math.abs(abD)<1e-9&&on(a,b,d))||(Math.abs(cdA)<1e-9&&on(c,d,a))||(Math.abs(cdB)<1e-9&&on(c,d,b));
    }
    private void ensureSimple(JsonNode points) {
        Set<String> distinct=new HashSet<>(); int n=points.size();
        for(JsonNode p:points) if(!distinct.add(p.get("x").asDouble()+":"+p.get("y").asDouble())) throw ApiException.invalid("轮廓顶点不能重复");
        if(distinct.size()<3) throw ApiException.invalid("轮廓至少需要3个不同顶点");
        for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) {
            if(j==i+1||(i==0&&j==n-1)) continue;
            if(intersects(points.get(i),points.get((i+1)%n),points.get(j),points.get((j+1)%n))) throw ApiException.invalid("轮廓不能自相交");
        }
        // Adjacent edges may share only one endpoint, and must not double back.
        for(int i=0;i<n;i++) { JsonNode a=points.get(i),b=points.get((i+1)%n),c=points.get((i+2)%n); if(Math.abs(cross(a,b,c))<1e-9&&(on(a,b,c)||on(b,c,a))) throw ApiException.invalid("轮廓边不能重叠"); }
    }
}
