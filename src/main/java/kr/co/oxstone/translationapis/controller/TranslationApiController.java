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
import io.github.kezhenxu94.chatgpt.message.AssistantMessage;
import io.github.kezhenxu94.chatgpt.message.Message;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        String content = getNewConversation().ask(text).content();
        log.info(content);
        return content;
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
        SecretVersionName secretVersionName = SecretVersionName.of(projectNumber, secretName, "latest");
        AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
                .setName(secretVersionName.toString())
                .build();
        AccessSecretVersionResponse response = secretManagerServiceClient.accessSecretVersion(request);
        return response.getPayload().getData().toStringUtf8();
    }

    public Conversation getNewConversation() {
        String prompt = getSecret(projectID, "CHATGPT_PROMPT_FOR_TRANSLATION");
        Conversation newConversation = getChatGPT().newConversation();
        newConversation.messages().add(Message.ofSystem("저는 문장을 정리해주는 기능을 합니다. 문장을 어떻게 정리할까요?"));
        newConversation.messages().add(Message.ofUser(prompt));
        newConversation.messages().add(new AssistantMessage("정리할 문장이 없으면 어떻게 할까요?"));
        newConversation.messages().add(Message.ofUser("입력된 텍스트만 그대로 반환."));
        newConversation.messages().add(new AssistantMessage("어떤 문장을 정리할까요?"));
        newConversation.messages().add(Message.ofUser("다음 문장을 번역하지 말고 위 조건대로 정리만 해서 반환해줘\n"));
        return newConversation;
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

}
