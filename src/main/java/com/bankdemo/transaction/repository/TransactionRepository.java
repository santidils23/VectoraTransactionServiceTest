package com.bankdemo.transaction.repository;

import com.bankdemo.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccountOrToAccountOrderByFechaDesc(Long fromAccount, Long toAccount);
}