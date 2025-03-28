package com.bankdemo.transaction.controller;

import com.bankdemo.transaction.dto.TransactionRequestDTO;
import com.bankdemo.transaction.dto.TransactionResponseDTO;
import com.bankdemo.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponseDTO> createTransaction(
            @Valid @RequestBody TransactionRequestDTO transactionRequest) {
        TransactionResponseDTO response = transactionService.createTransaction(transactionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> getTransaction(@PathVariable Long id) {
        TransactionResponseDTO transaction = transactionService.getTransaction(id);
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponseDTO>> getTransactionsByAccount(@PathVariable Long accountId) {
        List<TransactionResponseDTO> transactions = transactionService.getTransactionsByAccount(accountId);
        return ResponseEntity.ok(transactions);
    }
}
