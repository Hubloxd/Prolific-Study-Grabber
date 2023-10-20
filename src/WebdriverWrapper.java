import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v118.network.Network;
import org.openqa.selenium.devtools.v118.network.model.Request;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WebdriverWrapper {
    public final WebDriver driver;
    private final ArrayList<Request> requests;
    private DevTools devTools;

    public WebdriverWrapper(JSONObject config) {
        this.driver = WebdriverUtils.initializeChromedriver(config.optString("proxy", ""));
        this.requests = new ArrayList<>();
        setupDevTools();
    }

    private void setupDevTools() {
        this.devTools = ((HasDevTools) driver).getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        devTools.addListener(Network.requestWillBeSent(), requestWillBeSent -> requests.add(requestWillBeSent.getRequest()));
    }

    public void login(User user) {
        if (Files.exists(Path.of("cookies.json"))) {
            try {
                loginWithCookies();
            } catch (IOException e) {
                deleteCookies();
                loginNormally(user);
                throw new RuntimeException("Error while loading cookies from file. " + e.getMessage(), e);
            }
        } else {
            loginNormally(user);
        }
    }

    /**
     * Retrieves the Prolific Client ID from the list of HTTP requests.
     * This method searches for a Prolific request with a URL starting with
     * "<a href="https://internal-api.prolific.com/openid/authorize">https://internal-api.prolific.com/openid/authorize</a>" and extracts the client ID
     * from its query parameters.
     *
     * @return The Prolific Client ID if found.
     * @throws RuntimeException if not found.
     */
    public String grabProlificClientId() {
        Optional<Request> prolificRequest = requests.stream()
                .filter(request -> request.getUrl().startsWith("https://internal-api.prolific.com/openid/authorize"))
                .findFirst();

        if (prolificRequest.isPresent()) {
            URI uri = URI.create(prolificRequest.get().getUrl());
            String query = uri.getQuery();

            if (query != null) {
                String[] queryParams = query.split("&");
                for (String param : queryParams) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "client_id".equals(keyValue[0])) {
                        return keyValue[1];
                    }
                }
            }
        }

        throw new RuntimeException("Error grabbing Prolific User ID");
    }


    public String grabAuthToken() {
        Optional<Request> prolificRequest = requests.stream()
                .filter(request -> request.getHeaders().containsKey("Authorization"))
                .findAny();
        if (prolificRequest.isPresent()) {
            devTools.clearListeners();
            requests.clear();
            return (String) prolificRequest.get().getHeaders().get("Authorization");
        }
        throw new RuntimeException("Error grabbing authorization token");
    }

    private void loginWithCookies() throws IOException {
        driver.get("https://internal-api.prolific.com/auth/accounts/login/");
        loadCookies();

        driver.navigate().refresh();
        wait(5000);
        if (!driver.getCurrentUrl().startsWith("https://app.prolific.com/")) {
            throw new RuntimeException("Failed logging in with cookies");
        }
    }

    private void loginNormally(User user) {
        driver.get("https://internal-api.prolific.com/auth/accounts/login/");
        driver.findElement(By.id("id_username")).sendKeys(user.getEmail());
        driver.findElement(By.name("password")).sendKeys(user.getPassword());
        driver.findElement(By.id("login")).submit();

        wait(3000);
        handleConsentPopup();
        dumpCookies();
    }

    private void wait(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Handle or log the exception
        }
    }

    private void handleConsentPopup() {
        try {
            driver.findElement(By.xpath("//*[@id=\"klaro\"]/div/div/div[2]/div/div/div/button[1]")).click();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            // Handle or log the exception
        }
    }

    private void dumpCookies() {
        driver.get("https://internal-api.prolific.com");
        Set<Cookie> cookies = driver.manage().getCookies();
        JSONArray jsonArray = new JSONArray(cookies);
        try (PrintWriter out = new PrintWriter(new FileWriter("cookies.json"))) {
            out.write(jsonArray.toString(4));
        } catch (IOException e) {
            throw new RuntimeException("Error dumping cookies to file. " + e.getMessage(), e);
        }
        driver.get("https://app.prolific.com/studies");
    }

    private void loadCookies() throws IOException {
        String data;
        try (FileInputStream fileInputStream = new FileInputStream("cookies.json")) {
            byte[] bytes = fileInputStream.readAllBytes();
            data = new String(bytes, StandardCharsets.UTF_8);
        }
        JSONArray jsonArray = new JSONArray(data);
        for (Object jsonObject : jsonArray) {
            Cookie cookie = deserializeJSONCookie((JSONObject) jsonObject);
            driver.manage().addCookie(cookie);
        }
    }

    private Cookie deserializeJSONCookie(JSONObject json) {
        return new Cookie.Builder(json.getString("name"), json.getString("value"))
                .domain(json.getString("domain"))
                .path(json.getString("path"))
                .isSecure(json.getBoolean("secure"))
                .build();
    }

    private void deleteCookies() {
        try {
            Files.delete(Path.of("cookies.json"));
        } catch (IOException ex) {
            // Handle or log the exception
            throw new RuntimeException(ex);
        }
    }

}
