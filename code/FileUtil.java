package filetransferappjs;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {
    static final Path CLIENT_DIR = Paths.get("C:", "Users", "joels", "OneDrive", "Oswego", "Spring 2024", "CSC445", "project2js", "filetransferappjs", "src", "main", "java", "filetransferappjs", "clientDir");
    static final Path SERVER_DIR = Paths.get(System.getProperty("user.home"), "CSC445", "project2js", "filetransferappjs", "src", "main", "java", "filetransferappjs", "serverDir");

    // Writes a byte array content to a unique file in the server or client
    // directory based on operation.
    public static String writeFile(String fileName, byte[] content, boolean isUpload) throws IOException {
        Path dir = isUpload ? SERVER_DIR : CLIENT_DIR;
        String filePath = generateUniqueFilePath(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(content);
        }
        return filePath; // Return the path of the saved file
    }
    
    // Generates a unique file path with an incrementing number if the file already
    // exists.
    static String generateUniqueFilePath(Path dir, String originalFileName) {
        int count = 0;
        String fileName = "receivedFile_" + stripExtension(originalFileName);
        String extension = getFileExtension(originalFileName);
        Path path = dir.resolve(fileName + extension);
    
        while (Files.exists(path)) {
            count++;
            fileName = "receivedFile_" + stripExtension(originalFileName) + "_" + count;
            path = dir.resolve(fileName + extension);
        }
    
        return path.toString();
    }

    // Utility method to get the file extension.
    private static String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndexOf);
    }

    // Utility method to strip the file extension.
    private static String stripExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return fileName;
        }
        return fileName.substring(0, lastIndexOf);
    }

    // Generates a SHA-256 checksum for a file.
    public static String generateChecksum(String filePath) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(filePath)) {
            byte[] block = new byte[4096];
            int length;
            while ((length = in.read(block)) > 0) {
                digest.update(block, 0, length);
            }
        }
        byte[] encodedHash = digest.digest();
        StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
        for (byte b : encodedHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Validates the checksum of a file against the provided checksum.
    public static boolean validateChecksum(String filePath, String receivedChecksum)
            throws NoSuchAlgorithmException, IOException {
        String fileChecksum = generateChecksum(filePath);
        return fileChecksum.equals(receivedChecksum);
    }
}
