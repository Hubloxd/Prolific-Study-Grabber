import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import org.json.JSONObject;

/**
 * The `User` class represents a user profile with email, password, and Prolific
 * ID.
 * Users can be created, loaded from a JSON file, or both.
 *
 * @param email      The user's email address.
 * @param password   The user's password.
 * @param prolificId The user's Prolific ID.
 */
public record User(String email, String password, String prolificId) {
    /**
     * Creates or loads a User instance, based on the presence of a JSON file.
     *
     * @return The User instance created or loaded.
     * @throws RuntimeException If there is an error loading or saving user data.
     */
    public static User createOrLoadUser() {
        User user;
        Path path = Paths.get("./user.json");

        if (Files.exists(path)) {
            user = loadUser();
        } else {
            user = createUser();
            try {
                user.saveToFile();
            } catch (IOException e) {
                throw new RuntimeException("Error saving user data. ", e);
            }
        }

        return user;
    }

    @Override
    public String toString() {
        return "User{" +
                "email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", prolificId='" + prolificId + '\'' +
                '}';
    }

    /**
     * Saves the User instance to a JSON file.
     *
     * @throws IOException If there is an error writing to the file.
     */
    private void saveToFile() throws IOException {
        JSONObject json = new JSONObject();
        json.put("email", email);
        String rotatedPassword = rotatePassword(password, 8, false);
        json.put("password", rotatedPassword);
        json.put("prolific_id", prolificId);

        try (PrintWriter out = new PrintWriter(new FileWriter("./user.json"))) {
            out.write(json.toString(4));
        }
    }

    /**
     * Creates a new User by taking input from the console.
     *
     * @return A new User instance created from user input.
     */
    private static User createUser() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("E-mail:\n> ");
        String email = scanner.nextLine();
        System.out.print("Password:\n> ");
        String password = scanner.nextLine();
        System.out.print("Prolific ID:\n> ");
        String prolificId = scanner.nextLine();

        return new User(email, password, prolificId);
    }

    /**
     * Loads a User from a JSON file.
     *
     * @return The User loaded from the JSON file.
     * @throws RuntimeException If there is an error loading the user data.
     */
    private static User loadUser() {
        try {
            String email, password, prolificId;
            String data = Files.readString(Path.of("./user.json"));
            JSONObject userJSON = new JSONObject(data);

            email = (String) userJSON.get("email");
            password = (String) userJSON.get("password");
            password = rotatePassword(password, 8, true);
            prolificId = (String) userJSON.get("prolific_id");

            return new User(email, password, prolificId);
        } catch (IOException e) {
            throw new RuntimeException("Error loading user data.", e);
        }
    }

    private static String rotatePassword(String password, int n, boolean reverse) {
        StringBuilder rotated = new StringBuilder();
        for (char chr : password.toCharArray()) {
            char rotatedChar;
            if (reverse) {
                rotatedChar = (char) (chr - n);
            } else {
                rotatedChar = (char) (chr + n);
            }
            rotated.append(rotatedChar);
        }

        return rotated.toString();
    }
}