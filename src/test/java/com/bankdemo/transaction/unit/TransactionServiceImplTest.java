package com.bankdemo.transaction.unit;

import com.bankdemo.transaction.client.AccountServiceClient;
import com.bankdemo.transaction.dto.AccountDTO;
import com.bankdemo.transaction.dto.TransactionRequestDTO;
import com.bankdemo.transaction.dto.TransactionResponseDTO;
import com.bankdemo.transaction.exception.TransactionException;
import com.bankdemo.transaction.model.Transaction;
import com.bankdemo.transaction.model.Transaction.TransactionStatus;
import com.bankdemo.transaction.producer.TransactionEventProducer;
import com.bankdemo.transaction.repository.TransactionRepository;
import com.bankdemo.transaction.service.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountServiceClient accountClient;

    @Mock
    private TransactionEventProducer eventProducer;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private TransactionRequestDTO validRequest;
    private Transaction savedTransaction;
    private AccountDTO sourceAccount;
    private AccountDTO destAccount;

    @BeforeEach
    void setUp() {
        // Configurar datos de prueba con monto mínimo de 1000.00
        validRequest = new TransactionRequestDTO();
        validRequest.setFromAccount(1001L);
        validRequest.setToAccount(2001L);
        validRequest.setMonto(new BigDecimal("1000.00")); // Monto mínimo

        savedTransaction = new Transaction();
        savedTransaction.setId(1L);
        savedTransaction.setFromAccount(1001L);
        savedTransaction.setToAccount(2001L);
        savedTransaction.setMonto(new BigDecimal("1000.00")); // Monto mínimo
        savedTransaction.setFecha(LocalDateTime.now());
        savedTransaction.setStatus(TransactionStatus.PENDING);

        sourceAccount = new AccountDTO();
        sourceAccount.setId(1001L);
        sourceAccount.setNombre("Cuenta Origen");
        sourceAccount.setSaldo(new BigDecimal("5000.00")); // Saldo suficiente para la transferencia

        destAccount = new AccountDTO();
        destAccount.setId(2001L);
        destAccount.setNombre("Cuenta Destino");
        destAccount.setSaldo(new BigDecimal("500.00"));
    }

    @Test
    void createTransaction_Success() {
        // Configurar mocks
        when(accountClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountClient.getAccount(2001L)).thenReturn(destAccount);
        when(accountClient.validateAccount(eq(1001L), any())).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        doNothing().when(eventProducer).sendTransactionEvent(any(Transaction.class));

        // Ejecutar el método a probar
        TransactionResponseDTO response = transactionService.createTransaction(validRequest);

        // Verificaciones
        assertNotNull(response);
        assertEquals(1L, response.getTransactionId());
        assertEquals(TransactionStatus.PROCESSING.toString(), response.getStatus());
        assertEquals(validRequest.getMonto(), response.getMonto());

        // Verificar que se llamaron los métodos esperados
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(eventProducer).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_AmountBelowMinimum() {
        // Crear una solicitud con monto por debajo del mínimo
        TransactionRequestDTO lowAmountRequest = new TransactionRequestDTO();
        lowAmountRequest.setFromAccount(1001L);
        lowAmountRequest.setToAccount(2001L);
        lowAmountRequest.setMonto(new BigDecimal("999.99"));  // Por debajo de 1000.00

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.createTransaction(lowAmountRequest);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("monto mínimo"));
        assertEquals("El monto mínimo de transferencia es 1000.00", exception.getMessage());

        // Verificar que no se realizaron otras llamadas
        verify(accountClient, never()).getAccount(anyLong());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(eventProducer, never()).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_ExactMinimumAmount() {
        // La prueba utiliza el validRequest que ya está configurado con el monto mínimo exacto

        // Configurar mocks
        when(accountClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountClient.getAccount(2001L)).thenReturn(destAccount);
        when(accountClient.validateAccount(eq(1001L), any())).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        doNothing().when(eventProducer).sendTransactionEvent(any(Transaction.class));

        // Ejecutar el método con el monto mínimo exacto
        TransactionResponseDTO response = transactionService.createTransaction(validRequest);

        // Verificaciones
        assertNotNull(response);
        assertEquals(1L, response.getTransactionId());
        assertEquals(new BigDecimal("1000.00"), response.getMonto());

        // Verificar que se llamaron los métodos esperados
        verify(accountClient).getAccount(1001L);
        verify(accountClient).getAccount(2001L);
        verify(accountClient).validateAccount(eq(1001L), any());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(eventProducer).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_SourceAccountNotFound() {
        // Configurar mock para simular cuenta origen no encontrada
        when(accountClient.getAccount(1001L)).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.createTransaction(validRequest);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("cuenta de origen no existe"));

        // Verificar que no se realizaron otras llamadas
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(eventProducer, never()).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_DestinationAccountNotFound() {
        // Configurar mocks
        when(accountClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountClient.getAccount(2001L)).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.createTransaction(validRequest);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("cuenta de destino no existe"));

        // Verificar que no se realizaron otras llamadas
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(eventProducer, never()).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_InsufficientFunds() {
        // Configurar mocks
        when(accountClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountClient.getAccount(2001L)).thenReturn(destAccount);
        when(accountClient.validateAccount(eq(1001L), any())).thenReturn(false);

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.createTransaction(validRequest);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("saldo suficiente"));

        // Verificar que no se realizaron otras llamadas
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(eventProducer, never()).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_EventProducerFails() {
        // Configurar mocks
        when(accountClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountClient.getAccount(2001L)).thenReturn(destAccount);
        when(accountClient.validateAccount(eq(1001L), any())).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        doThrow(new RuntimeException("Error al enviar evento")).when(eventProducer).sendTransactionEvent(any(Transaction.class));

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.createTransaction(validRequest);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("Error al procesar la transacción"));

        // Verificar que se guardó la transacción con estado FAILED
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void getTransaction_Success() {
        // Configurar mock
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(savedTransaction));

        // Ejecutar el método a probar
        TransactionResponseDTO response = transactionService.getTransaction(1L);

        // Verificaciones
        assertNotNull(response);
        assertEquals(1L, response.getTransactionId());
        assertEquals(savedTransaction.getStatus().toString(), response.getStatus());
    }

    @Test
    void getTransaction_NotFound() {
        // Configurar mock
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        // Verificar que se lanza la excepción esperada
        TransactionException exception = assertThrows(TransactionException.class, () -> {
            transactionService.getTransaction(1L);
        });

        // Verificar mensaje de error
        assertTrue(exception.getMessage().contains("no encontrada"));
    }

    @Test
    void getTransactionsByAccount_Success() {
        // Configurar mock
        Transaction transaction1 = new Transaction();
        transaction1.setId(1L);
        transaction1.setFromAccount(1001L);
        transaction1.setToAccount(2001L);
        transaction1.setMonto(new BigDecimal("1000.00"));
        transaction1.setFecha(LocalDateTime.now());
        transaction1.setStatus(TransactionStatus.COMPLETED);

        Transaction transaction2 = new Transaction();
        transaction2.setId(2L);
        transaction2.setFromAccount(2001L);
        transaction2.setToAccount(1001L);
        transaction2.setMonto(new BigDecimal("1500.00"));
        transaction2.setFecha(LocalDateTime.now().minusDays(1));
        transaction2.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.findByFromAccountOrToAccountOrderByFechaDesc(1001L, 1001L))
                .thenReturn(Arrays.asList(transaction1, transaction2));

        // Ejecutar el método a probar
        List<TransactionResponseDTO> responses = transactionService.getTransactionsByAccount(1001L);

        // Verificaciones
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).getTransactionId());
        assertEquals(2L, responses.get(1).getTransactionId());
    }
}
