# MyGameListApi

API REST do MyGameList, construída com Java e Spring Boot.

## Pré-requisitos

- Java 25
- Docker

## Banco de dados local

Inicie o PostgreSQL:

```bash
docker compose up -d
```

Por padrão, a aplicação usa estas configurações locais:

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/mygamelist
DATABASE_USERNAME=mygamelist
DATABASE_PASSWORD=mygamelist
```

As três variáveis podem ser sobrescritas no ambiente.

## Executar a aplicação

Com `JAVA_HOME` apontando para um JDK 25:

```bash
./mvnw spring-boot:run
```

## Executar os testes

```bash
./mvnw test
```

Os testes de integração usam Testcontainers e iniciam um PostgreSQL temporário. Não é necessário iniciar o banco local antes dos testes, mas o Docker precisa estar disponível.
