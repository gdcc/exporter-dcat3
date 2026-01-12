package io.gdcc.spi.export.dcat3.config.loader;

import static io.gdcc.spi.export.util.AssertionsUtil.assertNodeTemplate;
import static io.gdcc.spi.export.util.AssertionsUtil.assertValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gdcc.spi.export.dcat3.config.model.NodeTemplate;
import io.gdcc.spi.export.dcat3.config.model.ResourceConfig;
import io.gdcc.spi.export.dcat3.config.model.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

public class PropertiesMappingLoaderTest {

    private ResourceConfig load(String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader()
                                        .getResourceAsStream( resource )) {
            assertThat( in ).as( "Test resource should exist: " + resource )
                            .isNotNull();
            return new ResourceConfigLoader().load( in );
        }
    }

    @Test
    void loads_subject() throws Exception {
        ResourceConfig resourceConfig = load( "AP_NL30/mapping/dcat-catalog.properties" );

        assertThat( resourceConfig.subject()
                                  .iriConst() ).isEqualTo( "https://data.example.org/catalog/gdn-test" );
        assertThat( resourceConfig.subject()
                                  .iriJson() ).isNull();
        assertThat( resourceConfig.subject()
                                  .iriTemplate() ).isNull();
    }

    @Test
    void loads_literal_properties_with_lang_and_json_or_const() throws Exception {
        ResourceConfig resourceConfig = load( "AP_NL30/mapping/dcat-catalog.properties" );

        ValueSource titleEn = resourceConfig.props()
                                            .get( "title_en" );
        assertValueSource( titleEn, "literal", "dct:title", "en", null, "$.datasetORE['ore:describes']['schema:isPartOf']['schema:name']", null, null, false );

        ValueSource descrEn = resourceConfig.props()
                                            .get( "description_en" );
        assertValueSource( descrEn, "literal", "dct:description", "en", null, "$.datasetORE['ore:describes']['schema:isPartOf']['schema:description']", null, null, false );
    }

    @Test
    void loads_node_ref_properties_and_node_templates() throws Exception {
        ResourceConfig resourceConfig = load( "AP_NL30/mapping/dcat-catalog.properties" );

        // contact node-ref property
        ValueSource cp = resourceConfig.props()
                                       .get( "contactPoint" );
        assertValueSource( cp, "node-ref", "dcat:contactPoint", null, null, null, null, "contact", false );

        // contact node template
        NodeTemplate contact = resourceConfig.nodes()
                                             .get( "contact" );
        assertNodeTemplate( contact, "contact", "bnode", null, "vcard:Kind" );
        assertThat( contact.props() ).hasSize( 3 );

        assertValueSource( contact.props()
                                  .get( "fn_en" ), "literal", "vcard:fn", "en", null, null, "Geological Survey of the Netherlands", null, false );
        assertValueSource( contact.props()
                                  .get( "email" ), "iri", "vcard:hasEmail", null, null, null, "mailto:support@geologischedienst.nl", null, false );
        assertValueSource( contact.props()
                                  .get( "url" ), "iri", "vcard:hasURL", null, null, null, "https://www.geologischedienst.nl/contact/", null, false );

        // publisher node-ref property
        ValueSource pub = resourceConfig.props()
                                        .get( "publisher" );
        assertValueSource( pub, "node-ref", "dct:publisher", null, null, null, null, "publisher", false );

        // publisher node template
        NodeTemplate publisher = resourceConfig.nodes()
                                               .get( "publisher" );
        assertNodeTemplate( publisher, "publisher", "iri", "https://ror.org/01bnjb948", "foaf:Agent" );
        assertThat( publisher.props() ).hasSize( 2 );
        assertValueSource( publisher.props()
                                    .get( "name_nl" ), "literal", "foaf:name", "nl", null, null, "Nederlandse Organisatie voor Toegepast Natuurwetenschappelijk Onderzoek (nl), TNO", null, false );
        assertValueSource( publisher.props()
                                    .get( "name_en" ), "literal", "foaf:name", "en", null, null, "Netherlands Organisation for Applied Scientific Research", null, false );
    }

    @Test
    void ignores_unknown_keys_but_keeps_known_ones() throws Exception {
        // simulate unknown key: load a tiny properties string
        String props = """
            props.foo.predicate = dct:title
            props.foo.bar = unknown
            """;
        ResourceConfig resourceConfig = new ResourceConfigLoader().load( new ByteArrayInputStream( props.getBytes() ) );

        // loader should have created ValueSource and set the known field only
        assertThat( resourceConfig.props() ).containsKey( "foo" );
        ValueSource vs = resourceConfig.props()
                                       .get( "foo" );
        assertThat( vs.predicate() ).isEqualTo( "dct:title" );
        // unknown 'bar' should be ignored silently
        assertThat( vs.lang() ).isNull();
    }

    @Test
    void supports_map_entries_when_present() throws Exception {
        String props = """
            props.language.predicate = dct:language
            props.language.as = iri
            props.language.json = $.dataset.language
            props.language.map.nl = http://publications.europa.eu/resource/authority/language/NLD
            props.language.map.en = http://publications.europa.eu/resource/authority/language/ENG
            """;
        ResourceConfig cfg = new ResourceConfigLoader().load( new java.io.ByteArrayInputStream( props.getBytes() ) );

        ValueSource lang = cfg.props()
                              .get( "language" );
        assertThat( lang.map() ).containsEntry( "nl", "http://publications.europa.eu/resource/authority/language/NLD" )
                                .containsEntry( "en", "http://publications.europa.eu/resource/authority/language/ENG" );
    }

    @Test
    void fails_cleanly_on_missing_resource() {
        assertThatThrownBy( () -> {
            try (InputStream in = getClass().getClassLoader()
                                            .getResourceAsStream( "mappings/nope.properties" )) {
                new ResourceConfigLoader().load( in ); // in == null → NPE if you don’t guard
            }
        } ).isInstanceOf( NullPointerException.class )
           .as( "Guard against null InputStream in your loader (or throw FileNotFound)" )
           .withFailMessage( "Consider making PropertiesMappingLoader.load throw FileNotFoundException" + " when InputStream is null" );
    }

    @Test
    void loads_node_template_iri_fields_and_map_and_multi() throws Exception {
        String props = """
                # rights node: boolean -> mapped IRI
                nodes.rights.kind = iri
                nodes.rights.type = dct:RightsStatement
                nodes.rights.iri.json = $.restricted
                nodes.rights.map.true  = http://publications.europa.eu/resource/authority/access-right/RESTRICTED
                nodes.rights.map.false = http://publications.europa.eu/resource/authority/access-right/PUBLIC
            
                # theme node: multi values -> mapped IRIs
                nodes.theme.kind = iri
                nodes.theme.type = skos:Concept
                nodes.theme.multi = true
                nodes.theme.iri.json = $.themes[*]
                nodes.theme.map.ener = http://publications.europa.eu/resource/authority/data-theme/ENER
                nodes.theme.map.tech = http://publications.europa.eu/resource/authority/data-theme/TECH
            
                # accessURL node: IRI from format
                nodes.acc.kind = iri
                nodes.acc.type = rdfs:Resource
                nodes.acc.iri.json = $.id
                nodes.acc.iri.format = http://localhost:8080/api/access/datafile/${value}
            """;

        ResourceConfig cfg = new ResourceConfigLoader().load( new ByteArrayInputStream( props.getBytes() ) );

        // rights
        NodeTemplate rights = cfg.nodes()
                                 .get( "rights" );
        assertThat( rights ).isNotNull();
        assertThat( rights.kind() ).isEqualTo( "iri" );
        assertThat( rights.type() ).isEqualTo( "dct:RightsStatement" );
        assertThat( rights.iriJson() ).isEqualTo( "$.restricted" );
        assertThat( rights.iriFormat() ).isNull();
        assertThat( rights.multi() ).isFalse();
        assertThat( rights.iriMap() ).containsEntry( "true", "http://publications.europa.eu/resource/authority/access-right/RESTRICTED" )
                                     .containsEntry( "false", "http://publications.europa.eu/resource/authority/access-right/PUBLIC" );

        // theme
        NodeTemplate theme = cfg.nodes()
                                .get( "theme" );
        assertThat( theme ).isNotNull();
        assertThat( theme.kind() ).isEqualTo( "iri" );
        assertThat( theme.type() ).isEqualTo( "skos:Concept" );
        assertThat( theme.multi() ).isTrue();
        assertThat( theme.iriJson() ).isEqualTo( "$.themes[*]" );
        assertThat( theme.iriMap() ).containsEntry( "ener", "http://publications.europa.eu/resource/authority/data-theme/ENER" )
                                    .containsEntry( "tech", "http://publications.europa.eu/resource/authority/data-theme/TECH" );

        // accessURL
        NodeTemplate acc = cfg.nodes()
                              .get( "acc" );
        assertThat( acc ).isNotNull();
        assertThat( acc.kind() ).isEqualTo( "iri" );
        assertThat( acc.type() ).isEqualTo( "rdfs:Resource" );
        assertThat( acc.iriJson() ).isEqualTo( "$.id" );
        assertThat( acc.iriFormat() ).isEqualTo( "http://localhost:8080/api/access/datafile/${value}" );
    }
}
