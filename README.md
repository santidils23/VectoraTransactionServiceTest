# Vectora Transaction Service Test

Este proyecto es un servicio de transacciones desarrollado en Java con Spring Boot. Se puede ejecutar en un entorno local o dentro de un contenedor Docker.

## 📋 Requisitos

Antes de ejecutar el servicio, asegúrate de tener instalados:

- [Docker](https://www.docker.com/)

## 🚀 Instalación y Ejecución


###  Ejecutar con Docker

1. **Construir la imagen Docker**
   ```bash
   docker build -t transaction-service .
   ```

2. **Ejecutar el contenedor**
   ```bash
   docker-compose up -d
   ```

## 🛠️ Configuración

El servicio utiliza variables de entorno para configurar su ejecución. Puedes definirlas en un archivo `.env` o pasarlas al ejecutar el contenedor.

## 📝 Endpoints

### Crear una transacción
```bash
curl -X POST http://localhost:8082/transactions \
     -H "Content-Type: application/json" \
     -d '{"fromAccount": 1,"toAccount": 2,"monto": 1000}'
```

### Obtener una transacción por ID
```bash
curl -X GET http://localhost:8082/transactions/{id}
```

### Obtener todas las transacciones de una cuenta
```bash
curl -X GET http://localhost:8082/transactions/account/{accountId}
```

## 📜 Licencia

Este proyecto está bajo la licencia MIT.