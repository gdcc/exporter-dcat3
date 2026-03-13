package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.ExportDataProvider;
import org.junit.jupiter.api.Test;

class ExportDataTest {

    @Test
    void testMaxSetParsesCorrectly() {
        String old = System.getProperty("dataverse.siteUrl");
        try {
            System.setProperty("dataverse.siteUrl", "https://acc-dataverse.gdnnet.nl");

            ExportDataProvider exportDataProvider =
                    getExportDataProvider("src/test/resources/input/export_data_source_lightweight");

            ExportData result =
                    ExportData.builder().provider(exportDataProvider).build();

            assertThat(result).isNotNull();
            assertThat(result.datasetJson()).isNotNull();
            assertThat(result.dataCiteXml()).isNotNull();
            assertThat(result.datasetORE()).isNotNull();
            assertThat(result.datasetFileDetails()).isNotNull();
            assertThat(result.datasetSchemaDotOrg()).isNotNull();

            // New: env node checks
            assertThat(result.env()).isNotNull();
            assertThat(result.env().get("siteUrl").asText()).isEqualTo("https://acc-dataverse.gdnnet.nl");
            assertThat(result.env().get("apiBaseUrl").asText()).isEqualTo("https://acc-dataverse.gdnnet.nl/api/");
        } finally {
            restore(old);
        }
    }

    @Test
    void testSpecificSetParsesCorrectly() {
        String old = System.getProperty("dataverse.siteUrl");
        try {
            System.setProperty("dataverse.siteUrl", "http://localhost:8080");

            ExportDataProvider exportDataProvider =
                    getExportDataProvider("src/test/resources/input/export_data_source_AP_NL30");

            ExportData result =
                    ExportData.builder().provider(exportDataProvider).build();

            assertThat(result).isNotNull();
            assertThat(result.datasetJson()).isNotNull();
            assertThat(result.dataCiteXml()).isNotNull();
            assertThat(result.datasetORE()).isNotNull();
            assertThat(result.datasetFileDetails()).isNotNull();
            assertThat(result.datasetSchemaDotOrg()).isNotNull();

            // New: env node checks
            assertThat(result.env()).isNotNull();
            assertThat(result.env().get("siteUrl").asText()).isEqualTo("http://localhost:8080");
            assertThat(result.env().get("apiBaseUrl").asText()).isEqualTo("http://localhost:8080/api/");
        } finally {
            restore(old);
        }
    }

    private static void restore(String old) {
        if (old == null) {
            System.clearProperty("dataverse.siteUrl");
        } else {
            System.setProperty("dataverse.siteUrl", old);
        }
    }
}
