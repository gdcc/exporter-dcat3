package io.gdcc.spi.export.dcat3.config.loader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileResolver {

    /**
     * @param baseDir may be null for classpath
     */
    public record ResolvedFile(InputStream in, Path baseDir) {}

    /**
     * Resolve an element file relative to the root’s directory, then cwd, then user.home, then
     * classpath.
     */
    public static InputStream resolveElementFile(Path baseDir, String fileName) throws IOException {
        return resolveFile(baseDir, fileName).in;
    }

    /**
     * Resolve file from: absolute → relative to baseDir → cwd → user.home → classpath. Returns both
     * InputStream and baseDir (null if classpath).
     */
    public static ResolvedFile resolveFile(Path baseDir, String fileName) throws IOException {
        // 0) absolute path
        Path absolute = Paths.get(fileName);
        if (Files.isRegularFile(absolute) && Files.isReadable(absolute)) {
            return new ResolvedFile(Files.newInputStream(absolute), absolute.getParent());
        }

        // 1) relative to provided baseDir
        if (baseDir != null) {
            Path relative = baseDir.resolve(fileName).normalize();
            if (Files.isRegularFile(relative) && Files.isReadable(relative)) {
                return new ResolvedFile(Files.newInputStream(relative), relative.getParent());
            }
        }

        // 2) cwd
        Path cwd = Paths.get("").toAbsolutePath().resolve(fileName).normalize();
        if (Files.isRegularFile(cwd) && Files.isReadable(cwd)) {
            return new ResolvedFile(Files.newInputStream(cwd), cwd.getParent());
        }

        // 3) user.home
        String home = System.getProperty("user.home");
        if (home != null) {
            Path homePath = Paths.get(home).resolve(fileName).normalize();
            if (Files.isRegularFile(homePath) && Files.isReadable(homePath)) {
                return new ResolvedFile(Files.newInputStream(homePath), homePath.getParent());
            }
        }

        // 4) classpath (baseDir = null)
        InputStream classPath = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
        if (classPath != null) {
            return new ResolvedFile(classPath, null);
        }
        classPath = RootConfigLoader.class.getResourceAsStream("/" + fileName);
        if (classPath != null) {
            return new ResolvedFile(classPath, null);
        }

        throw new FileNotFoundException("File not found: " + fileName);
    }
}
