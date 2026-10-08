package com.appcontrol.panel.service;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConnectionServiceTest {
    @Test @SuppressWarnings("unchecked") void disabledDoesNotContactDatabase() {
        ObjectProvider<Firestore> provider=mock(ObjectProvider.class);
        var status=new ConnectionService(provider,"insta-11358",false).status();
        assertEquals("not_configured",status.get("status"));verifyNoInteractions(provider);
    }
    @Test @SuppressWarnings("unchecked") void successfulReadIsConnectedAndCachedEvenForMissingMetadata() {
        ObjectProvider<Firestore> provider=mock(ObjectProvider.class);var db=mock(Firestore.class);var ref=mock(DocumentReference.class);
        when(provider.getIfAvailable()).thenReturn(db);when(db.document("_meta/schema")).thenReturn(ref);
        when(ref.get()).thenReturn(ApiFutures.immediateFuture(mock(DocumentSnapshot.class)));
        var service=new ConnectionService(provider,"insta-11358",true);
        var status=service.status();assertEquals("connected",status.get("status"));assertSame(status,service.status());
        verify(ref,times(1)).get();assertEquals(4,status.size());
    }
    @Test @SuppressWarnings("unchecked") void errorDoesNotExposeCredentialOrExceptionDetails() {
        ObjectProvider<Firestore> provider=mock(ObjectProvider.class);var db=mock(Firestore.class);var ref=mock(DocumentReference.class);
        when(provider.getIfAvailable()).thenReturn(db);when(db.document("_meta/schema")).thenReturn(ref);
        when(ref.get()).thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("sensitive-detail")));
        var status=new ConnectionService(provider,"insta-11358",true).status();
        assertEquals("unavailable",status.get("status"));assertFalse(status.toString().contains("sensitive-detail"));
    }
}
