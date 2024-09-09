import java.nio.file.Path;

public class TargetPath {
    final Path sourcePath;
    final Path targetPath;

    public TargetPath(Path sourcePath, Path targetPath) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
    }
}
