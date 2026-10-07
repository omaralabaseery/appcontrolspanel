package com.appcontrol.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseServiceTest {
    Firestore db;
    Transaction transaction;
    DocumentReference settings, metadata, audit;
    DocumentSnapshot settingsSnapshot, metadataSnapshot;
    DatabaseService service;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        db=mock(Firestore.class); transaction=mock(Transaction.class);
        settings=mock(DocumentReference.class); metadata=mock(DocumentReference.class); audit=mock(DocumentReference.class);
        settingsSnapshot=mock(DocumentSnapshot.class); metadataSnapshot=mock(DocumentSnapshot.class);
        var auditCollection=mock(CollectionReference.class);
        when(db.document("settings/application")).thenReturn(settings);
        when(db.document("_meta/schema")).thenReturn(metadata);
        when(db.collection("audit")).thenReturn(auditCollection);
        when(auditCollection.document()).thenReturn(audit);
        when(transaction.get(settings)).thenReturn(ApiFutures.immediateFuture(settingsSnapshot));
        when(transaction.get(metadata)).thenReturn(ApiFutures.immediateFuture(metadataSnapshot));
        when(db.runTransaction(any(Transaction.Function.class))).thenAnswer(invocation -> {
            Transaction.Function<Object> f=invocation.getArgument(0);
            return ApiFutures.immediateFuture(f.updateCallback(transaction));
        });
        ObjectProvider<Firestore> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(db);
        service=new DatabaseService(provider,new ObjectMapper(),"insta-11358");
    }
    @Test void createsOnlyConfigurationAndAuditInEmptyDatabase() throws Exception {
        var result=service.initialize("admin-uid");
        assertEquals(true,result.get("changed"));
        assertEquals(List.of("settings/application","_meta/schema"),result.get("createdDocuments"));
        verify(transaction).create(eq(settings),argThat((Map<String,Object> data)->Boolean.FALSE.equals(data.get("maintenance"))));
        verify(transaction).create(eq(metadata),anyMap());
        verify(transaction).create(eq(audit),anyMap());
        verify(transaction,never()).set(any(),any());
        verify(db,never()).collection("users");
        verify(db,never()).collection("installations");
    }
    @Test void repeatInitializationDoesNotWriteOrResetPolicy() throws Exception {
        when(settingsSnapshot.exists()).thenReturn(true);
        when(metadataSnapshot.exists()).thenReturn(true);
        when(metadataSnapshot.getLong("schemaVersion")).thenReturn(1L);
        assertEquals(false,service.initialize("admin-uid").get("changed"));
        verify(transaction,never()).create(any(),any());
        verify(transaction,never()).set(any(),any());
        verify(transaction,never()).update(any(DocumentReference.class),anyMap());
    }
    @Test void existingPolicyIsPreservedWhenMetadataIsMissing() throws Exception {
        when(settingsSnapshot.exists()).thenReturn(true);
        assertEquals(List.of("_meta/schema"),service.initialize("admin-uid").get("createdDocuments"));
        verify(transaction,never()).create(eq(settings),any());
    }
    @Test void incompatibleMetadataPreventsAllWrites() {
        when(metadataSnapshot.exists()).thenReturn(true);
        when(metadataSnapshot.getLong("schemaVersion")).thenReturn(2L);
        assertThrows(IllegalStateException.class,()->service.initialize("admin-uid"));
        verify(transaction,never()).create(any(),any());
    }
}
