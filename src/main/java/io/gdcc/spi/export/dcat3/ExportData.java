package io.gdcc.spi.export.dcat3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.gdcc.spi.export.ExportDataProvider;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * ExportData is the mapping context root for the DCAT3 exporter.
 *
 * <p>All fields are converted to a single JsonNode tree (see Dcat3ExporterBase),
 * which is then queried by JSONPath expressions in the mapping configuration.</p>
 *
 * <p>New: env node containing environment-derived values, notably:
 * <ul>
 *   <li>env.siteUrl: the public base URL of the Dataverse installation</li>
 *   <li>env.apiBaseUrl: siteUrl + "/api/"</li>
 * </ul>
 * This enables environment-independent mappings (no localhost constants).</p>
 *
 * @param datasetJson native JSON tree
 * @param datasetORE ORE JSON tree
 * @param datasetFileDetails array JSON tree
 * @param datasetSchemaDotOrg schema.org JSON tree
 * @param dataCiteXml DataCite as JSON tree (converted from XML)
 * @param env environment context (computed)
 */
public record ExportData(
    JsonNode datasetJson,
    JsonNode datasetORE,
    JsonNode datasetFileDetails,
    JsonNode datasetSchemaDotOrg,
    JsonNode dataCiteXml,
    JsonNode env
) {

    public static ExportDataBuilder builder() {
        return new ExportDataBuilder();
    }

    public static class ExportDataBuilder {
        private ExportDataProvider provider;

        public ExportDataBuilder provider(ExportDataProvider provider) {
            this.provider = provider;
            return this;
        }

        public ExportData build() {
            ObjectMapper jsonMapper = new ObjectMapper();
            jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            try {
                // provider already returns JsonObject/JsonArray for JSON sources
                JsonNode datasetJson = jsonMapper.readTree(provider.getDatasetJson().toString());
                JsonNode datasetORE = jsonMapper.readTree(provider.getDatasetORE().toString());
                JsonNode datasetFileDetails = jsonMapper.readTree(provider.getDatasetFileDetails().toString());
                JsonNode datasetSchemaDotOrg = jsonMapper.readTree(provider.getDatasetSchemaDotOrg().toString());

                // DataCite XML → JsonNode once
                JsonNode dataCiteXml = xmlMapper.readTree(provider.getDataCiteXml());

                // Build env node (siteUrl + apiBaseUrl) for mapping
                ObjectNode env = buildEnv(jsonMapper, datasetSchemaDotOrg, datasetORE);

                return new ExportData(
                    datasetJson,
                    datasetORE,
                    datasetFileDetails,
                    datasetSchemaDotOrg,
                    dataCiteXml,
                    env
                );
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Build the environment object used by mappings.
         *
         * Priority:
         *  1) -Ddataverse.siteUrl
         *  2) datasetSchemaDotOrg.includedInDataCatalog.url
         *  3) parse datasetORE["@id"] and use scheme://authority
         */
        private static ObjectNode buildEnv(ObjectMapper jsonMapper, JsonNode datasetSchemaDotOrg, JsonNode datasetORE) {
            String siteUrl = trimToNull(System.getProperty("dataverse.siteUrl"));

            if (siteUrl == null) {
                siteUrl = trimToNull(extractSchemaOrgCatalogUrl(datasetSchemaDotOrg));
            }
            if (siteUrl == null) {
                siteUrl = trimToNull(extractBaseFromOreId(datasetORE));
            }

            // Normalize siteUrl and build apiBaseUrl
            String normalizedSiteUrl = normalizeBaseUrl(siteUrl);
            String apiBaseUrl = normalizedSiteUrl.isEmpty()
                ? ""
                : ensureTrailingSlash(normalizedSiteUrl + "/api");

            ObjectNode env = jsonMapper.createObjectNode();
            env.put("siteUrl", normalizedSiteUrl);
            env.put("apiBaseUrl", apiBaseUrl);
            return env;
        }

        private static String extractSchemaOrgCatalogUrl(JsonNode datasetSchemaDotOrg) {
            if (datasetSchemaDotOrg == null || datasetSchemaDotOrg.isMissingNode() || datasetSchemaDotOrg.isNull()) {
                return null;
            }

            // Most common: includedInDataCatalog is an object with a "url" field
            JsonNode urlNode = datasetSchemaDotOrg.at("/includedInDataCatalog/url");
            if (urlNode != null && urlNode.isTextual()) {
                return urlNode.asText();
            }

            // Sometimes includedInDataCatalog itself can be a string (less common)
            JsonNode catalogNode = datasetSchemaDotOrg.get("includedInDataCatalog");
            if (catalogNode != null && catalogNode.isTextual()) {
                return catalogNode.asText();
            }

            return null;
        }

        private static String extractBaseFromOreId(JsonNode datasetORE) {
            if (datasetORE == null || datasetORE.isMissingNode() || datasetORE.isNull()) {
                return null;
            }
            JsonNode idNode = datasetORE.get("@id");
            if (idNode == null || !idNode.isTextual()) {
                return null;
            }
            String id = idNode.asText();
            try {
                URI uri = new URI(id);
                if (uri.getScheme() == null || uri.getAuthority() == null) {
                    return null;
                }
                return uri.getScheme() + "://" + uri.getAuthority();
            } catch (URISyntaxException e) {
                return null;
            }
        }

        private static String normalizeBaseUrl(String siteUrl) {
            if (siteUrl == null) {
                return "";
            }
            // remove trailing slash(es)
            String s = siteUrl.trim();
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
            return s;
        }

        private static String ensureTrailingSlash(String url) {
            if (url == null || url.isBlank()) {
                return "";
            }
            return url.endsWith("/") ? url : url + "/";
        }

        private static String trimToNull(String s) {
            if (s == null) return null;
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }
    }
}