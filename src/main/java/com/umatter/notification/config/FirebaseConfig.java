package com.umatter.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    private final FirebaseProperties props;

    public FirebaseConfig(FirebaseProperties props) {
        this.props = props;
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!props.isEnabled()) {
            log.warn("Firebase disabled via configuration — push notifications will be skipped.");
            return null;
        }
        if (!StringUtils.hasText(props.getCredentialsPath())) {
            log.warn("notification.firebase.credentials-path is blank — push notifications will be skipped.");
            return null;
        }

        try (InputStream is = new FileInputStream(props.getCredentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized.");
                return app;
            }
            return FirebaseApp.getInstance();
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        if (app == null) {
            return null;
        }
        return FirebaseMessaging.getInstance(app);
    }
}
