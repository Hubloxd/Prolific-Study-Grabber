import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides methods to interact with the Prolific API, such as
 * fetching studies and reserving studies.
 * It uses the OkHttp library for making HTTP requests and the JSON library for
 * handling JSON data.
 */
public class ProlificAPI {
    public record APIResult<T>(T data, int status) {
    }

    public record Study(String id, String name, double reward) {
        public static Study fromJSON(JSONObject jsonObject) {
            String id = jsonObject.getString("id");
            String name = jsonObject.getString("name");
            double reward = jsonObject.getDouble("reward");
            return new Study(id, name, reward);
        }
    }

    private static String authToken;
    private static Proxy proxy;

    /**
     * Set the authentication token for Prolific API requests.
     *
     * @param authToken The authentication token to set.
     */
    public static void setAuthToken(String authToken) {
        ProlificAPI.authToken = authToken;
    }

    /**
     * Set a proxy for making HTTP requests. (Optional)
     *
     * @param proxy The proxy to set. Pass null if not using a proxy.
     */
    public static void setProxy(Proxy proxy) {
        ProlificAPI.proxy = proxy;
        System.out.println("Set proxy to " + proxy);
    }

    public static void renewAuthToken(String clientId) {
//        System.out.println("Renewing authorization token.");

        Map<String, String> cookies = fetchCookies();
        String sp = cookies.get("sp");
        String csrftoken = cookies.get("csrftoken");
        String sessionid = cookies.get("sessionid");

        Map<String, String> params = Map.of(
                "client_id", clientId,
                "redirect_uri", "https://app.prolific.com/silent-renew.html",
                "response_type", "id_token%20token",
                "scope", "openid%20profile",
                "nonce", "5d50ff9351a847daad7a92ec37ecd224",
                "prompt", "none",
                "returnPath", "/");

        String baseUrl = "https://internal-api.prolific.com/openid/authorize";
        String queryParams = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((p1, p2) -> p1 + "&" + p2)
                .orElse("");

        String url = baseUrl + "?" + queryParams;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Sec-Ch-Ua-Mobile", "?0")
                .addHeader("Sec-Ch-Ua-Platform", "\"\"")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.111 Safari/537.36")
                .addHeader("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("Sec-Fetch-Site", "same-site")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Dest", "iframe")
                .addHeader("Referer", "https://app.prolific.com/")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Cookie", String.format("sessionid=%s; csrftoken=%s; sp=%s", sessionid, csrftoken, sp))
                .build();

        String locationHeader;
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.setFollowRedirects$okhttp(false);
        OkHttpClient httpClient = builder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            locationHeader = response.header("location");
            assert locationHeader != null;

        } catch (IOException e) {
            throw new RuntimeException("Error calling Prolific authorize API. " + e.getMessage(), e);
        }

        String query = locationHeader.split("#", 2)[1];
        Map<String, String> queryMap = new HashMap<>();
        String[] queries = query.split("&");
        for (String q : queries) {
            String[] split = q.split("=", 2);
            queryMap.put(split[0], split[1]);
        }

        String accessToken = queryMap.get("access_token");

        setAuthToken("Bearer " + accessToken);
//        System.out.println("Renewed auth token: " + authToken);
    }

    /**
     * Fetch a list of studies from the Prolific API.
     *
     * @return An APIResult containing the list of studies and the HTTP status code.
     */
    public static APIResult<List<Study>> fetchStudies() {
        try {
            APIResult<JSONObject> apiResult = callStudyAPI();
            JSONArray jsonArray = apiResult.data.optJSONArray("results");
            List<Study> studies = new ArrayList<>();

            if (jsonArray != null) {
                for (Object o : jsonArray) {
                    studies.add(Study.fromJSON((JSONObject) o));
                }
            }

            return new APIResult<>(studies, apiResult.status);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error fetching studies from Prolific API. " + e.getMessage(), e);
        }
    }

    /**
     * Makes an HTTP GET request to fetch studies from the Prolific API.
     *
     * @return An APIResult containing a JSONObject with the response data.
     * @throws IOException If there is an issue with the HTTP request or response.
     */
    private static APIResult<JSONObject> callStudyAPI() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url("https://internal-api.prolific.com/api/v1/participant/studies")
                .get()
                .addHeader("authority", "internal-api.prolific.com")
                .addHeader("accept", "application/json, text/plain, */*")
                .addHeader("accept-language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("authorization", authToken)
                .addHeader("cache-control", "no-cache")
                .addHeader("dnt", "1")
                .addHeader("origin", "https://app.prolific.com")
                .addHeader("pragma", "no-cache")
                .addHeader("referer", "https://app.prolific.com/")
                .addHeader("sec-fetch-dest", "empty")
                .addHeader("sec-fetch-mode", "cors")
                .addHeader("sec-fetch-site", "same-site")
                .addHeader("user-agent",
                        "Mozilla/5.0 (Linux; Android 8.0.0; SM-G965F Build/R16NW) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
                .build();

        if (proxy != null) {
            return getJsonObjectAPIResult(request, proxy);
        }
        return getJsonObjectAPIResult(request);
    }

    /**
     * Reserves a study for a participant.
     *
     * @param studyId       The ID of the study to be reserved.
     * @param participantId The ID of the participant reserving the study.
     * @return An APIResult containing a JSONObject with the response data.
     */
    public static APIResult<JSONObject> reserveStudy(String studyId, String participantId) {
        try {
            return callReserveAPI(studyId, participantId);
        } catch (IOException e) {
            throw new RuntimeException("Error calling Prolific reserve API. " + e.getMessage(), e);
        }
    }

    /**
     * Calls the Prolific API to reserve a study.
     *
     * @param studyId       The ID of the study to be reserved.
     * @param participantId The ID of the participant reserving the study.
     * @return An APIResult containing a JSONObject with the response data.
     * @throws IOException If there is an issue with the HTTP request or response.
     */
    private static APIResult<JSONObject> callReserveAPI(String studyId, String participantId) throws IOException {
        String jsonBody = String.format("{\"study_id\":\"%s\",\"participant_id\":\"%s\"}", studyId, participantId);
//        System.out.println("Reserve request body: " + jsonBody);
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(jsonBody, mediaType);

        Request request = new Request.Builder()
                .url("https://internal-api.prolific.com/api/v1/submissions/reserve/")
                .post(body)
                .addHeader("Sec-Ch-Ua-Mobile", "?0")
                .addHeader("X-Datadog-Origin", "rum")
                .addHeader("Authorization", authToken)
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.111 Safari/537.36")
                .addHeader("X-Datadog-Sampling-Priority", "1")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Sec-Ch-Ua-Platform", "\"\"")
                .addHeader("Origin", "https://app.prolific.com")
                .addHeader("Sec-Fetch-Site", "same-site")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Referer", "https://app.prolific.com/")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build();

        if (proxy != null) {
            return getJsonObjectAPIResult(request, proxy);
        }
        return getJsonObjectAPIResult(request);
    }

    /**
     * Makes an HTTP GET request and returns the response as a JSONObject.
     *
     * @param request The HTTP request to be executed.
     * @return An APIResult containing a JSONObject with the response data.
     * @throws IOException If there is an issue with the HTTP request or response.
     */
    private static APIResult<JSONObject> getJsonObjectAPIResult(Request request) throws IOException {
        OkHttpClient httpClient = new OkHttpClient();

        return executeCall(request, httpClient);
    }

    /**
     * Makes an HTTP GET request with a proxy and returns the response as a
     * JSONObject.
     *
     * @param request The HTTP request to be executed.
     * @param proxy   The proxy configuration to use.
     * @return An APIResult containing a JSONObject with the response data.
     * @throws IOException If there is an issue with the HTTP request or response.
     */
    private static APIResult<JSONObject> getJsonObjectAPIResult(Request request, Proxy proxy) throws IOException {
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        builder.setProxy$okhttp(proxy);
        OkHttpClient httpClient = builder.build();
        return executeCall(request, httpClient);
    }

    /**
     * Executes an HTTP request and processes the response.
     *
     * @param request    The HTTP request to be executed.
     * @param httpClient The OkHttpClient to use for the request.
     * @return An APIResult containing a JSONObject with the response data.
     * @throws IOException If there is an issue with the HTTP request or response.
     */
    private static APIResult<JSONObject> executeCall(Request request, OkHttpClient httpClient) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            assert response.body() != null;
            String responseBody = response.body().string();

            try {
//                System.out.println("Response body: " + responseBody);
                JSONObject jsonObject = new JSONObject(responseBody);
                return new APIResult<>(jsonObject, response.code());
            } catch (JSONException e) {
                throw new RemoteException("Response: " + responseBody + " is not a valid JSON. " + e.getMessage(), e);
            }
        }
    }

    /**
     * Fetches cookies from a JSON file and returns them as a map of name-value
     * pairs.
     *
     * @return A map containing cookies with names and values.
     */
    private static Map<String, String> fetchCookies() {
        Map<String, String> cookies = new HashMap<>();

        String data;
        try (FileInputStream fileInputStream = new FileInputStream("cookies.json")) {
            byte[] bytes = fileInputStream.readAllBytes();
            data = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Object o : new JSONArray(data)) {
            JSONObject jsonObject = (JSONObject) o;

            cookies.put(jsonObject.getString("name"), jsonObject.getString("value"));
        }

        return cookies;
    }
}
