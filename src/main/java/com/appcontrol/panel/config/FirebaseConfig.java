package com.appcontrol.panel.config;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
@Configuration
@ConditionalOnProperty(name="panel.firebase.enabled", havingValue="true")
class FirebaseConfig {
    @Bean FirebaseApp firebaseApp(@Value("${panel.firebase.project-id:${FIREBASE_PROJECT_ID:insta-11358}}") String projectId) throws Exception {
        if (projectId.isBlank()) throw new IllegalStateException("FIREBASE_PROJECT_ID is required");
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(projectId)
            .setCredentials(GoogleCredentials.getApplicationDefault()).build());
    }
    @Bean FirebaseAuth firebaseAuth(FirebaseApp app) { return FirebaseAuth.getInstance(app); }
    @Bean Firestore firestore(FirebaseApp app) { return FirestoreClient.getFirestore(app); }
    @Bean FirebaseMessaging messaging(FirebaseApp app) { return FirebaseMessaging.getInstance(app); }
}
