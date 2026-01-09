package io.gdcc.spi.export.dcat3.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceMapperTest {

    private static JsonNode jsonNode(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(json);
    }

    private static JaywayJsonFinder finderFor(String json) throws Exception {
        return new JaywayJsonFinder(jsonNode(json));
    }

    @Test
    @DisplayName("build() adds RDF.type and a literal property from constValue with language")
    void build_adds_type_and_literal_from_const() throws Exception {
        // Real Prefixes
        Map<String, String> ns = new LinkedHashMap<String, String>();
        ns.put("dcat", "http://www.w3.org/ns/dcat#");
        ns.put("dct", "http://purl.org/dc/terms/");
        Prefixes prefixes = new Prefixes(ns);

        // ResourceConfig with deep stubs for subject()
        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/id");
        when(rc.subject().iriTemplate()).thenReturn(null);
        when(rc.subject().iriFormat()).thenReturn(null);
        when(rc.subject().iriJson()).thenReturn(null);

        // ValueSource for dct:title literal
        ValueSource vsTitle = mock(ValueSource.class);
        when(vsTitle.predicate()).thenReturn("dct:title");
        when(vsTitle.as()).thenReturn("literal");
        when(vsTitle.constValue()).thenReturn("Demo");
        when(vsTitle.lang()).thenReturn("en");
        when(vsTitle.datatype()).thenReturn(null);
        when(vsTitle.map()).thenReturn(java.util.Collections.emptyMap());
        when(vsTitle.jsonPaths()).thenReturn(java.util.Collections.emptyList());
        when(vsTitle.json()).thenReturn(null);
        when(vsTitle.multi()).thenReturn(false);
        when(vsTitle.format()).thenReturn(null);

        Map<String, ValueSource> props = new LinkedHashMap<String, ValueSource>();
        props.put("title", vsTitle);
        when(rc.props()).thenReturn(props);
        when(rc.nodes()).thenReturn(java.util.Collections.emptyMap());
        when(rc.scopeJson()).thenReturn(null);

        JaywayJsonFinder finder = finderFor("{\"dataset\":{\"title\":\"Demo\"}}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dcat:Dataset");

        Model model = mapper.build(finder);
        assertThat(model).isNotNull();

        // Verify RDF.type triple
        List<Statement> typeStmts =
                model.listStatements(
                                (Resource) null,
                                model.getProperty(
                                        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                                model.getResource("http://www.w3.org/ns/dcat#Dataset"))
                        .toList();
        assertThat(typeStmts).hasSize(1);

        // Verify title literal with language
        List<Statement> titleStmts =
                model.listStatements(
                                (Resource) null,
                                model.getProperty("http://purl.org/dc/terms/title"),
                                (org.apache.jena.rdf.model.RDFNode) null)
                        .toList();
        assertThat(titleStmts).hasSize(1);
        assertThat(titleStmts.get(0).getObject().asLiteral().getLanguage()).isEqualTo("en");
        assertThat(titleStmts.get(0).getObject().asLiteral().getString()).isEqualTo("Demo");
    }

    @Test
    @DisplayName(
            "build() maps IRI object from JSON path with as='iri' and default (single) selection")
    void build_maps_iri_from_json() throws Exception {
        // Real Prefixes
        Map<String, String> ns = new LinkedHashMap<String, String>();
        ns.put("dcat", "http://www.w3.org/ns/dcat#");
        ns.put("dct", "http://purl.org/dc/terms/");
        Prefixes prefixes = new Prefixes(ns);

        // ResourceConfig + subject
        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/id");
        when(rc.subject().iriTemplate()).thenReturn(null);
        when(rc.subject().iriFormat()).thenReturn(null);
        when(rc.subject().iriJson()).thenReturn(null);

        // ValueSource to map an IRI found in JSON
        ValueSource vsId = mock(ValueSource.class);
        when(vsId.predicate()).thenReturn("dct:identifier");
        when(vsId.as()).thenReturn("iri");
        when(vsId.constValue()).thenReturn(null);
        when(vsId.json()).thenReturn("$.dataset.identifier");
        when(vsId.multi()).thenReturn(false);
        when(vsId.format()).thenReturn(null);
        when(vsId.lang()).thenReturn(null);
        when(vsId.datatype()).thenReturn(null);
        when(vsId.map()).thenReturn(java.util.Collections.emptyMap());
        when(vsId.jsonPaths()).thenReturn(java.util.Collections.emptyList());

        Map<String, ValueSource> props = new LinkedHashMap<String, ValueSource>();
        props.put("identifier", vsId);
        when(rc.props()).thenReturn(props);
        when(rc.nodes()).thenReturn(java.util.Collections.emptyMap());
        when(rc.scopeJson()).thenReturn(null);

        JaywayJsonFinder finder =
                finderFor("{\"dataset\":{\"identifier\":\"http://example.org/id-iri\"}}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dcat:Dataset");

        Model model = mapper.build(finder);
        assertThat(model).isNotNull();

        // Verify identifier as IRI object
        List<Statement> idStmts =
                model.listStatements(
                                (Resource) null,
                                model.getProperty("http://purl.org/dc/terms/identifier"),
                                (org.apache.jena.rdf.model.RDFNode) null)
                        .toList();
        assertThat(idStmts).hasSize(1);
        assertThat(idStmts.get(0).getObject().isResource()).isTrue();
        assertThat(idStmts.get(0).getObject().asResource().getURI())
                .isEqualTo("http://example.org/id-iri");
    }


    // --- NEW TESTS: iri.format & iri.map on ValueSource and NodeTemplate ---

    @Test
    @DisplayName("ValueSource as=iri supports inline JSONPath in format")
    void valuesource_iri_format_inline_jsonpath() throws Exception {
        Map<String, String> ns = new LinkedHashMap<>();
        ns.put("dcat", "http://www.w3.org/ns/dcat#");
        ns.put("dct",  "http://purl.org/dc/terms/");
        Prefixes prefixes = new Prefixes(ns);

        // subject
        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/dist/4");
        when(rc.nodes()).thenReturn(java.util.Collections.emptyMap());
        when(rc.scopeJson()).thenReturn(null);

        // dcat:accessURL as IRI built from format and inline JSONPath
        ValueSource vs = mock(ValueSource.class);
        when(vs.predicate()).thenReturn("dcat:accessURL");
        when(vs.as()).thenReturn("iri");
        when(vs.json()).thenReturn(null);
        when(vs.format()).thenReturn("http://localhost:8080/api/access/datafile/${$.id}");
        when(vs.map()).thenReturn(java.util.Collections.emptyMap());
        when(vs.jsonPaths()).thenReturn(java.util.Collections.emptyList());
        when(vs.multi()).thenReturn(false);
        Map<String, ValueSource> props = new LinkedHashMap<>();
        props.put("accessURL", vs);
        when(rc.props()).thenReturn(props);

        JaywayJsonFinder finder = finderFor("{\"id\":\"4\"}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dcat:Distribution");
        Model model = mapper.build(finder);

        List<Statement> stmts = model.listStatements(
            (Resource) null,
            model.getProperty("http://www.w3.org/ns/dcat#accessURL"),
            (org.apache.jena.rdf.model.RDFNode) null).toList();

        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0).getObject().isResource()).isTrue();
        assertThat(stmts.get(0).getObject().asResource().getURI())
            .isEqualTo("http://localhost:8080/api/access/datafile/4");
    }

    @Test
    @DisplayName("ValueSource as=iri maps boolean/string via map.* to authority IRIs")
    void valuesource_iri_map_boolean_to_authority() throws Exception {
        Map<String, String> ns = new LinkedHashMap<>();
        ns.put("dct", "http://purl.org/dc/terms/");
        Prefixes prefixes = new Prefixes(ns);

        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/ds/1");
        when(rc.nodes()).thenReturn(java.util.Collections.emptyMap());
        when(rc.scopeJson()).thenReturn(null);

        ValueSource vs = mock(ValueSource.class);
        when(vs.predicate()).thenReturn("dct:accessRights");
        when(vs.as()).thenReturn("iri");
        when(vs.json()).thenReturn("$.restricted");
        when(vs.multi()).thenReturn(false);
        when(vs.format()).thenReturn(null);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("true",  "http://publications.europa.eu/resource/authority/access-right/RESTRICTED");
        map.put("false", "http://publications.europa.eu/resource/authority/access-right/PUBLIC");
        when(vs.map()).thenReturn(map);
        when(vs.jsonPaths()).thenReturn(java.util.Collections.emptyList());
        Map<String, ValueSource> props = new LinkedHashMap<>();
        props.put("accessRights", vs);
        when(rc.props()).thenReturn(props);

        JaywayJsonFinder finder = finderFor("{\"restricted\": false}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dct:Dataset");
        Model model = mapper.build(finder);

        List<Statement> stmts = model.listStatements(
            (Resource) null,
            model.getProperty("http://purl.org/dc/terms/accessRights"),
            (org.apache.jena.rdf.model.RDFNode) null).toList();

        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0).getObject().isResource()).isTrue();
        assertThat(stmts.get(0).getObject().asResource().getURI())
            .isEqualTo("http://publications.europa.eu/resource/authority/access-right/PUBLIC");
    }

    @Test
    @DisplayName("NodeTemplate as node-ref uses iri.format for object IRI")
    void node_ref_uses_template_iri_format() throws Exception {
        Map<String, String> ns = new LinkedHashMap<>();
        ns.put("dcat", "http://www.w3.org/ns/dcat#");
        ns.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        Prefixes prefixes = new Prefixes(ns);

        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/dist/4");
        when(rc.scopeJson()).thenReturn(null);

        // NodeTemplate 'acc' with iri.format
        io.gdcc.spi.export.dcat3.config.model.NodeTemplate accT =
            new io.gdcc.spi.export.dcat3.config.model.NodeTemplate(
                "acc", "iri", null, "$.id", "http://localhost:8080/api/access/datafile/${value}",
                "rdfs:Resource", false, java.util.Collections.emptyMap(), java.util.Collections.emptyMap()
            );
        Map<String, io.gdcc.spi.export.dcat3.config.model.NodeTemplate> nodes = new LinkedHashMap<>();
        nodes.put("acc", accT);
        when(rc.nodes()).thenReturn(nodes);

        ValueSource vs = mock(ValueSource.class);
        when(vs.predicate()).thenReturn("dcat:accessURL");
        when(vs.as()).thenReturn("node-ref");
        when(vs.nodeRef()).thenReturn("acc");
        Map<String, ValueSource> props = new LinkedHashMap<>();
        props.put("accessURL", vs);
        when(rc.props()).thenReturn(props);

        JaywayJsonFinder finder = finderFor("{\"id\":\"4\"}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dcat:Distribution");
        Model model = mapper.build(finder);

        List<Statement> stmts = model.listStatements(
            (Resource) null,
            model.getProperty("http://www.w3.org/ns/dcat#accessURL"),
            (org.apache.jena.rdf.model.RDFNode) null).toList();

        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0).getObject().isResource()).isTrue();
        assertThat(stmts.get(0).getObject().asResource().getURI())
            .isEqualTo("http://localhost:8080/api/access/datafile/4");
    }

    @Test
    @DisplayName("NodeTemplate multi=true + map.* emits multiple mapped concept IRIs with type")
    void node_ref_multi_map_emits_multiple() throws Exception {
        Map<String, String> ns = new LinkedHashMap<>();
        ns.put("dcat", "http://www.w3.org/ns/dcat#");
        ns.put("skos", "http://www.w3.org/2004/02/skos/core#");
        Prefixes prefixes = new Prefixes(ns);

        ResourceConfig rc = mock(ResourceConfig.class, RETURNS_DEEP_STUBS);
        when(rc.subject().iriConst()).thenReturn("http://example.org/ds/1");
        when(rc.scopeJson()).thenReturn(null);

        Map<String, String> nodeMap = new LinkedHashMap<>();
        nodeMap.put("ener", "http://publications.europa.eu/resource/authority/data-theme/ENER");
        nodeMap.put("tech", "http://publications.europa.eu/resource/authority/data-theme/TECH");

        io.gdcc.spi.export.dcat3.config.model.NodeTemplate themeT =
            new io.gdcc.spi.export.dcat3.config.model.NodeTemplate(
                "theme", "iri", null, "$.themes[*]", null,
                "skos:Concept", true, nodeMap, java.util.Collections.emptyMap()
            );
        Map<String, io.gdcc.spi.export.dcat3.config.model.NodeTemplate> nodes = new LinkedHashMap<>();
        nodes.put("theme", themeT);
        when(rc.nodes()).thenReturn(nodes);

        ValueSource vs = mock(ValueSource.class);
        when(vs.predicate()).thenReturn("dcat:theme");
        when(vs.as()).thenReturn("node-ref");
        when(vs.nodeRef()).thenReturn("theme");
        Map<String, ValueSource> props = new LinkedHashMap<>();
        props.put("theme", vs);
        when(rc.props()).thenReturn(props);

        JaywayJsonFinder finder = finderFor("{\"themes\":[\"ener\",\"tech\"]}");
        ResourceMapper mapper = new ResourceMapper(rc, prefixes, "dcat:Dataset");
        Model model = mapper.build(finder);

        List<Statement> themeStmts = model.listStatements(
            (Resource) null,
            model.getProperty("http://www.w3.org/ns/dcat#theme"),
            (org.apache.jena.rdf.model.RDFNode) null).toList();

        assertThat(themeStmts).hasSize(2);

        List<String> objUris = themeStmts.stream()
                                         .map(s -> s.getObject().asResource().getURI()).toList();

        assertThat(objUris).containsExactlyInAnyOrder(
            "http://publications.europa.eu/resource/authority/data-theme/ENER",
            "http://publications.europa.eu/resource/authority/data-theme/TECH"
        );

        // also ensure each emitted node carries rdf:type skos:Concept
        for (Statement s : themeStmts) {
            Resource obj = s.getObject().asResource();
            boolean hasType = model.contains(obj,
                                             model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                                             model.getResource("http://www.w3.org/2004/02/skos/core#Concept"));
            assertThat(hasType).isTrue();
        }
    }

}
