package com.bankdemo.transaction.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_account", nullable = false)
    private Long fromAccount;

    @Column(name = "to_account", nullable = false)
    private Long toAccount;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    public enum TransactionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
