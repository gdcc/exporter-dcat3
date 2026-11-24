package io.gdcc.spi.export.dcat3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gdcc.spi.export.dcat3.config.loader.ResourceConfigLoader;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.mapping.JaywayJsonFinder;
import io.gdcc.spi.export.dcat3.mapping.Prefixes;
import io.gdcc.spi.export.dcat3.mapping.ResourceMapper;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

/** A format with indexed placeholders must not emit anything when the JSON source lacks the values. */
class GeometryFormatIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DCT_SPATIAL = "http://purl.org/dc/terms/spatial";
    private static final String GEO_AS_WKT = "http://www.opengis.net/ont/geosparql#asWKT";

    private static InputStream resource(String path) {
        InputStream in = GeometryFormatIntegrationTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(in).as("Test resource should exist: " + path).isNotNull();
        return in;
    }

    private static Model runMapping(String inputPath) throws Exception {
        ResourceConfig cfg;
        try (InputStream in = resource("mapping/geometry_format/dcat-dataset.properties")) {
            cfg = new ResourceConfigLoader().load(in);
        }
        JsonNode input;
        try (InputStream in = resource(inputPath)) {
            input = MAPPER.readTree(in);
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dct", "http://purl.org/dc/terms/");
        p.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        p.put("locn", "http://www.w3.org/ns/locn#");
        p.put("geo", "http://www.opengis.net/ont/geosparql#");
        ResourceMapper mapper = new ResourceMapper(cfg, new Prefixes(p), "dcat:Dataset");
        return mapper.build(new JaywayJsonFinder(input));
    }

    @Test
    void polygon_emitted_when_bbox_present() throws Exception {
        Model model = runMapping("input/geometry_format/input_with_bbox.json");

        Resource subj = model.createResource("https://example.org/ds/1");
        assertThat(subj.hasProperty(model.createProperty(DCT_SPATIAL))).isTrue();

        Property asWkt = model.createProperty(GEO_AS_WKT);
        assertThat(model.listObjectsOfProperty(asWkt).toList())
                .singleElement()
                .satisfies(o -> assertThat(o.asLiteral().getLexicalForm())
                        .isEqualTo("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> "
                                + "POLYGON((3.3 50.7, 7.2 50.7, 7.2 53.6, 3.3 53.6, 3.3 50.7))"));
    }

    @Test
    void nothing_emitted_when_geospatial_block_missing() throws Exception {
        Model model = runMapping("input/geometry_format/input_no_geospatial.json");

        assertThat(model.listObjectsOfProperty(model.createProperty(DCT_SPATIAL)).toList())
                .as("no dct:spatial without a bounding box")
                .isEmpty();
        assertThat(model.listObjectsOfProperty(model.createProperty(GEO_AS_WKT)).toList())
                .isEmpty();
    }
}
