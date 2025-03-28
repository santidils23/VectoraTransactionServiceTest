CREATE TABLE IF NOT EXISTS transactions (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            from_account BIGINT NOT NULL,
                                            to_account BIGINT NOT NULL,
                                            monto DECIMAL(19, 2) NOT NULL,
    fecha TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(255)
    );