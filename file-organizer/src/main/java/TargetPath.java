import java.io.Serializable;
import java.nio.file.Path;

public class TargetPath implements Serializable {
    private static final long serialVersionUID = 1L;
    final Path sourcePath;
    final Path targetPath;

    public TargetPath(Path sourcePath, Path targetPath) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
    }
}
