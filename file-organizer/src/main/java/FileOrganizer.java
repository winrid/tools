import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FileOrganizer {
    public static void main(String[] args) throws IOException, ParseException {
        final Options options = new Options();
        options.addOption(Option.builder().longOpt("source").hasArg().desc("source dir").build());
        options.addOption(Option.builder().longOpt("target").hasArg().desc("target dir").build());
        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);
        if (!cmd.hasOption("source")) {
            throw new IllegalArgumentException("--source is required");
        }
        if (!cmd.hasOption("target")) {
            throw new IllegalArgumentException("--target is required");
        }
        final String source = cmd.getOptionValue("source");
        final String target = cmd.getOptionValue("target");
        final Path sourcePath = Path.of(source);
        final Path targetPath = Path.of(target);
        final List<Path> rawSourcePaths = new ArrayList<>(10_000);
        final long start = System.currentTimeMillis();
        Timer.timed("Walk Source Dir", () -> {
            try {
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        final String filePath = file.toString();
                        System.out.println("Found File: " + filePath);
                        if (!filePath.endsWith("DS_Store") && !filePath.endsWith("__MACOSX")) {
                            rawSourcePaths.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        final String dirPath = dir.toString();
                        System.out.println("Found Directory: " + dirPath);
                        if (dirPath.endsWith("/__MACOSX")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.printf("Found %s files %n", rawSourcePaths.size());
        Timer.timed("Sort files", () -> rawSourcePaths.sort(Comparator.comparing(Path::getFileName)));
        final Map<String, Path> hashedPathMap = new HashMap<>(rawSourcePaths.size());
        final List<Path> deDupedSourcePaths = new ArrayList<>(rawSourcePaths.size());
        Timer.timed("De-dup files", () -> {
            // just doing this sequentially on one thread is probably optimal, especially at least right now this is done
            // on big spinny bois
            for (Path path : rawSourcePaths) {
                try {
                    System.out.printf("Hashing %s...%n", path);
                    final String hash = FileHasher.md5HashFile(path.toFile());
                    final Path conflictingPath = hashedPathMap.get(hash);
                    if (conflictingPath == null) {
                        hashedPathMap.put(hash, path);
                        deDupedSourcePaths.add(path);
                    } else {
                        System.out.printf("%s de-duped by %s%n", path, conflictingPath);
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        System.out.printf("Got %s files after dedupe, from %s.%n", deDupedSourcePaths.size(), rawSourcePaths.size());
        final Set<String> targetDirs = new HashSet<>(1_000);
        final Map<String, List<TargetPath>> resultingStructure = new HashMap<>(1_000);
        Timer.timed("Determine target directory structure...", () -> {
            final var formatter = DateTimeFormatter.ofPattern("yyyy")
                    .withZone(ZoneId.systemDefault());
            for (Path deDupedFileSourcePath : deDupedSourcePaths) {
                final File file = deDupedFileSourcePath.toFile();
                try {
                    final BasicFileAttributes attr = Files.readAttributes(deDupedFileSourcePath, BasicFileAttributes.class);
                    final Instant creationTime = attr.creationTime().toInstant();
                    final String formattedCreationDate = formatter.format(creationTime);
//                    System.out.println("Creation Date: " + formattedCreationDate);
                    final String namespace = FileNameSpace.determine(file.getName());
                    final String targetDir = String.format("%s%s%s", formattedCreationDate, File.separator, namespace);
                    final Path targetFilePath = Path.of(targetPath.toAbsolutePath().toString(), targetDir, file.getName());
                    if (targetDirs.add(targetDir)) {
                        System.out.println("Will create: " + targetDir);
                        final List<TargetPath> dirPaths = new ArrayList<>(10);
                        dirPaths.add(new TargetPath(deDupedFileSourcePath, targetFilePath));
                        resultingStructure.put(targetDir, dirPaths);
                    } else {
                        resultingStructure.get(targetDir).add(new TargetPath(deDupedFileSourcePath, targetFilePath));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        System.out.printf("Will create %s dirs...%n", targetDirs.size());
        Timer.timed("Create target dirs...", () -> {
            final String targetDir = targetPath.toString();
            for (String subTargetDir : targetDirs) {
                try {
                    Files.createDirectories(Path.of(targetDir, subTargetDir));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        Timer.timed("Copy files...", () -> {
            int counter = 0;
            for (String key : resultingStructure.keySet()) {
                final List<TargetPath> targetPaths = resultingStructure.get(key);
                for (TargetPath targetFileNode : targetPaths) {
                    System.out.printf("BEGIN: %s -> %s...%n", targetFileNode.sourcePath, targetFileNode.targetPath);
                    final File sourceFile = targetFileNode.sourcePath.toFile();
                    final File targetFile = targetFileNode.targetPath.toFile();
                    try {
                        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.printf("END: %s -> %s...%n", targetFileNode.sourcePath, targetFileNode.targetPath);
                        System.out.printf("Progress: %.2f%%...%n", (counter / (double) deDupedSourcePaths.size()) * 100);
                        counter++;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        System.out.printf("Done in %sms.%n", System.currentTimeMillis() - start);
    }
}
