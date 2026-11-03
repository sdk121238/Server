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
    private ObjectNode capture(String points) throws Exception {
        return (ObjectNode)json.readTree("""
            {"referenceOnly":false,"calibration":{"pixelsPerMm":2},
             "analysis":{"isValid":true,"areaMm2":25},
             "annotation":{"imageWidth":20,"imageHeight":20,"points":%s}}
            """.formatted(points));
    }
    private JsonNode file() throws Exception { return json.readTree("{\"width\":20,\"height\":20}"); }
    @Test void oversizedInt64DimensionCannotWrapToMatchFile() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10}]");
        ((ObjectNode)capture.get("annotation")).put("imageWidth",4294967316L);
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class);
    }
    @Test void selfCrossingPolygonIsRejectedEvenWithNonzeroArea() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10},{\"x\":10,\"y\":0},{\"x\":15,\"y\":5}]");
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class).hasMessageContaining("自相交");
    }
    @Test void referenceOnlyCannotClaimValidMeasurement() throws Exception {
        var capture=capture("[{\"x\":0,\"y\":0},{\"x\":10,\"y\":0},{\"x\":10,\"y\":10},{\"x\":0,\"y\":10}]");capture.put("referenceOnly",true);
        JsonNode file=file();assertThatThrownBy(()->validator.validate(capture,file)).isInstanceOf(ApiException.class);
    }
}
