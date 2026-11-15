# MyGameListApi

API REST do MyGameList, construída com Java e Spring Boot.

## Pré-requisitos

- Java 25
- Docker

## Banco de dados local

Inicie o PostgreSQL:

```bash
docker run --name some-postgres -e POSTGRES_PASSWORD=mysecretpassword -d postgres
```

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
| `POST` | `/api/users` | Público, com convite | Cria uma conta |
| `POST` | `/api/auth/login` | Público | Inicia uma sessão |
| `GET` | `/api/auth/me` | Autenticado | Retorna o usuário da sessão |
| `POST` | `/api/auth/logout` | Autenticado | Encerra a sessão |
| `GET` | `/api/games?query=...` | Público | Pesquisa o catálogo com paginação |
| `GET` | `/api/games/{gameId}` | Público | Consulta um jogo |
| `GET` | `/api/games/recent` | Público | Lista até 18 jogos lançados recentemente com capa |
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

## Verificação de segurança após o deploy

Execute os testes rápidos contra a origem HTTPS publicada, usando Python 3.

```bash
python3 scripts/security-smoke.py https://ENDERECO_DO_SITE
```

O script verifica o redirecionamento, os cabeçalhos do frontend, o acesso anônimo,
CSRF, CORS, uma busca com entrada suspeita e a exposição pública do OpenAPI.
Ele retorna código diferente de zero se algum teste falhar.

Passe `--check-rate-limit` para testar também o HTTP 429. Essa opção consome
temporariamente a cota de login e cadastro do IP que executa o teste.
O teste de isolamento entre usuários roda com `./mvnw verify` e usa dados descartáveis.

## Licença

O código da API é disponibilizado sob a [Apache License 2.0](LICENSE).
O arquivo [NOTICE](NOTICE) contém o aviso de autoria.
