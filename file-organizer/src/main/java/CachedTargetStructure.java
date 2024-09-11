import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CachedTargetStructure implements Serializable {
    private static final long serialVersionUID = 1L;
    final Map<String, String> hashedPathMap = new HashMap<>();
    final List<String> deDupedSourcePaths = new ArrayList<>();
    final Set<String> checkedPaths = new HashSet<>();
    final Set<String> targetDirs = new HashSet<>();
    final Map<String, List<TargetPath>> resultingStructure = new HashMap<>();
    final static String CACHED_EXT = ".file-organizer-data";

    CachedTargetStructure() {
    }

    /**
     * @param sourcePath     - source directory. where the cached file is/will go
     * @param targetPath     - target directory
     * @param rawSourcePaths - should be sorted
     */
    static CachedTargetStructure get(Path sourcePath, Path targetPath, List<Path> rawSourcePaths) {
        try {
            System.out.println("Hashing source paths to determine cached structure file name...");
            final String md5Hash = md5Hash(rawSourcePaths);
            final Path cachePath = sourcePath.resolve(md5Hash + CACHED_EXT);

            if (Files.exists(cachePath)) {
                System.out.println("Reading cached target structure from disk...");
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cachePath.toFile()))) {
                    return (CachedTargetStructure) ois.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException("Failed to load from disk", e);
                }
            } else {
                System.out.println("No cached target structure on disk, creating...");
            }

            CachedTargetStructure cachedTargetStructure = new CachedTargetStructure();
            cachedTargetStructure.determine(cachePath, targetPath, rawSourcePaths);
            cachedTargetStructure.saveToDisk(cachePath);
            return cachedTargetStructure;

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String md5Hash(List<Path> rawSourcePaths) throws NoSuchAlgorithmException, IOException {
        final MessageDigest md = MessageDigest.getInstance("MD5");
        for (Path path : rawSourcePaths) {
            md.update(path.toString().getBytes());
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    void saveToDisk(Path cachePath) {
        Timer.timed("CachedTargetStructure.saveToDisk", () -> {
            try {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cachePath.toFile()))) {
                    oos.writeObject(this);
                    System.out.printf("Structure saved to %s%n", cachePath);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to save to disk", e);
            }
        });
    }

    void determine(Path cachePath, Path targetPath, List<Path> rawSourcePaths) {
        Timer.timed("De-dup files", () -> {
            // just doing this sequentially on one thread is probably optimal, especially at least right now this is done
            // on big spinny bois
            int i = 0;
            for (Path path : rawSourcePaths) {
                try {
                    final String pathAsString = path.toString();
                    if (checkedPaths.add(pathAsString)) {
                        System.out.printf("Hashing %s...%n", path);
                        final String hash = FileHasher.md5HashFile(path.toFile());
                        final String conflictingPath = hashedPathMap.get(hash);
                        if (conflictingPath == null) {
                            hashedPathMap.put(hash, pathAsString);
                            deDupedSourcePaths.add(pathAsString);
                        } else {
                            System.out.printf("%s de-duped by %s%n", path, conflictingPath);
                        }
                        if (i % 10 == 0) {
                            saveToDisk(cachePath);
                        }
                        i++;
                    } else {
                        System.out.printf("Already checked for duplicates %s...%n", path);
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Timer.timed("Determine target directory structure...", () -> {
            final var formatter = DateTimeFormatter.ofPattern("yyyy")
                    .withZone(ZoneId.systemDefault());
            for (String deDupedFileSourcePathString : deDupedSourcePaths) {
                final Path deDupedFileSourcePath = Path.of(deDupedFileSourcePathString);
                final File file = deDupedFileSourcePath.toFile();
                try {
                    final BasicFileAttributes attr = Files.readAttributes(deDupedFileSourcePath, BasicFileAttributes.class);
                    final Instant creationTime = attr.creationTime().toInstant();
                    final String formattedCreationDate = formatter.format(creationTime);
                    final String namespace = FileNameSpace.determine(file.getName());
                    final String targetDir = String.format("%s%s%s", formattedCreationDate, File.separator, namespace);
                    final Path targetFilePath = Path.of(targetPath.toAbsolutePath().toString(), targetDir, file.getName());
                    if (!targetFilePath.toFile().exists()) {
                        if (targetDirs.add(targetDir)) {
                            System.out.println("Will create: " + targetDir);
                            final List<TargetPath> dirPaths = new ArrayList<>(10);
                            dirPaths.add(new TargetPath(deDupedFileSourcePath, targetFilePath));
                            resultingStructure.put(targetDir, dirPaths);
                        } else {
                            resultingStructure.get(targetDir).add(new TargetPath(deDupedFileSourcePath, targetFilePath));
                        }
                    } else {
                        System.out.println("Already exists, ignoring: " + targetFilePath);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
