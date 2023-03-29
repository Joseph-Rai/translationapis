package kr.co.oxstone.translationapis.controller;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.TranslationServiceClient;
import com.google.cloud.translate.v3.TranslationServiceSettings;
import com.google.common.collect.Lists;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/")
public class TranslationApiController {

    private TranslationServiceClient client;

    @PostMapping("/authenticate")
    public ResponseEntity<String> authenticate(@RequestBody String json) {
        GoogleCredentials myCredentials = null;
        try {
            String keyFilePath = saveJsonToFile(json);
            myCredentials = GoogleCredentials.fromStream(new FileInputStream(keyFilePath))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-translation"));
            TranslationServiceSettings defaultSettings =
                    TranslationServiceSettings.newBuilder()
                            .setCredentialsProvider(FixedCredentialsProvider.create(myCredentials))
                            .build();
            client = TranslationServiceClient.create(defaultSettings);

            return ResponseEntity.ok("인증에 성공 했습니다.");

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("인증에 실패 했습니다.");
        }
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

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

}
