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
| `POST` | `/api/users` | Público, com CSRF | Inicia cadastro pendente e envia um código por e-mail |
| `POST` | `/api/users/verify-email` | Público, com CSRF | Confirma e ativa a conta |
| `POST` | `/api/users/resend-code` | Público, com CSRF | Reenvia código usando e-mail e senha do cadastro |
| `POST` | `/api/auth/login` | Público | Inicia uma sessão |
| `GET` | `/api/auth/me` | Autenticado | Retorna o usuário da sessão |
| `POST` | `/api/auth/logout` | Autenticado | Encerra a sessão |
| `GET` | `/api/games?query=...` | Público | Pesquisa o catálogo com paginação |
| `GET` | `/api/games/{gameId}` | Público | Consulta um jogo |
| `GET` | `/api/games/recent` | Público | Lista até 18 jogos lançados recentemente com capa |
| `GET` | `/api/games/recent/pages?page=0&size=18` | Público | Lista jogos já lançados com capa, por data de lançamento decrescente |
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

### Confirmação de e-mail

O cadastro responde HTTP `202` com `email`, `expiresAt` e `emailSent`. A conta
permanece desabilitada para login e perfil público até a confirmação. O código tem
8 dígitos, validade de 10 minutos e limite de 5 tentativas por conta. Sua versão
armazenada usa Argon2id. Contas anteriores à migração V5 permanecem ativas.

Envie `{"email":"player@example.com","code":"12345678"}` para `/api/users/verify-email`.
Uma confirmação válida responde `204`, invalida o código e permite login normal.
Respostas `400` cobrem código incorreto, expirado, já usado ou com tentativas esgotadas.
As tentativas incorretas são persistidas mesmo quando a resposta indica erro.

Para reenviar, envie e-mail e senha para `/api/users/resend-code`. Há intervalo de
60 segundos, até 3 envios na mesma janela e preservação do limite de tentativas.
O código anterior é invalidado. Após expirar a janela, um novo envio inicia outra.
O cadastro pode ser retomado preenchendo os mesmos dados. A limpeza
horária exclui contas ainda pendentes cujo código expirou há mais de 24 horas.

O envio usa a API HTTPS do Resend e ocorre após a persistência do cadastro/código.
Se o provedor falhar, `emailSent` retorna `false` e a conta continua pendente, com
opção de reenvio. O código não é retornado pela API nem registrado nos logs.
Para desenvolvimento/testes automatizados, o remetente é substituído por um mock.

| Propriedade Spring | Variável de ambiente | Finalidade |
| --- | --- | --- |
| `app.mail.resend.api-key` | `APP_MAIL_RESEND_API_KEY` | Chave do Resend |
| `app.mail.resend.from` | `APP_MAIL_RESEND_FROM` | Remetente autorizado |

O remetente padrão é `MyGameList <conta@contact.mygamelist.com.br>`.
Na VPS, o systemd carrega a chave via `LoadCredential=app.mail.resend.api-key:...`
e o Spring lê o arquivo pelo `configtree`. A chave fica somente no backend.

### Limites de cadastro

`POST /api/users` e `POST /api/users/resend-code` compartilham, por padrão, até
5 tentativas por IP e 200 tentativas
no total em uma janela de 15 minutos. O limite conta também campos inválidos e contas duplicadas. A checagem ocorre depois do CSRF e antes
do processamento do cadastro. O bloqueio retorna HTTP `429` com `Retry-After`
em segundos. As tentativas bloqueadas por IP também contam no limite total.

Os contadores ficam no PostgreSQL e são compartilhados entre instâncias da API.
As janelas começam na primeira tentativa de cada contador. Contadores de IP
expirados são removidos nas próximas tentativas permitidas pelo limite total.

| Variável de ambiente | Padrão | Finalidade |
| --- | --- | --- |
| `APP_REGISTRATION_RATE_LIMIT_REQUESTS_PER_IP` | `5` | Tentativas por IP |
| `APP_REGISTRATION_RATE_LIMIT_GLOBAL_REQUESTS` | `200` | Tentativas totais |
| `APP_REGISTRATION_RATE_LIMIT_WINDOW_SECONDS` | `900` | Duração da janela |

IPs compartilhados dividem o mesmo limite. Ajuste os valores ao tráfego observado.
Em produção, a API deve receber tráfego somente do proxy confiável. O Nginx
sobrescreve os headers de encaminhamento com o IP da conexão, e o Spring usa o
endereço resultante. A aplicação não lê diretamente `X-Forwarded-For` para escolher
o contador.

### Cookies e token CSRF

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
