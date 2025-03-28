package com.bankdemo.transaction.service;

import com.bankdemo.transaction.dto.TransactionRequestDTO;
import com.bankdemo.transaction.dto.TransactionResponseDTO;
import java.util.List;

public interface TransactionService {

    TransactionResponseDTO createTransaction(TransactionRequestDTO transactionRequest);
    TransactionResponseDTO getTransaction(Long id);
    List<TransactionResponseDTO> getTransactionsByAccount(Long accountId);
}
