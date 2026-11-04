# MyGameListApi

API REST do MyGameList, construída com Java e Spring Boot.

## Ambientes com Docker

O código é o mesmo nos dois ambientes e os arquivos dos dois ambientes devem acompanhar
as branches `dev` e `main`. A diferença fica na configuração e no momento de promover
uma versão validada para `main`.

| Ambiente | Dockerfile | Compose | Comportamento |
| --- | --- | --- | --- |
| Desenvolvimento | `Dockerfile.dev` | `compose.dev.yaml` | Ferramentas de desenvolvimento e código local |
| Produção | `Dockerfile` | `compose.prod.yaml` | Build compilado, sem montagem do código local |

Com os dois repositórios lado a lado na pasta `MyGameList`, execute na pasta compartilhada:

```bash
./start-dev.sh
./seed-dev.sh
```

Acesse `http://localhost:5174`; a API fica em `http://localhost:8080` e o banco em
`localhost:5433`. `./logs-dev.sh` acompanha os logs e `./stop-dev.sh` encerra o ambiente
preservando os dados. `./reset-dev.sh` apaga e recria somente o banco de desenvolvimento.
As portas de desenvolvimento ficam restritas ao computador local.

O Compose da raiz inclui os arquivos de cada repositório, usando Docker Compose 2.20.3+
([documentação de include](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)).
O `compose.yaml` da raiz continua sendo um atalho para desenvolvimento.

### Produção

Na pasta compartilhada, copie `.env.prod.example` para `.env.prod`, preencha uma senha
forte para `DATABASE_PASSWORD` e a origem pública HTTPS em `FRONTEND_ORIGIN` (sem caminho
nem barra final). Nunca publique o arquivo real com credenciais.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml up --build -d --wait
```

Esse ambiente usa o projeto `mygamelist-prod`, com volume separado do desenvolvimento.
O banco e a API não publicam portas; o front atende em `127.0.0.1:8081`, ajustável por
`PROD_WEB_PORT`. É necessário configurar um proxy HTTPS confiável no host para publicar
o domínio, preservar `Host` e sobrescrever `X-Forwarded-Proto` com `https`.

O Nginx do front encaminha `/api` para a API na mesma origem, preservando o caminho.
A imagem de produção usa `VITE_API_BASE_URL` vazio, então o navegador usa URLs relativas.
Isso permite ler o cookie CSRF na origem do front. A API usa o perfil `prod`, cookies de
sessão `Secure` e documentação Swagger desativada. Login por HTTP local não é um teste
válido desse ambiente: os cookies de sessão exigem HTTPS.

A configuração prepara a execução; domínio, certificados, hospedagem e backups ainda
precisam ser definidos antes de disponibilizar o serviço. Não use `down --volumes` em
produção, pois isso apaga o banco. Para encerrar preservando os dados:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml down
```

## Pré-requisitos

- Java 25
- Docker

## Desenvolvimento da API no Docker

Para iniciar somente banco e API a partir deste repositório:

```bash
docker compose -f compose.dev.yaml up --build -d --wait
```

`src` é montado em modo de leitura. Depois de editar Java, recompile e reinicie com:

```bash
docker compose -f compose.dev.yaml restart api
```

Se iniciou pela raiz com os scripts, execute o reinício pela raiz, mantendo o mesmo projeto:

```bash
docker compose -p mygamelist -f compose.dev.yaml restart api
```

Ao alterar `pom.xml`, reconstrua a imagem. O `compose.yaml` deste repositório continua
iniciando somente PostgreSQL para quem executa Java diretamente no host.

## Banco de dados local

Inicie o PostgreSQL:

```bash
docker compose up -d
```

Se a porta `5432` já estiver ocupada, publique o banco em outra porta:

```bash
POSTGRES_PORT=5433 docker compose up -d
DATABASE_URL=jdbc:postgresql://localhost:5433/mygamelist ./mvnw spring-boot:run
```

O volume do PostgreSQL não depende da porta escolhida, portanto os dados locais são
preservados ao alternar entre elas.

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

Para executar todas as verificações do projeto, incluindo testes, formato e avisos do
compilador:

```bash
./mvnw verify
```

## Endpoints do MVP

| Método | Endpoint | Acesso | Finalidade |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | Público | Obtém o token CSRF |
| `POST` | `/api/users` | Público | Cria uma conta |
| `POST` | `/api/auth/login` | Público | Inicia uma sessão |
| `GET` | `/api/auth/me` | Autenticado | Retorna o usuário da sessão |
| `POST` | `/api/auth/logout` | Autenticado | Encerra a sessão |
| `GET` | `/api/games?query=...` | Público | Pesquisa o catálogo com paginação |
| `GET` | `/api/games/{gameId}` | Público | Consulta um jogo |
| `GET` | `/api/me/games` | Autenticado | Consulta a própria lista |
| `POST` | `/api/me/games` | Autenticado | Adiciona um jogo à própria lista |
| `PATCH` | `/api/me/games/{gameId}` | Autenticado | Atualiza uma entrada própria |
| `DELETE` | `/api/me/games/{gameId}` | Autenticado | Remove uma entrada própria |
| `GET` | `/api/users/{username}` | Público | Consulta um perfil público |
| `GET` | `/api/users/{username}/games` | Público | Consulta a lista pública de um usuário |

As respostas paginadas contêm `content`, `page`, `size`, `totalElements`,
`totalPages`, `first` e `last`. A primeira página é `0` e o tamanho máximo é `100`.

O catálogo é somente leitura pela API. Os jogos usados nos testes são criados como
fixtures e não existe endpoint de cadastro manual. Uma integração futura será responsável
por sincronizar os jogos de uma fonte externa.

## Sessão e CSRF

O front-end deve enviar as requisições com cookies (`credentials: "include"`). Antes de
uma operação `POST`, `PATCH` ou `DELETE`, ele consulta `/api/auth/csrf` e envia o valor do
cookie `XSRF-TOKEN` no header `X-XSRF-TOKEN`.

O token deve ser consultado novamente depois do login, pois a autenticação troca o ID da
sessão e invalida o token anterior. O cookie da sessão é `HttpOnly`; nenhum identificador
de sessão precisa ser salvo no armazenamento do navegador.

## Documentação da API

Com a aplicação em execução:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Configuração de produção

Ative o perfil `prod` com `SPRING_PROFILES_ACTIVE=prod`. Esse perfil exige as variáveis
`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` e `FRONTEND_ORIGIN`, sem valores
padrão inseguros, e força o cookie de sessão a usar `Secure`.

## Licença

Copyright 2026 João Vítor Motta Souto Maior.

Este projeto é licenciado sob a [Apache License 2.0](LICENSE).
Consulte também os avisos de autoria em [NOTICE](NOTICE).
A licença do código não concede direitos sobre capas, marcas ou dados de terceiros.
