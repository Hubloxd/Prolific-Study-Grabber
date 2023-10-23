import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The WebdriverUtils class provides utility methods for downloading and setting up the ChromeDriver executable for Selenium WebDriver.
 * This class supports downloading and extracting the appropriate ChromeDriver version based on the user's operating system.
 */
public class WebdriverUtils {
    private static final String chromedriverPathString = "./chromedriver-win64/chromedriver.exe";

    /**
     * Checks if the ChromeDriver executable already exists in the current directory.
     * If not, it proceeds to download and unzip the latest version.
     */
    public static void downloadWebdriver() {
        if (Files.exists(Path.of(chromedriverPathString))) {
            return;
        }
        System.out.println("Downloading the newest Chromedriver");
        String version = getLatestChromedriverVersion();
        downloadChromedriver(version);
        unzipChromedriver();
    }

    public static WebDriver initializeChromedriver(String proxyAddress) {
        String chromedriverPath = new File(chromedriverPathString).getAbsolutePath();
        System.setProperty("webdriver.chrome.driver", chromedriverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*", "--start-maximized");

        if (!proxyAddress.isEmpty()) {
            Proxy p = new Proxy();
            p.setSslProxy(proxyAddress);
            options.setProxy(p);
//            options.addArguments("--proxy-server=" + proxyAddress);
        }

        WebDriver driver = new ChromeDriver(options);
        Runtime.getRuntime().addShutdownHook(new Thread(driver::quit));

        return driver;
    }

    /**
     * Retrieves the latest version of ChromeDriver available for download from the ChromeDriver repository.
     *
     * @return The latest ChromeDriver version as a string.
     * @throws RuntimeException if unable to fetch the version or if an error occurs during the process.
     */
    private static String getLatestChromedriverVersion() {
        String latestReleaseURL = "https://googlechromelabs.github.io/chrome-for-testing/LATEST_RELEASE_STABLE";

        try {
            URL url = URI.create(latestReleaseURL).toURL();

            try (Scanner scanner = new Scanner(url.openStream())) {
                if (scanner.hasNext()) {
                    return scanner.next();
                } else {
                    throw new RuntimeException("Failed to retrieve the ChromeDriver version.");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Error retrieving the ChromeDriver version: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads the ChromeDriver executable zip file for the specified version.
     *
     * @param version The version of ChromeDriver to download.
     * @throws RuntimeException if an error occurs during the download process.
     */
    private static void downloadChromedriver(String version) {
        String downloadURL = "https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/" + version + "/win64/chromedriver-win64.zip";
        try {
            URL url = URI.create(downloadURL).toURL();

            try (InputStream fileInputStream = url.openStream();
                 OutputStream fileOutputStream = new FileOutputStream("chromedriver.zip")) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error downloading ChromeDriver: " + e.getMessage(), e);
        }
    }

    /**
     * Unzips the downloaded ChromeDriver zip file and places the executable in the current directory.
     *
     * @throws RuntimeException if an error occurs while unzipping the ChromeDriver file.
     */
    private static void unzipChromedriver() {
        String zipFilePath = "chromedriver.zip";
        try (FileInputStream fileInputStream = new FileInputStream(zipFilePath);
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            byte[] buffer = new byte[4096];
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                File entryFile = new File("./", entryName);

                if (entry.isDirectory()) {
                    System.out.print(entryFile.mkdirs());
                } else {
                    File parent = entryFile.getParentFile();
                    if (!parent.exists()) {
                        System.out.print(parent.mkdirs());
                    }

                    try (FileOutputStream fileOutputStream = new FileOutputStream(entryFile)) {
                        int length;
                        while ((length = zipInputStream.read(buffer)) != -1) {
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
