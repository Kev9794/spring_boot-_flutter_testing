package com.example.demo.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Controller
public class MainController {

    private final ExecutorService executorService = Executors.newFixedThreadPool(5); // Sesuaikan jumlah thread sesuai
                                                                                     // kebutuhan

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestParam("url") String fileURL,
            @RequestParam("flutterid") String flutterid) {
        Map<String, Object> response = new HashMap<>();
        try {
            Future<Map<String, Object>> futureResponse = executorService.submit(new TestTask(fileURL, flutterid));
            response = futureResponse.get(); // Tunggu hingga tugas selesai
        } catch (InterruptedException | ExecutionException e) {
            response.put("message", "An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*"); // Sesuaikan dengan domain yang benar
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Max-Age", "3600");
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    private class TestTask implements Callable<Map<String, Object>> {
        private final String fileURL;
        private final String flutterid;

        public TestTask(String fileURL, String flutterid) {
            this.fileURL = fileURL;
            this.flutterid = flutterid;
        }

        @Override
        public Map<String, Object> call() {
            Map<String, Object> response = new HashMap<>();
            String repoName = extractRepoName(fileURL);
            String destDirectory = "C:\\Users\\kevin\\Desktop\\Materi kuliah\\SKRIPSI\\unzipped";
            String zipFilePath = destDirectory + "\\downloaded.zip";
            String folderToCopy = "C:\\Users\\kevin\\Desktop\\Materi kuliah\\SKRIPSI\\testFile\\" + flutterid
                    + "\\test";
            String destinationDir = destDirectory + "\\" + repoName + "-main\\test";

            System.out.println("Received URL: " + fileURL);

            try {
                // Clear destination directory and previous zip file before download and unzip
                clearDirectory(new File(destDirectory));
                Files.deleteIfExists(Paths.get(zipFilePath));
                // Download and unzip the new file
                downloadFile(fileURL, zipFilePath);
                unzip(zipFilePath, destDirectory);
                copyDirectory(new File(folderToCopy), new File(destinationDir));
                // Run Flutter tests
                String testOutput = runFlutterTest(destDirectory + "\\" + repoName + "-main");
                List<String> successTests = new ArrayList<>();
                List<String> failedTests = new ArrayList<>();
                parseTestOutput(testOutput, successTests, failedTests);
                int totalSuccess = successTests.size();
                int totalFailed = failedTests.size();
                // Calculate score
                int totalTests = totalSuccess + totalFailed;
                double score = totalTests == 0 ? 0 : ((double) totalSuccess / totalTests) * 100;
                DecimalFormat df = new DecimalFormat("#.##");
                String formattedScore = df.format(score);
                // Prepare response
                response.put("message", "Download, unzip, and test completed successfully.");
                response.put("successTests", successTests);
                response.put("failedTests", failedTests);
                response.put("totalSuccess", totalSuccess);
                response.put("totalFailed", totalFailed);
                response.put("score", formattedScore);
            } catch (Exception ex) {
                response.put("message", "An error occurred: " + ex.getMessage());
                ex.printStackTrace();
            }
            return response;
        }
    }

    private String extractRepoName(String url) {
        // Contoh URL:
        // https://github.com/Kev9794/media_player_flutter_failed/archive/refs/heads/main.zip
        String pattern = "github\\.com/(.+?)/(\\w+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(url);

        if (m.find()) {
            String repoName = m.group(2);
            return repoName;
        } else {
            throw new IllegalArgumentException("Invalid GitHub URL format: " + url);
        }
    }

    private static void downloadFile(String fileURL, String destination) throws IOException {
        System.out.println("Downloading from URL: " + fileURL);
        URL url = new URL(fileURL);
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        FileOutputStream fileOutputStream = new FileOutputStream(destination);
        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        fileOutputStream.close();
        readableByteChannel.close();
        System.out.println("File downloaded to " + destination);
    }

    private static void clearDirectory(File directory) throws IOException {
        if (directory.exists()) {
            for (File file : directory.listFiles()) {
                if (file.isDirectory()) {
                    clearDirectory(file);
                }
                if (!file.delete()) {
                    throw new IOException("Failed to delete " + file);
                }
            }
        } else {
            directory.mkdirs();
        }
        System.out.println("Directory " + directory.getPath() + " is cleared.");
    }

    private static void unzip(String zipFilePath, String destDirectory) throws IOException {
        System.out.println("Unzipping file " + zipFilePath + " to " + destDirectory);
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
        System.out.println("Unzip completed.");
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static void copyDirectory(File sourceDir, File destinationDir) throws IOException {
        if (!sourceDir.isDirectory()) {
            throw new IOException("Source is not a directory");
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        for (String file : sourceDir.list()) {
            File srcFile = new File(sourceDir, file);
            File destFile = new File(destinationDir, file);

            if (srcFile.isDirectory()) {
                copyDirectory(srcFile, destFile);
            } else {
                Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        System.out.println("Directory " + sourceDir.getName() + " copied to " + destinationDir.getPath());
    }

    private static String runFlutterTest(String projectDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe", "/c",
                "cd \"" + projectDir + "\" && flutter test");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    private static void parseTestOutput(String output, List<String> successTests,
            List<String> failedTests) {
        String[] lines = output.split("\n");
        Pattern successPattern = Pattern.compile("\\+\\d+(?: -\\d+)?: .*: (.*)(?<! \\[E\\])$");
        Pattern failedPattern = Pattern.compile("[\\+\\-]\\d+(?: -\\d+)?: .*: (.*) \\[E\\]");

        Set<String> failedTestsSet = new HashSet<>(failedTests);

        for (String line : lines) {
            System.out.println("Processing line: " + line); // Debugging line
            Matcher successMatcher = successPattern.matcher(line);
            Matcher failedMatcher = failedPattern.matcher(line);

            if (successMatcher.find()) {
                String testName = successMatcher.group(1).trim();
                successTests.add(testName);
                System.out.println("Success test found: " + testName); // Debugging line
            } else if (failedMatcher.find()) {
                String testName = failedMatcher.group(1).trim();
                failedTestsSet.add(testName);
                failedTests.add(testName + " (failed)");
                System.out.println("Failed test found: " + testName); // Debugging line
            }
        }
        successTests.removeIf(failedTestsSet::contains);

    }

}
