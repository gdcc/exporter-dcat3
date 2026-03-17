package io.gdcc.spi.export.util;

import io.gdcc.spi.export.ExportDataProvider;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.ValidationReport;

public class TestUtil {

    public static ExportDataProvider getExportDataProvider(String resourceDir) {
        return new ExportDataProvider() {
            @Override
            public JsonObject getDatasetJson() {
                String pathToJsonFile = resourceDir + "/datasetJson.json";
                try (JsonReader jsonReader = Json.createReader(new FileReader(pathToJsonFile))) {
                    return jsonReader.readObject();
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public JsonObject getDatasetORE() {
                String pathToJsonFile = resourceDir + "/datasetORE.json";
                try (JsonReader jsonReader = Json.createReader(new FileReader(pathToJsonFile))) {
                    return jsonReader.readObject();
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public JsonArray getDatasetFileDetails() {
                String pathToJsonFile = resourceDir + "/datasetFileDetails.json";
                try (JsonReader jsonReader = Json.createReader(new FileReader(pathToJsonFile))) {
                    return jsonReader.readArray();
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public JsonObject getDatasetSchemaDotOrg() {
                String pathToJsonFile = resourceDir + "/datasetSchemaDotOrg.json";
                try (JsonReader jsonReader = Json.createReader(new FileReader(pathToJsonFile))) {
                    return jsonReader.readObject();
                } catch (FileNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public String getDataCiteXml() {
                try {
                    return Files.readString(Paths.get(resourceDir + "/dataCiteXml.xml"), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    return null;
                }
            }
        };
    }

    public static Model readModel(byte[] rdf, Lang lang) {
        Model model = ModelFactory.createDefaultModel();
        RDFParser.create().source(new ByteArrayInputStream(rdf)).lang(lang).parse(model.getGraph());
        return model;
    }

    /** Fetch and combine multiple SHACL shape files (Turtle). */
    public static Model fetchShapesModel(List<String> urls) throws Exception {
        Model shapes = ModelFactory.createDefaultModel();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        for (String u : urls) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/turtle,application/x-turtle;q=0.9,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try (var in = new ByteArrayInputStream(resp.body())) {
                    shapes.read(in, null, "TURTLE");
                }
            } else {
                throw new IllegalStateException(
                        "Failed to fetch SHACL from " + u + " (HTTP " + resp.statusCode() + ")");
            }
        }
        return shapes;
    }

    /** Quick connectivity probe to avoid flaky CI. */
    public static boolean looksOnline() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://semiceu.github.io/DCAT-AP/releases/3.0.0/"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public static String toValidationReport(ValidationReport report) {
        var sb = new StringBuilder("DCAT/DCAT-AP-NL 3.0 SHACL validation report\n");
        report.getEntries().forEach(entry -> sb.append(" - focusNode: ")
                .append(entry.focusNode())
                .append('\n')
                .append("   path: ")
                .append(entry.resultPath())
                .append('\n')
                .append("   message: ")
                .append(entry.message())
                .append('\n')
                .append("   severity: ")
                .append(entry.severity())
                .append('\n'));
        return sb.toString();
    }

    public static void copyDirectory(Path srcDir, Path dstDir) throws IOException {
        try (Stream<Path> stream = Files.walk(srcDir)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path dst = dstDir.resolve(srcDir.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static Properties loadProps(Path file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p;
    }

    public static void storeProps(Path file, Properties p, String header) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, header);
        }
    }

    public static void removeByPrefix(Properties p, String... prefixes) {
        for (String key : p.stringPropertyNames().toArray(new String[0])) {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    p.remove(key);
                    break;
                }
            }
        }
    }
}
