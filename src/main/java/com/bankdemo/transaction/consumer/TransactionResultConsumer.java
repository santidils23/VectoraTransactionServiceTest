package com.bankdemo.transaction.consumer;

import com.bankdemo.transaction.model.Transaction;
import com.bankdemo.transaction.model.Transaction.TransactionStatus;
import com.bankdemo.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionResultConsumer {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${spring.kafka.topic.transaction-results}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void processTransactionResult(String message) {
        try {
            log.info("Received transaction result: {}", message);

            // Parsear mensaje JSON para obtener campos necesarios
            JsonNode root = objectMapper.readTree(message);
            Long transactionId = root.path("id").asLong();
            String status = root.path("status").asText();
            String errorMessage = root.path("errorMessage").asText();

            // Buscar la transacción en la base de datos
            Transaction transaction = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new RuntimeException("Transacción no encontrada: " + transactionId));

            // Actualizar el estado de la transacción según el resultado
            if ("COMPLETED".equals(status)) {
                transaction.setStatus(TransactionStatus.COMPLETED);
                log.info("Transaction completed successfully: {}", transactionId);
            } else if ("FAILED".equals(status)) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setErrorMessage(errorMessage);
                log.warn("Transaction failed: {} - {}", transactionId, errorMessage);
            }

            // Guardar la transacción actualizada
            transactionRepository.save(transaction);

            log.info("Transaction status updated: {} -> {}", transactionId, status);

        } catch (Exception e) {
            log.error("Error processing transaction result: {}", e.getMessage(), e);
        }
    }
}