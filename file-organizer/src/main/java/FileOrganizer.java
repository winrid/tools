import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
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
        Timer.timed("Walk Source Dir", () -> {
            try {
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        System.out.println("Found File: " + file.toString());
                        rawSourcePaths.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        System.out.println("Found Directory: " + dir.toString());
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
    }
}
