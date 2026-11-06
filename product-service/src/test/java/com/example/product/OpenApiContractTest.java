package com.example.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Spec-first check: every operation in the hand-written contract
 * ({@code openapi/product-api.yaml}) must exist in the code, and the code must not expose
 * operations that the contract does not describe.
 * <p>
 * The "code" side is the document springdoc generates from the controllers ({@code /v3/api-docs}).
 * If someone adds, removes or renames an endpoint without updating the contract, this test fails.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "product.seed.enabled=false"})
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void codeMatchesTheSpecFirstContract() throws Exception {
        Set<String> contract;
        try (InputStream in = new ClassPathResource("openapi/product-api.yaml").getInputStream()) {
            contract = operations(new Yaml().load(in));
        }

        String generatedJson = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        // JSON is valid YAML, so the same parser reads both documents.
        Set<String> generated = operations(new Yaml().load(generatedJson));

        assertThat(generated)
                .as("operations generated from the code vs. operations in product-api.yaml")
                .containsExactlyInAnyOrderElementsOf(contract);
    }

    @Test
    void contractIsOpenApi31() throws Exception {
        try (InputStream in = new ClassPathResource("openapi/product-api.yaml").getInputStream()) {
            Map<String, Object> spec = new Yaml().load(in);
            assertThat(String.valueOf(spec.get("openapi"))).startsWith("3.1");
        }
    }

    /** "GET /api/v1/products/{id}" style entries for every operation in an OpenAPI document. */
    @SuppressWarnings("unchecked")
    private static Set<String> operations(Map<String, Object> document) {
        Set<String> result = new TreeSet<>();
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        paths.forEach((path, item) -> ((Map<String, Object>) item).keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> result.add(method.toUpperCase() + " " + path)));
        return result;
    }
}
