package uk.gov.pay.ledger.transaction.service;

import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.commons.model.Source;
import uk.gov.pay.ledger.event.model.Event;
import uk.gov.pay.ledger.event.model.EventDigest;
import uk.gov.pay.ledger.event.model.ResourceType;
import uk.gov.pay.ledger.event.model.SalientEventType;
import uk.gov.pay.ledger.metadatakey.dao.MetadataKeyDao;
import uk.gov.pay.ledger.transaction.dao.TransactionDao;
import uk.gov.pay.ledger.transaction.entity.TransactionEntity;
import uk.gov.pay.ledger.transaction.state.TransactionState;
import uk.gov.pay.ledger.transactionmetadata.dao.TransactionMetadataDao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.ledger.event.model.ResourceType.PAYMENT;
import static uk.gov.pay.ledger.util.fixture.EventFixture.anEventFixture;
import static uk.gov.pay.ledger.util.fixture.QueuePaymentEventFixture.aQueuePaymentEventFixture;
import static uk.gov.pay.ledger.util.fixture.TransactionFixture.aTransactionFixture;

@ExtendWith(MockitoExtension.class)
public class TransactionMetadataServiceTest {

    @Mock
    private TransactionDao mockTransactionDao;
    @Mock
    private TransactionMetadataDao mockTransactionMetadataDao;
    @Mock
    private MetadataKeyDao mockMetadataKeyDao;

    private TransactionMetadataService service;

    @Test
    public void shouldInsertMetadata() {
        String externalId = "transaction-id";
        TransactionEntity transaction = aTransactionFixture().withState(TransactionState.CREATED).toEntity();
        service = new TransactionMetadataService(mockMetadataKeyDao, mockTransactionMetadataDao, mockTransactionDao);

        when(mockTransactionDao.findTransactionByExternalId(externalId)).thenReturn(Optional.of(transaction));

        Event paymentCreatedEvent = aQueuePaymentEventFixture()
                .withResourceExternalId(externalId)
                .withEventType(SalientEventType.PAYMENT_CREATED.name())
                .withResourceType(ResourceType.PAYMENT)
                .withSource(Source.CARD_API)
                .withMetadata("meta1", "data1")
                .withMetadata("meta2", "data2")
                .withDefaultEventDataForEventType(SalientEventType.PAYMENT_CREATED.name())
                .toEntity();
        EventDigest eventDigest = EventDigest.fromEventList(List.of(paymentCreatedEvent));

        service.upsertMetadataFor(eventDigest);

        verify(mockMetadataKeyDao).insertIfNotExist("meta1");
        verify(mockMetadataKeyDao).insertIfNotExist("meta2");
        verify(mockTransactionMetadataDao).upsert(transaction.getId(), "meta1", "data1");
        verify(mockTransactionMetadataDao).upsert(transaction.getId(), "meta2", "data2");
    }

    @Test
    public void shouldNotTryToInsertMetadata() {
        String externalId = "transaction-id";
        TransactionEntity transaction = aTransactionFixture().withState(TransactionState.STARTED).toEntity();
        service = new TransactionMetadataService(mockMetadataKeyDao, mockTransactionMetadataDao, mockTransactionDao);

        Event paymentCreatedEvent = aQueuePaymentEventFixture()
                .withResourceExternalId(externalId)
                .withEventType(SalientEventType.CAPTURE_SUBMITTED.name())
                .withResourceType(ResourceType.PAYMENT)
                .withSource(Source.CARD_API)
                .withDefaultEventDataForEventType(SalientEventType.CAPTURE_SUBMITTED.name())
                .toEntity();

        EventDigest eventDigest = EventDigest.fromEventList(List.of(paymentCreatedEvent));

        service.upsertMetadataFor(eventDigest);

        verify(mockMetadataKeyDao, never()).insertIfNotExist(anyString());
        verify(mockTransactionMetadataDao, never()).upsert(anyLong(), anyString(), anyString());
    }

    @Test
    public void shouldUpsertValue() {
        String externalId = "transaction-id";
        TransactionEntity transaction = aTransactionFixture().withState(TransactionState.STARTED).toEntity();
        service = new TransactionMetadataService(mockMetadataKeyDao, mockTransactionMetadataDao, mockTransactionDao);

        Event event = anEventFixture().withResourceType(PAYMENT)
                .withResourceExternalId(externalId)
                .withEventType("ADMIN_MANUALLY_DID_AN_UPDATE")
                .withEventData(new GsonBuilder().create()
                        .toJson(Map.of("external_metadata", Map.of("key1", true))))
                .toEntity();

        Event previousEvent = anEventFixture().withResourceType(PAYMENT)
                .withEventType("USER_APPROVED_FOR_CAPTURE")
                .toEntity();
        EventDigest paymentEventDigest = EventDigest.fromEventList(List.of(event, previousEvent));

        when(mockTransactionDao.findTransactionByExternalId(externalId)).thenReturn(Optional.of(transaction));

        service.upsertMetadataFor(paymentEventDigest);

        verify(mockTransactionMetadataDao).upsert(any(), eq("key1"), eq("true"));
    }
}
