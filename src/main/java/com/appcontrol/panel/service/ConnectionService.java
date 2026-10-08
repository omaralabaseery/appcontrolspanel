package com.appcontrol.panel.service;

import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Public status exposes no documents, account details or credentials. Cached to bound reads. */
@Service
public class ConnectionService {
    private final ObjectProvider<Firestore> provider;
    private final String projectId;
    private final boolean enabled;
    private Map<String,Object> cached;
    private long expiresAt;
    public ConnectionService(ObjectProvider<Firestore> provider,
        @Value("${panel.firebase.project-id:${FIREBASE_PROJECT_ID:insta-11358}}") String projectId,
        @Value("${panel.firebase.enabled:false}") boolean enabled) {
        this.provider=provider;this.projectId=projectId;this.enabled=enabled;
    }
    public synchronized Map<String,Object> status() {
        long now=System.currentTimeMillis();
        if(cached!=null && now<expiresAt) return cached;
        String status="not_configured";
        if(enabled) {
            var db=provider.getIfAvailable();
            if(db!=null) {
                try {
                    // A successful read proves connectivity even when this document does not exist.
                    db.document("_meta/schema").get().get(4,TimeUnit.SECONDS);
                    status="connected";
                } catch(InterruptedException ex) {
                    Thread.currentThread().interrupt();status="unavailable";
                } catch(Exception ex) { status="unavailable"; }
            }
        }
        cached=Map.of("projectId",projectId,"database","(default)","status",status,"checkedAt",now);
        expiresAt=System.currentTimeMillis()+30000;
        return cached;
    }
}
