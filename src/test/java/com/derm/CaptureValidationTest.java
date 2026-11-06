package com.derm;

import com.derm.api.ApiException;
import com.derm.resource.CaptureValidation;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CaptureValidationTest {
    private final ObjectMapper json=new ObjectMapper();
    private final CaptureValidation validator=new CaptureValidation();
    /** 构造包含指定轮廓点的采集校验测试数据。 */
    private ObjectNode capture(String points) throws Exception {
        return (ObjectNode)json.readTree("""
            {"referenceOnly":false,"calibration":{"pixelsPerMm":2},
             "analysis":{"isValid":true,"areaMm2":25},
             "annotation":{"imageWidth":20,"imageHeight":20,"points":%s}}
            """.formatted(points));
    }
    /** 构造具有固定尺寸的测试文件元信息。 */
    private JsonNode file() throws Exception { return json.readTree("{\"width\":20,\"height\":20}"); }
    /** 验证超大64位尺寸不会截断后绕过图片匹配。 */
    @Test void oversizedInt64DimensionCannotWrapToMatchFile() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10}]");
        ((ObjectNode)capture.get("annotation")).put("imageWidth",4294967316L);
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class);
    }
    /** 验证面积非零的自相交轮廓仍被拒绝。 */
    @Test void selfCrossingPolygonIsRejectedEvenWithNonzeroArea() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10},{\"x\":10,\"y\":0},{\"x\":15,\"y\":5}]");
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class).hasMessageContaining("自相交");
    }
    /** 验证参考图片不能声明有效测量结果。 */
    @Test void referenceOnlyCannotClaimValidMeasurement() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10}]");capture.put("referenceOnly",true);
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class);
    }
}
