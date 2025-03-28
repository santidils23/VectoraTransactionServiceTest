package com.bankdemo.transaction.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountDTO {

    private Long id;
    private String nombre;
    private BigDecimal saldo;
}
