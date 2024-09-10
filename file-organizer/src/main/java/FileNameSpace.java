import java.util.HashMap;
import java.util.Map;

public class FileNameSpace {
    private static Map<String, String> fileCategories;

    public static String determine(String fileName) {
        if (fileCategories == null) {
            fileCategories = new HashMap<>();
            fileCategories.put("txt", "text");
            fileCategories.put("doc", "text");
            fileCategories.put("docx", "text");
            fileCategories.put("pdf", "text");
            fileCategories.put("md", "text");
            fileCategories.put("rtf", "text");

            // Images
            fileCategories.put("jpg", "images");
            fileCategories.put("jpeg", "images");
            fileCategories.put("png", "images");
            fileCategories.put("gif", "images");
            fileCategories.put("bmp", "images");
            fileCategories.put("svg", "images");
            fileCategories.put("psd", "images");
            fileCategories.put("xd", "images");
            fileCategories.put("ai", "images");
            fileCategories.put("eps", "images");
            fileCategories.put("webp", "images");

            // Videos
            fileCategories.put("mp4", "videos");
            fileCategories.put("avi", "videos");
            fileCategories.put("mov", "videos");
            fileCategories.put("mkv", "videos");
            fileCategories.put("webm", "videos");

            // Audio
            fileCategories.put("mp3", "audio");
            fileCategories.put("wav", "audio");
            fileCategories.put("flac", "audio");
            fileCategories.put("aac", "audio");

            // Executables
            fileCategories.put("exe", "executables");
            fileCategories.put("bat", "executables");
            fileCategories.put("sh", "executables");
            fileCategories.put("jar", "executables");

            // Compressed Files
            fileCategories.put("zip", "compressed");
            fileCategories.put("rar", "compressed");
            fileCategories.put("tar", "compressed");
            fileCategories.put("gz", "compressed");

            // Others
            fileCategories.put("iso", "disks");
            fileCategories.put("dll", "system-files");
            fileCategories.put("json", "json");
        }
        String fileExtension = "";
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(dotIndex + 1);
            final String mapped = fileCategories.get(fileExtension);
            if (mapped == null) {
                System.err.printf("No mapping for %s...%n", fileExtension);
                return fileExtension;
            }
            return mapped;
        }
        return "no-extension";
    }
}
