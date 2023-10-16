import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebdriverUtils {
    public static void downloadWebdriver(){
        if(Files.exists(Path.of("chromedriver.exe"))){
            return;
        }
        System.out.println("Downloading the newest Chromedriver");
        String version = getLatestChromedriverVersion();
        downloadChromedriver(version);
        unzipChromedriver();
    }

    private static String getLatestChromedriverVersion(){
        String latestReleaseURL = "https://chromedriver.storage.googleapis.com/LATEST_RELEASE";

        try{
            URL url = URI.create(latestReleaseURL).toURL();

            try(Scanner scanner = new Scanner(url.openStream())){
                if(scanner.hasNext()){
                    return scanner.next();
                } else{
                    throw new RuntimeException("Failed to retrieve the ChromeDriver version.");
                }
            }

        } catch (IOException e){
            throw new RuntimeException("Error retrieving the ChromeDriver version: " + e.getMessage(), e);
        }
    }

    private static void downloadChromedriver(String version){
        String downloadURL = "https://chromedriver.storage.googleapis.com/" + version + "/chromedriver_win32.zip";
        try{
            URL url = URI.create(downloadURL).toURL();

            try(InputStream fileInputStream = url.openStream();
                OutputStream fileOutputStream = new FileOutputStream("chromedriver.zip")){
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fileInputStream.read(buffer)) != -1){
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e){
            throw new RuntimeException("Error downloading ChromeDriver: " + e.getMessage(), e);
        }
    }

    private static void unzipChromedriver(){
        String zipFilePath = "chromedriver.zip";

        try(FileInputStream fileInputStream = new FileInputStream(zipFilePath);
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            byte[] buffer = new byte[4096];
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null){
                String entryName = entry.getName();
                File entryFile = new File("./", entryName);

                if(entry.isDirectory()){
                    entryFile.mkdirs();
                } else {
                    File parent = entryFile.getParentFile();
                    if(!parent.exists()){
                        parent.mkdirs();
                    }

                    try (FileOutputStream fileOutputStream = new FileOutputStream(entryFile)){
                        int length;
                        while ((length = zipInputStream.read(buffer)) != -1){
                            fileOutputStream.write(buffer, 0, length);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error unzipping ChromeDriver: " + e.getMessage(), e);
        }

        try {
            Files.delete(Path.of(zipFilePath));
        } catch (IOException e) {
            throw new RuntimeException("Error deleting chromedriver.zip: " + e.getMessage(), e);
        }
    }
}
