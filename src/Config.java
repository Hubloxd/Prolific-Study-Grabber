import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.json.JSONObject;

public class Config {
    public static JSONObject getConfig(){
        JSONObject jsonObject;
        String data;
        try(FileInputStream fileInputStream = new FileInputStream("config.json")){
            byte[] bytes = fileInputStream.readAllBytes();
            data = new String(bytes, StandardCharsets.UTF_8);
            jsonObject = new JSONObject(data);
        } catch (IOException e){
            throw new RuntimeException("Error reading config file. " + e.getMessage(), e);
        }

        return jsonObject;
    }
}
