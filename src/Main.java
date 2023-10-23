import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.*;

class Main {
    private static final int SECOND = 1000, THIRD_OF_SECOND = 333;

    public static void main(String[] args) throws InterruptedException {
        JSONObject config = Config.getConfig();

        ProlificAPI.setProxy(createProxy(config));
        long interval = config.optLong("interval", 30);
        User user = User.createOrLoadUser();

        WebdriverUtils.downloadWebdriver();
        WebdriverWrapper webdriverWrapper = new WebdriverWrapper(config);

        webdriverWrapper.login(user);
        String prolificClientId = webdriverWrapper.grabProlificClientId();
        String authToken = webdriverWrapper.grabAuthToken();
        ProlificAPI.setAuthToken(authToken);
        System.out.println("Initial auth token: " + authToken);

        webdriverWrapper.driver.quit();

        while (true) {
            ProlificAPI.APIResult<List<ProlificAPI.Study>> fetchApiResult = ProlificAPI.fetchStudies();
            List<ProlificAPI.Study> studies = fetchApiResult.data();
            int statusCode = fetchApiResult.status();

            System.out.println(printCurrentTime() + ": " + studies);
            if (statusCode == 200 && !studies.isEmpty()) {
                ProlificAPI.Study study = studies.get(0);
                ProlificAPI.APIResult<JSONObject> reserveApiResult = ProlificAPI.reserveStudy(study.id(),
                        user.prolificId());
                statusCode = reserveApiResult.status();

                if (statusCode == 201) {
                    break;
                } else if (statusCode == 400) {
                    System.out.println("Study full");

                    // noinspection BusyWait
                    Thread.sleep(interval * THIRD_OF_SECOND);
                    continue;
                } else if (statusCode == 404) {
                    ProlificAPI.renewAuthToken(prolificClientId);
                    continue;
                } else {
                    throw new RuntimeException("Unknown status code received from Prolific reserve API: " + statusCode);
                }

            } else if (statusCode == 404) {
                ProlificAPI.renewAuthToken(prolificClientId);
                continue;
            } else if (statusCode != 200) {
                throw new RuntimeException("Unknown status code received from Prolific fetch API: " + statusCode);
            }

            // noinspection BusyWait
            Thread.sleep(interval * SECOND);
        }
        webdriverWrapper = new WebdriverWrapper(config);

        Audio.playSound();
        webdriverWrapper.login(user);
        pause();
        webdriverWrapper.quit();
    }

    private static void pause() {
        System.out.println("Press Enter to exit the program");

        new Scanner(System.in).nextLine();
    }

    private static String printCurrentTime() {
        Formatter format;

        Calendar gfg_calender = Calendar.getInstance();

        format = new Formatter();

        return format.format("%tl:%tM", gfg_calender, gfg_calender).toString();
    }

    private static Proxy createProxy(JSONObject config){
        String proxyString = config.getString("proxy");
        if(Objects.equals(proxyString, "")){
            return null;
        }
        String[] strings = proxyString.split(":", 2);
        String hostname = strings[0];
        int port = Integer.parseInt(strings[1]);

        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
    }
}