package com.bankdemo.transaction.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionResponseDTO {

    private Long transactionId;
    private String status;
    private LocalDateTime fecha;
    private Long fromAccount;
    private Long toAccount;
    private BigDecimal monto;
    private String errorMessage;
}
