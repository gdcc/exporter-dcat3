package io.gdcc.spi.export.dcat3;

import static io.gdcc.spi.export.util.TestUtil.getExportDataProvider;
import static org.assertj.core.api.Assertions.assertThat;

import io.gdcc.spi.export.ExportDataProvider;
import org.junit.jupiter.api.Test;

class ExportDataTest {

    @Test
    void testMaxSetParsesCorrectly() {

        // -- prepare
        ExportDataProvider exportDataProvider =
                getExportDataProvider("src/test/resources/input/export_data_source_lightweight");
        ExportData result = ExportData.builder().provider(exportDataProvider).build();

        // -- verify
        assertThat(result).isNotNull();
        assertThat(result.datasetJson()).isNotNull();
        assertThat(result.dataCiteXml()).isNotNull();
        assertThat(result.datasetORE()).isNotNull();
        assertThat(result.datasetFileDetails()).isNotNull();
        assertThat(result.datasetSchemaDotOrg()).isNotNull();
    }

    @Test
    void testSpecificSetParsesCorrectly() {

        // -- prepare
        ExportDataProvider exportDataProvider =
                getExportDataProvider("src/test/resources/input/export_data_source_AP_NL30");
        ExportData result = ExportData.builder().provider(exportDataProvider).build();

        // -- verify
        assertThat(result).isNotNull();
        assertThat(result.datasetJson()).isNotNull();
        assertThat(result.dataCiteXml()).isNotNull();
        assertThat(result.datasetORE()).isNotNull();
        assertThat(result.datasetFileDetails()).isNotNull();
        assertThat(result.datasetSchemaDotOrg()).isNotNull();
    }
}
