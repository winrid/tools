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
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        final String filePath = file.toString();
                        System.out.println("Found File: " + filePath);
                        if (!filePath.endsWith("DS_Store") && !filePath.endsWith("__MACOSX") && !filePath.endsWith(CachedTargetStructure.CACHED_EXT)) {
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
        final CachedTargetStructure cachedTargetStructure = CachedTargetStructure.get(sourcePath, targetPath, rawSourcePaths);
        System.out.printf("Got %s files after dedupe, from %s.%n", cachedTargetStructure.deDupedSourcePaths.size(), rawSourcePaths.size());
        System.out.printf("Will create %s dirs...%n", cachedTargetStructure.targetDirs.size());
        Timer.timed("Create target dirs...", () -> {
            final String targetDir = targetPath.toString();
            for (String subTargetDir : cachedTargetStructure.targetDirs) {
                try {
                    Files.createDirectories(Path.of(targetDir, subTargetDir));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        Timer.timed("Copy files...", () -> {
            int counter = 0;
            for (String key : cachedTargetStructure.resultingStructure.keySet()) {
                final List<TargetPath> targetPaths = cachedTargetStructure.resultingStructure.get(key);
                for (TargetPath targetFileNode : targetPaths) {
                    System.out.printf("BEGIN: %s -> %s...%n", targetFileNode.sourcePath, targetFileNode.targetPath);
                    final File sourceFile = targetFileNode.sourcePath.toFile();
                    final File targetFile = targetFileNode.targetPath.toFile();
                    try {
                        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        System.out.printf("END: %s -> %s...%n", targetFileNode.sourcePath, targetFileNode.targetPath);
                        System.out.printf("Progress: %.2f%%...%n", (counter / (double) cachedTargetStructure.deDupedSourcePaths.size()) * 100);
                        counter++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        System.out.printf("Done in %sms.%n", System.currentTimeMillis() - start);
    }
}
