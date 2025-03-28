package com.bankdemo.transaction.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
public class TransactionRequestDTO {

    @NotNull(message = "La cuenta de origen no puede ser nula")
    private Long fromAccount;

    @NotNull(message = "La cuenta de destino no puede ser nula")
    private Long toAccount;

    @NotNull(message = "El monto no puede ser nulo")
    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monto;
}
