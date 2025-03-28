package com.bankdemo.transaction.integration;

import com.bankdemo.transaction.client.AccountServiceClient;
import com.bankdemo.transaction.dto.AccountDTO;
import com.bankdemo.transaction.dto.TransactionRequestDTO;
import com.bankdemo.transaction.dto.TransactionResponseDTO;
import com.bankdemo.transaction.model.Transaction;
import com.bankdemo.transaction.model.Transaction.TransactionStatus;
import com.bankdemo.transaction.producer.TransactionEventProducer;
import com.bankdemo.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Usar import estáticos específicos para evitar ambigüedad
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"transaction-events", "transaction-results"})
@DirtiesContext
@Transactional
public class TransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @MockBean
    private TransactionEventProducer eventProducer;

    @Autowired
    private ObjectMapper objectMapper;

    private AccountDTO sourceAccount;
    private AccountDTO destAccount;

    @BeforeEach
    void setUp() {
        // Limpiar repositorio
        transactionRepository.deleteAll();

        // Preparar datos de prueba con saldos suficientes para el monto mínimo
        sourceAccount = new AccountDTO();
        sourceAccount.setId(1001L);
        sourceAccount.setNombre("Cuenta Origen");
        sourceAccount.setSaldo(new BigDecimal("5000.00"));

        destAccount = new AccountDTO();
        destAccount.setId(2001L);
        destAccount.setNombre("Cuenta Destino");
        destAccount.setSaldo(new BigDecimal("2000.00"));

        // Configuración por defecto de los mocks
        when(accountServiceClient.getAccount(1001L)).thenReturn(sourceAccount);
        when(accountServiceClient.getAccount(2001L)).thenReturn(destAccount);
        when(accountServiceClient.validateAccount(eq(1001L), any())).thenReturn(true);

        // Configurar el comportamiento predeterminado para el eventProducer (corregido)
        doNothing().when(eventProducer).sendTransactionEvent(any(Transaction.class));

        // Configurar ObjectMapper para manejar LocalDateTime
        objectMapper.findAndRegisterModules();
    }

    @AfterEach
    void tearDown() {
        // Limpiar después de cada prueba
        reset(accountServiceClient, eventProducer);
    }

    @Test
    void createAndRetrieveTransaction() throws Exception {
        // Crear transacción a través de la API con monto mínimo válido
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("1000.00"));

        MvcResult result = mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PROCESSING")))
                .andExpect(jsonPath("$.monto", is(1000.00)))
                .andReturn();

        // Extraer ID de la transacción de la respuesta
        TransactionResponseDTO response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransactionResponseDTO.class
        );
        Long transactionId = response.getTransactionId();

        // Verificar que la transacción se guardó en la base de datos
        Transaction savedTransaction = transactionRepository.findById(transactionId).orElse(null);
        assertNotNull(savedTransaction);
        assertEquals(TransactionStatus.PROCESSING, savedTransaction.getStatus());
        assertEquals(new BigDecimal("1000.00"), savedTransaction.getMonto());

        // Verificar que se puede recuperar la transacción a través de la API
        mockMvc.perform(get("/transactions/" + transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", is(transactionId.intValue())))
                .andExpect(jsonPath("$.status", is("PROCESSING")))
                .andExpect(jsonPath("$.fromAccount", is(1001)))
                .andExpect(jsonPath("$.toAccount", is(2001)))
                .andExpect(jsonPath("$.monto", is(1000.0)));

        // Verificar interacciones con los mocks
        verify(accountServiceClient).getAccount(1001L);
        verify(accountServiceClient).getAccount(2001L);
        verify(accountServiceClient).validateAccount(eq(1001L), any());
        verify(eventProducer).sendTransactionEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_AmountBelowMinimum() throws Exception {
        // Intentar crear una transacción por debajo del monto mínimo
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("999.99"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("monto mínimo")));

        // Verificar que no se llamaron los métodos del servicio de cuentas
        verify(accountServiceClient, never()).getAccount(anyLong());
        verify(accountServiceClient, never()).validateAccount(anyLong(), any());
        verify(eventProducer, never()).sendTransactionEvent(any(Transaction.class));

        // Verificar que no se guardó ninguna transacción
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void createTransaction_SourceAccountNotFound() throws Exception {
        // Configurar mock para simular cuenta origen no encontrada
        when(accountServiceClient.getAccount(1001L))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Intentar crear una transacción
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("1000.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("cuenta de origen")));

        // Verificar que no se guardó ninguna transacción
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void createTransaction_DestinationAccountNotFound() throws Exception {
        // Configurar mock para simular cuenta destino no encontrada
        when(accountServiceClient.getAccount(2001L))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Intentar crear una transacción
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("1000.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("cuenta de destino")));

        // Verificar que se llamó solo al getAccount de la cuenta origen
        verify(accountServiceClient).getAccount(1001L);
        verify(accountServiceClient).getAccount(2001L);

        // Verificar que no se guardó ninguna transacción
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void createTransaction_InsufficientFunds() throws Exception {
        // Configurar mock para simular fondos insuficientes
        when(accountServiceClient.validateAccount(eq(1001L), any())).thenReturn(false);

        // Intentar crear una transacción
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("1000.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("saldo suficiente")));

        // Verificar que no se guardó ninguna transacción
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void getTransactionsByAccount() throws Exception {
        // Crear varias transacciones directamente en la base de datos
        Transaction transaction1 = new Transaction();
        transaction1.setFromAccount(1001L);
        transaction1.setToAccount(2001L);
        transaction1.setMonto(new BigDecimal("1000.00"));
        transaction1.setFecha(LocalDateTime.now());
        transaction1.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction1);

        Transaction transaction2 = new Transaction();
        transaction2.setFromAccount(2001L);
        transaction2.setToAccount(1001L);
        transaction2.setMonto(new BigDecimal("1500.00"));
        transaction2.setFecha(LocalDateTime.now().minusDays(1));
        transaction2.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction2);

        // Consultar transacciones por cuenta a través de la API
        mockMvc.perform(get("/transactions/account/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fromAccount", is(1001)))
                .andExpect(jsonPath("$[0].toAccount", is(2001)))
                .andExpect(jsonPath("$[0].monto", is(1000.00)))
                .andExpect(jsonPath("$[1].fromAccount", is(2001)))
                .andExpect(jsonPath("$[1].toAccount", is(1001)))
                .andExpect(jsonPath("$[1].monto", is(1500.00)));
    }

    @Test
    void getTransactionsByAccount_NoTransactions() throws Exception {
        // Consultar transacciones para una cuenta sin transacciones
        mockMvc.perform(get("/transactions/account/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getTransaction_NotFound() throws Exception {
        // Intentar obtener una transacción que no existe
        mockMvc.perform(get("/transactions/9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("no encontrada")));
    }

    @Test
    void createTransaction_EventProducerFails() throws Exception {
        // Configurar mock para simular fallo al enviar evento
        // Primero resetear y luego configurar el comportamiento específico
        reset(eventProducer);
        doThrow(new RuntimeException("Error al enviar evento"))
                .when(eventProducer).sendTransactionEvent(any(Transaction.class));

        // Intentar crear una transacción
        TransactionRequestDTO request = new TransactionRequestDTO();
        request.setFromAccount(1001L);
        request.setToAccount(2001L);
        request.setMonto(new BigDecimal("1000.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Error al procesar")));

        // Verificar que se guardó una transacción con estado FAILED
        List<Transaction> transactions = transactionRepository.findAll();
        assertEquals(1, transactions.size());
        assertEquals(TransactionStatus.FAILED, transactions.get(0).getStatus());
        assertNotNull(transactions.get(0).getErrorMessage());
    }
}