package com.bankdemo.transaction.producer;

import com.bankdemo.transaction.model.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic.transaction-events}")
    private String transactionTopic;

    public void sendTransactionEvent(Transaction transaction) {
        try {
            String transactionAsString = objectMapper.writeValueAsString(transaction);
            String key = String.valueOf(transaction.getId());

            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(transactionTopic, key, transactionAsString);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Transaction event sent successfully: {}, partition: {}",
                            key, result.getRecordMetadata().partition());
                } else {
                    log.error("Failed to send transaction event: {}", ex.getMessage(), ex);
                }
            });

            log.info("Transaction event queued for sending: {}", transaction.getId());
        } catch (JsonProcessingException e) {
            log.error("Error serializing transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Error al enviar evento de transacción", e);
        }
    }
}