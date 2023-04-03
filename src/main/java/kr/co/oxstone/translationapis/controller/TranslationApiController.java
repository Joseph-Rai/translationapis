package kr.co.oxstone.translationapis.controller;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.*;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.TranslationServiceClient;
import com.google.cloud.translate.v3.TranslationServiceSettings;
import com.google.common.collect.Lists;
import io.github.kezhenxu94.chatgpt.ChatGPT;
import io.github.kezhenxu94.chatgpt.Conversation;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/")
public class TranslationApiController {

    private TranslationServiceClient client;
    private SecretManagerServiceClient secretManagerServiceClient;
    private String projectID;

    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticate(@RequestBody String json) {
        GoogleCredentials myCredentials = null;
        try {
            String keyFilePath = saveJsonToFile(json);
            //TranslationServiceClient 객체생성
            createTranslationServiceClient(keyFilePath);

            //SecretManagerServiceClient 객체생성
            createSecretManagerServiceClient(keyFilePath);

            projectID = getProjectIdFromJsonFile(keyFilePath);

            return ResponseEntity.ok("인증에 성공 했습니다.");

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증에 실패 했습니다.");
        }
    }

    private void createSecretManagerServiceClient(String keyFilePath) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(keyFilePath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        SecretManagerServiceSettings secretManagerServiceSettings =
                SecretManagerServiceSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();

        secretManagerServiceClient = SecretManagerServiceClient.create(secretManagerServiceSettings);
    }

    private void createTranslationServiceClient(String keyFilePath) throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(keyFilePath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-translation"));

        TranslationServiceSettings defaultSettings =
                TranslationServiceSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();
        client = TranslationServiceClient.create(defaultSettings);
    }

    private static String saveJsonToFile(String json) throws IOException {
        String homedir = System.getProperty("user.home");
        Path keyFile = Path.of(homedir).resolve("key.json");
        String keyFilePath = keyFile.toAbsolutePath().toString();
        FileWriter fw = new FileWriter(keyFilePath);
        fw.write(json);
        fw.flush();
        return keyFilePath;
    }

    @PostMapping("/translate")
    public ResponseEntity<byte[]> translateText(@RequestBody byte[] reqByteStream) throws IOException {
        TranslateTextRequest request = TranslateTextRequest.parseFrom(reqByteStream);
        TranslateTextResponse response = client.translateText(request);

        return ResponseEntity.ok(response.toByteArray());
    }

    @PostMapping("/chatGPT")
    public String correctByChatGPT(@RequestBody String text) throws IOException, InterruptedException {
        return getNewConversation().ask(text).content();
    }

    public ChatGPT getChatGPT() {
        String chatGptApiKey = getSecret(projectID, "CHATGPT_API_KEY");
        return ChatGPT
                .builder()
//                .dataPath(Files.createTempDirectory("chatgpt")) // Persist the chat history to a data path
                .apiKey(chatGptApiKey)
                .build();
    }

    public String getProjectIdFromJsonFile(String filePath) {
        try {
            // JSON 파일을 읽어 들입니다.
            String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

            // JSON 객체로 변환합니다.
            JSONObject jsonObject = new JSONObject(content);

            // 프로젝트 ID 속성을 가져옵니다.
            String projectNumber = jsonObject.getString("project_id");

            return projectNumber;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getSecret(String projectNumber, String secretName) {
        SecretVersionName secretVersionName = SecretVersionName.of(projectNumber, secretName, "1");
        AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
                .setName(secretVersionName.toString())
                .build();
        AccessSecretVersionResponse response = secretManagerServiceClient.accessSecretVersion(request);
        return response.getPayload().getData().toStringUtf8();
    }

    public Conversation getNewConversation() {
        return getChatGPT().newConversation(
                "다음 문장에서 반복, 띄어쓰기를 정리한 문장을 반환. 정리할게 없다면 input text만 그대로 반환.");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

}
