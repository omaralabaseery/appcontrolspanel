package com.appcontrol.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

/** Non-destructive bootstrap: no fake user/device/event records and no policy reset. */
@Service
public class DatabaseService {
    static final int SCHEMA_VERSION = 1;
    private final ObjectProvider<Firestore> provider;
    private final ObjectMapper mapper;
    private final String projectId;
    private final Map<String,Object> schema;

    DatabaseService(ObjectProvider<Firestore> provider, ObjectMapper mapper,
                    @Value("${panel.firebase.project-id}") String projectId) throws IOException {
        this.provider=provider; this.mapper=mapper; this.projectId=projectId;
        try(var in=new ClassPathResource("database/schema.json").getInputStream()) {
            schema=mapper.readValue(in,new TypeReference<Map<String,Object>>(){});
        }
        if(((Number)schema.get("schemaVersion")).intValue()!=SCHEMA_VERSION)
            throw new IllegalStateException("Database manifest version mismatch");
    }
    Firestore db() {
        var db=provider.getIfAvailable();
        if(db==null) throw new IllegalStateException("Configure Firebase server credentials before initializing Firestore.");
        return db;
    }
    public Map<String,Object> schema() { return schema; }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> collections() { return (List<Map<String,Object>>)schema.get("collections"); }

    public Map<String,Object> status() throws Exception {
        var db=db();
        var metadata=db.document("_meta/schema").get().get();
        var settings=db.document("settings/application").get().get();
        var collectionStatus=new ArrayList<Map<String,Object>>();
        for(var c:collections()) {
            String name=c.get("name").toString();
            boolean hasDocuments=!db.collection(name).limit(1).get().get().isEmpty();
            collectionStatus.add(Map.of("name",name,"hasDocuments",hasDocuments,
                "status",hasDocuments?"contains_documents":"created_on_first_write"));
        }
        boolean versionMatches=metadata.exists() && Objects.equals(metadata.getLong("schemaVersion"),(long)SCHEMA_VERSION);
        return Map.of("projectId",projectId,"database","(default)","schemaVersion",SCHEMA_VERSION,
            "initialized",versionMatches&&settings.exists(),"settingsExists",settings.exists(),
            "metadataExists",metadata.exists(),"collections",collectionStatus);
    }

    public Map<String,Object> initialize(String actor) throws Exception {
        PanelService.id(actor);
        var db=db(); var settings=db.document("settings/application"); var metadata=db.document("_meta/schema");
        var audit=db.collection("audit").document();
        var defaults=mapper.convertValue(PolicyService.defaults(),new TypeReference<Map<String,Object>>(){});
        var names=collections().stream().map(c->c.get("name").toString()).toList();
        // Read all targets before writes. Firestore retries safely if another admin initializes concurrently.
        var created=db.runTransaction(t -> {
            var currentSettings=t.get(settings).get(); var currentMetadata=t.get(metadata).get();
            if(currentMetadata.exists() && !Objects.equals(currentMetadata.getLong("schemaVersion"),(long)SCHEMA_VERSION))
                throw new IllegalStateException("Existing schema version differs. Use a reviewed migration; initialization will not overwrite it.");
            var paths=new ArrayList<String>(); long now=System.currentTimeMillis();
            if(!currentSettings.exists()) { t.create(settings,defaults); paths.add("settings/application"); }
            if(!currentMetadata.exists()) {
                t.create(metadata,Map.of("schemaVersion",SCHEMA_VERSION,"collectionNames",names,"createdAt",now,"createdBy",actor));
                paths.add("_meta/schema");
            }
            if(!paths.isEmpty()) t.create(audit,Map.of("action","database.initialize","actor",actor,"createdAt",now,"createdDocuments",List.copyOf(paths)));
            return paths;
        }).get();
        return Map.of("projectId",projectId,"database","(default)","schemaVersion",SCHEMA_VERSION,
            "createdDocuments",created,"changed",!created.isEmpty(),
            "message",created.isEmpty()?"Already initialized; no data changed.":"Initialized missing configuration. Data collections appear on first real API write.");
    }
}
