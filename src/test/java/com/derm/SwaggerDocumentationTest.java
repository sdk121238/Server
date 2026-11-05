package com.derm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerDocumentationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;

    @Test void swaggerPageAndBundledAssetsArePublicAndAvailableLocally() throws Exception {
        http.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
        http.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("swagger-ui-bundle.js")))
            .andExpect(content().string(containsString("swagger-initializer.js")));
        http.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
            .andExpect(content().string(containsString("../v3/api-docs")))
            .andExpect(content().string(containsString("validatorUrl: null")));
        http.perform(get("/webjars/swagger-ui/5.32.15/swagger-ui-bundle.js")).andExpect(status().isOk());
        http.perform(get("/webjars/swagger-ui/5.32.15/swagger-ui.css")).andExpect(status().isOk());
    }

    @Test void documentationUsesLocalServerAndOnlyImplementedOperationsWithoutChangingContract() throws Exception {
        var response=http.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.servers[0].url").value("/api/v1"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
            .andExpect(jsonPath("$.paths['/patients'].post.parameters[0].name").value("Idempotency-Key"))
            .andReturn();
        JsonNode displayed=json.readTree(response.getResponse().getContentAsByteArray());
        JsonNode original;
        try(var stream=new ClassPathResource("contract/backend-v1.openapi.json").getInputStream()) {
            original=json.readTree(stream);
        }
        int count=0;
        for(var paths=displayed.path("paths").fields();paths.hasNext();) {
            var path=paths.next();
            for(var operations=path.getValue().fields();operations.hasNext();) {
                var operation=operations.next();
                if (path.getKey().equals("/objects/{objectId}")) {
                    assertThat(operation.getValue().path("x-documentation-only").asBoolean()).isTrue();
                    assertThat(operation.getValue().path("security").isEmpty()).isTrue();
                    assertThat(operation.getValue().path("servers").get(0).path("url").asText()).isEqualTo("/");
                    continue;
                }
                assertThat(operation.getValue().path("x-priority").asText()).isEqualTo("P0");
                assertThat(original.path("paths").path(path.getKey()).has(operation.getKey())).isTrue();
                count++;
            }
        }
        assertThat(count).isEqualTo(35);
        assertThat(original.path("servers").get(0).path("url").asText()).contains("api.example.invalid");
        assertThat(displayed.path("paths").has("/organizations")).isFalse();
        assertThat(displayed.at("/paths/~1objects~1{objectId}/put/requestBody/content/image~1png/schema/format").asText()).isEqualTo("binary");
        http.perform(get("/swagger-ui/sample-20x20.png")).andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));
        // An unauthenticated viewer can read documentation, but business access stays protected.
        http.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    }
}
