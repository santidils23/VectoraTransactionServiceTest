package com.bankdemo.transaction.service;

import com.bankdemo.transaction.client.AccountServiceClient;
import com.bankdemo.transaction.dto.AccountDTO;
import com.bankdemo.transaction.dto.TransactionRequestDTO;
import com.bankdemo.transaction.dto.TransactionResponseDTO;
import com.bankdemo.transaction.exception.TransactionException;
import com.bankdemo.transaction.model.Transaction;
import com.bankdemo.transaction.model.Transaction.TransactionStatus;
import com.bankdemo.transaction.repository.TransactionRepository;
import com.bankdemo.transaction.producer.TransactionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountClient;
    private final TransactionEventProducer eventProducer;

    @Override
    @Transactional
    public TransactionResponseDTO createTransaction(TransactionRequestDTO request) {

        // 1. Primero, verificar que la cuenta de origen existe
        if(request.getMonto().compareTo(BigDecimal.valueOf(1000.0)) < 0) {
            throw new TransactionException("El monto mínimo de transferencia es 1000.00");
        }

        verifiyAccount(request.getFromAccount(), "origen");
        verifiyAccount(request.getToAccount(), "destino");

        // 2. Verificar que la cuenta de origen tenga saldo suficiente
        boolean hasSufficientFunds = accountClient.validateAccount(request.getFromAccount(), request.getMonto().negate());
        if (!hasSufficientFunds) {
            throw new TransactionException("La cuenta de origen no tiene saldo suficiente");
        }

        // 3. Crear la transacción con estado pendiente
        Transaction transaction = new Transaction();
        transaction.setFromAccount(request.getFromAccount());
        transaction.setToAccount(request.getToAccount());
        transaction.setMonto(request.getMonto());
        transaction.setFecha(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);

        // 4. Guardar la transacción en estado pendiente
        Transaction savedTransaction = transactionRepository.save(transaction);

        try {
            // 5. Publicar evento de transacción para que el servicio de cuentas la procese (ya validada)
            log.info("Enviando evento de transacción: {}", savedTransaction.getId());
            eventProducer.sendTransactionEvent(savedTransaction);

            // 6. Actualizar estado temporalmente a PROCESSING mientras esperamos confirmación
            savedTransaction.setStatus(TransactionStatus.PROCESSING);
            savedTransaction = transactionRepository.save(savedTransaction);

            return mapToDTO(savedTransaction);
        } catch (Exception e) {
            // En caso de error al publicar el evento, marcar la transacción como fallida
            savedTransaction.setStatus(TransactionStatus.FAILED);
            savedTransaction.setErrorMessage("Error al procesar la transacción: " + e.getMessage());
            transactionRepository.save(savedTransaction);

            throw new TransactionException("Error al procesar la transacción: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponseDTO getTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionException("Transacción no encontrada con ID: " + id));

        return mapToDTO(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponseDTO> getTransactionsByAccount(Long accountId) {
        List<Transaction> transactions = transactionRepository
                .findByFromAccountOrToAccountOrderByFechaDesc(accountId, accountId);

        return transactions.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private TransactionResponseDTO mapToDTO(Transaction transaction) {
        TransactionResponseDTO dto = new TransactionResponseDTO();
        dto.setTransactionId(transaction.getId());
        dto.setStatus(transaction.getStatus().toString());
        dto.setFecha(transaction.getFecha());
        dto.setFromAccount(transaction.getFromAccount());
        dto.setToAccount(transaction.getToAccount());
        dto.setMonto(transaction.getMonto());
        dto.setErrorMessage(transaction.getErrorMessage());
        return dto;
    }

    private void verifiyAccount(Long accountId, String tipo) {
        try {
            accountClient.getAccount(accountId);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                throw new TransactionException("La cuenta de " + tipo + " no existe: " + accountId);
            }
            throw new TransactionException("Error al verificar la cuenta de " + tipo + ": " + ex.getMessage());
        }
    }
}