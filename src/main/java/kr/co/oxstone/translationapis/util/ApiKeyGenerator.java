//package kr.co.oxstone.translationapis.util;
//
//import java.security.SecureRandom;
//import java.util.Base64;
//
//public class ApiKeyGenerator {
//
//    private static final int KEY_LENGTH = 40;
////    private static final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
//
//    public ApiKeyGenerator() {
//    }
//
////    public static String generateKey() {
////        String rawKey = createRawKey();
////        return passwordEncoder.encode(rawKey)
////                .substring(15)
////                .replaceAll("[^a-zA-Z0-9]", "");
////    }
//
//    private static String createRawKey() {
//        SecureRandom random = new SecureRandom();
//        byte[] rawKey = new byte[KEY_LENGTH];
//        random.nextBytes(rawKey);
//        return Base64.getEncoder().encodeToString(rawKey);
//    }
//}
