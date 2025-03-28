package com.bankdemo.transaction.client;

import com.bankdemo.transaction.dto.AccountDTO;
import com.bankdemo.transaction.dto.AuthRequestDTO;
import com.bankdemo.transaction.dto.AuthResponseDTO;
import com.bankdemo.transaction.exception.TransactionException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@Slf4j
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;
    private final String username;
    private final String password;

    private String jwtToken;

    public AccountServiceClient(
            RestTemplate restTemplate,
            @Value("${service.account.url}") String accountServiceUrl,
            @Value("${admin.username:admin}") String username,
            @Value("${admin.password:password}") String password) {
        this.restTemplate = restTemplate;
        this.accountServiceUrl = accountServiceUrl;
        this.username = username;
        this.password = password;
    }

    private void ensureTokenExists() {
        if (jwtToken == null) {
            try {
                AuthRequestDTO authRequest = new AuthRequestDTO(username, password);
                ResponseEntity<AuthResponseDTO> authResponse = restTemplate.postForEntity(
                        accountServiceUrl + "/auth/token",
                        authRequest,
                        AuthResponseDTO.class);

                if (authResponse.getBody() != null) {
                    jwtToken = authResponse.getBody().getToken();
                }
            } catch (Exception e) {
                throw new RuntimeException("Error al obtener token JWT: " + e.getMessage(), e);
            }
        }
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountFallback")
    public AccountDTO getAccount(Long id) {
        ensureTokenExists();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<AccountDTO> response = restTemplate.exchange(
                accountServiceUrl + "/accounts/" + id,
                HttpMethod.GET,
                requestEntity,
                AccountDTO.class);

        log.info(String.valueOf(response));

        return response.getBody();
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "validateAccountFallback")
    public Boolean validateAccount(Long id, BigDecimal amount) {
        ensureTokenExists();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<Boolean> response = restTemplate.exchange(
                accountServiceUrl + "/accounts/" + id + "/validate?amount=" + amount,
                HttpMethod.GET,
                requestEntity,
                Boolean.class);

        return response.getBody();
    }

    public AccountDTO getAccountFallback(Long id, Throwable t) {
        // En caso de fallo en el servicio account-service
        if (t instanceof HttpClientErrorException httpEx) {

            // Si es un 404 (NOT_FOUND), simplemente propagamos el error
            if (httpEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new TransactionException("Cuenta no encontrada con ID: " + id);
            }
        }

        // Para otros errores (problemas del servicio, no de lógica de negocio)
        log.error("Error al obtener cuenta {}: {}", id, t.getMessage());
        throw new TransactionException("Error en servicio externo al verificar cuenta: " + t.getMessage());
    }

    public Boolean validateAccountFallback(Long id, BigDecimal amount, Throwable t) {
        // Para casos de fallo, es más seguro denegar la transacción
        return false;
    }
}