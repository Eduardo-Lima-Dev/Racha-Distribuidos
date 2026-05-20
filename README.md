# Sistema de Divisão de Times

Projeto da disciplina de **Sistemas Distribuídos** (QXD0043 — UFC Quixadá). Implementa um serviço remoto de ranqueamento e divisão de times de futebol em duas iterações:

- **Trabalho 1** (`network/`): protocolo binário sobre TCP + multicast UDP, com serialização manual via streams customizados.
- **Trabalho 2** (`rmi/`, `marshalling/`, `server/`, `client/`): reimplementação sobre **Java RMI** com protocolo requisição-resposta explícito (Coulouris, seção 5.2) e representação externa em JSON manual.

A arquitetura ativa **é a do Trabalho 2**. Os pacotes do Trabalho 1 são mantidos no repositório como referência histórica.

---

## Tema

Sistema de **ranqueamento anônimo de jogadores de futebol**. Durante o período aberto, jogadores autenticados avaliam uns aos outros com notas de 0 a 10. Ao encerrar as avaliações, o servidor calcula a média de cada jogador e divide os participantes em times balanceados via algoritmo *snake draft*. O resultado é entregue a todos os clientes conectados por **callback RMI** (`NotificadorCliente`).

**Usuários do sistema:**

| Tipo          | Permissões                                                                                 |
| ------------- | ------------------------------------------------------------------------------------------ |
| Jogador       | Login, listar jogadores, avaliar (sem auto-avaliação, sem duplicata)                       |
| Administrador | Tudo acima + adicionar/remover jogadores, enviar avisos, encerrar avaliações e gerar times |

**Usuários de teste pré-cadastrados:**

| Nome     | Senha     | Tipo                |
| -------- | --------- | ------------------- |
| admin    | admin123  | Administrador       |
| gestor   | gestor123 | Administrador       |
| Carlos   | senha1    | Jogador (ATACANTE)  |
| Fernanda | senha2    | Jogador (GOLEIRO)   |
| Rodrigo  | senha3    | Jogador (DEFENSOR)  |
| Ana      | senha4    | Jogador (MEIO_CAMPO)|
| Pedro    | senha5    | Jogador (ATACANTE)  |

> Senha mestra `rachao2025` concede acesso a qualquer conta cadastrada.

---

## Arquitetura RMI (Trabalho 2)

### Camadas

```
src/
├── models/         entidades de domínio (Usuario, Jogador, Administrador,
│                   Avaliacao, Time, Racha, Sessao) e a interface Avaliavel
├── rmi/            interfaces Remote + estruturas de mensagem do protocolo
│                   (RequestMessage, ReplyMessage, RemoteObjectRef)
├── marshalling/    Marshaller + parser/writer JSON (Json)
├── utils/          parser JSON adicional usado pelo servidor (utils.Json)
├── server/         dispatcher do servidor (ServicoRachaImpl, SessaoRemotaImpl,
│                   EstadoServidor, MainServidor)
├── client/         primitiva doOperation (ClienteRMI), CLI interativa
│                   (ClienteInterativo) e callback (NotificadorImpl)
└── testes/         TesteMarshalling, TesteRMIDummy, TesteIntegracao
```

### Objetos remotos

| Objeto remoto       | Nome no Registry         | Quem exporta | Papel                                              |
|---------------------|--------------------------|--------------|----------------------------------------------------|
| `ServicoRacha`      | `ServicoRacha` (fixo)    | Servidor     | Entrada do sistema — trata LOGIN                   |
| `SessaoRemota`      | `Sessao:{id}` (1 por login) | Servidor  | Demais métodos (autenticados)                      |
| `NotificadorCliente`| anônimo (stub local)     | Cliente      | Callback de avisos e times (passagem por referência)|

A passagem por referência é demonstrada **três vezes**: o cliente faz `Naming.lookup("ServicoRacha")`; o servidor devolve no JSON do LOGIN o nome `"Sessao:N"` (cliente faz novo lookup); o cliente exporta um `NotificadorImpl` e entrega o stub ao servidor via `SessaoRemota.registrarNotificador(stub)`.

### Protocolo requisição-resposta (Coulouris, Fig. 5.4)

```
Cliente                                                Servidor
─────────                                              ─────────
doOperation(ref, methodId, args)                       ServicoRachaImpl / SessaoRemotaImpl
  monta RequestMessage                                   invocar(byte[] req)
  {                                                        getRequest()    ← desempacota
    "messageType": 0,                                      select object   ← dispatcher por methodId
    "requestId":   N,                                      execute method  ← lógica de negócio
    "objectReference": "ServicoRacha" | "Sessao:N",        sendReply()     ← empacota result
    "methodId": M,
    "args": { ... }
  }
  → stub.invocar(reqBytes)
                                                       ReplyMessage
                                                       {
                                                         "messageType": 1,
                                                         "requestId":   N,
                                                         "ok": true,
                                                         "result": { "ok": true|false, ... }
                                                       }
  desempacota, valida requestId,
  devolve só o payload "result"
```

### Tabela de `methodId`

| ID | Objeto         | Método             | Perfil   | `args`                                          | `result`                              |
|---:|----------------|--------------------|----------|------------------------------------------------|---------------------------------------|
| 1  | ServicoRacha   | LOGIN              | qualquer | `{nome, senha}`                                | `{ok, sessaoRef, tipo, idUsuario}`    |
| 2  | SessaoRemota   | LISTAR_JOGADORES   | qualquer | `{}`                                           | `{ok, jogadores:[...], sistemaAberto}`|
| 3  | SessaoRemota   | AVALIAR            | jogador  | `{idAvaliado, nota}`                           | `{ok}` ou `{ok:false, erro}`          |
| 4  | SessaoRemota   | ADICIONAR_JOGADOR  | admin    | `{nome, senha, posicao}`                       | `{ok, id}`                            |
| 5  | SessaoRemota   | REMOVER_JOGADOR    | admin    | `{id}`                                         | `{ok}`                                |
| 6  | SessaoRemota   | ENVIAR_AVISO       | admin    | `{mensagem}`                                   | `{ok}`                                |
| 7  | SessaoRemota   | ENCERRAR_AVALIACOES| admin    | `{qtdTimes}`                                   | `{ok, racha:{id, qtdTimes, times:[...]}}` |
| 8  | SessaoRemota   | LOGOUT             | qualquer | `{}`                                           | `{ok}`                                |

`SessaoRemota.registrarNotificador(NotificadorCliente)` é um método remoto Java **direto** (não passa por `methodId`): demonstra a passagem do stub do cliente por referência.

### Passagem por valor

Todos os payloads dentro de `args` e `result` são serializados em **JSON UTF-8** pelo `Marshaller` / `utils.Json`. Não é usado `java.io.Serializable` em nenhum lugar do trabalho — a representação externa é totalmente controlada por nós.

---

## Modelagem de domínio

```
Usuario  (id, nome, senha)
   ↑
   ├── Jogador        (implements Avaliavel; tem Posicao, somaNotas, qtdNotas)
   └── Administrador  (tem nivelAcesso)

Avaliacao  (idAvaliador, idAvaliado, nota)
Time       (numero; TEM-UMA List<Jogador>)
Racha      (id, data; TEM-UMA List<Time>)         ← agregação em cadeia: Racha → Time → Jogador
Sessao     (id, criadaEm; TEM-UM Usuario; rastreia requestId)
```

**Mapeamento das exigências do enunciado:**

| Requisito                          | Atendimento                                                             |
|------------------------------------|-------------------------------------------------------------------------|
| ≥ 4 entidades                      | `Usuario`, `Jogador`, `Administrador`, `Time`, `Racha`, `Avaliacao`, `Sessao` |
| ≥ 2 "é-um" (extensão)              | `Jogador extends Usuario`, `Administrador extends Usuario`              |
| ≥ 2 "tem-um" (agregação)           | `Time` tem `List<Jogador>`, `Racha` tem `List<Time>`, `Sessao` tem `Usuario` |
| ≥ 4 métodos remotos                | 8 `methodId`s + `registrarNotificador`                                  |
| Passagem por referência            | `ServicoRacha`, `SessaoRemota`, `NotificadorCliente` (todos `Remote`)   |
| Passagem por valor + repr. externa | DTOs em JSON UTF-8 dentro de `args`/`result`                            |

---

## Como compilar e executar

**Pré-requisito:** JDK 17+ instalado.

```bat
:: 1. Compilar
compile.bat

:: 2. Iniciar servidor (mantém aberto, escuta na porta 1099)
servidor.bat

:: 3. Iniciar um ou mais clientes em terminais separados
cliente.bat
```

Argumentos opcionais:

```bat
servidor.bat 5099            :: usa porta 5099 no Registry
cliente.bat   localhost 5099 :: conecta na porta especificada
```

---

## Testes

Três suítes, todas executáveis sem rede externa nem JUnit:

```bat
java -cp out testes.TesteMarshalling   :: 41 asserts — round-trip JSON e envelopes
java -cp out testes.TesteRMIDummy      :: 7 asserts  — ClienteRMI isolado contra dummies
java -cp out testes.TesteIntegracao    :: 34 asserts — ponta-a-ponta no servidor real,
                                          ::             cobre todos os methodIds + callback
```

`TesteIntegracao` sobe o `ServicoRachaImpl` real *in-process* em uma porta livre, executa LOGIN (admin e jogador), LISTAR (omissão do próprio jogador), AVALIAR (sucesso, duplicado, auto), ADICIONAR/REMOVER (admin), AVISO (com callback), ENCERRAR (gera Racha + 2 times) e LOGOUT — incluindo os caminhos de erro de autorização e de sistema encerrado.

---

## Conformidade com o enunciado

- ✅ **"Não crie sockets nesse trabalho"**: zero uso direto de `Socket`/`DatagramSocket`/`ServerSocket` no caminho RMI. O `rmiregistry` é responsabilidade do JDK.
- ✅ **doOperation/getRequest/sendReply seguidas**: as três primitivas estão visíveis em `ClienteRMI.doOperation`, e no servidor o ciclo `desempacotar → dispatcher → empacotar` é explícito em `ServicoRachaImpl.invocar` / `SessaoRemotaImpl.invocar`.
- ✅ **Estrutura `{messageType, requestId, objectReference, methodId, arguments}`**: implementada literalmente em `rmi/RequestMessage.java` e o complemento em `rmi/ReplyMessage.java`, com `messageType=0` para Request e `messageType=1` para Reply.
- ✅ **`requestId` único e validado**: gerado por `AtomicInteger` no cliente, ecoado pelo servidor, conferido em `ClienteRMI.doOperation` — divergência lança `RemoteException`.
- ✅ **Multicast removido do caminho ativo**: substituído por callbacks `NotificadorCliente`. O `ClienteMulticast` legado fica em `network/` apenas como referência histórica.

---

## Fases do desenvolvimento

- [x] **Fase 0** — Kickoff: contrato (`CONTRATO.md`)
- [x] **Fase 1a** — Infra RMI: `RequestMessage`, `ReplyMessage`, `Marshaller`, interfaces `Remote`
- [x] **Fase 1b** — Domínio: `Sessao`, `Racha`, reuso das entidades do Trabalho 1
- [x] **Fase 2a** — `ClienteRMI.doOperation`, scripts, smoke test
- [x] **Fase 2b** — `ServicoRachaImpl` com dispatcher + LOGIN + LISTAR
- [x] **Fase 3**  — Demais métodos: AVALIAR, ADICIONAR, REMOVER, AVISO, ENCERRAR
- [x] **Fase 4**  — Callbacks `NotificadorCliente` substituindo multicast UDP
- [x] **Fase 5**  — Teste de integração completo, CLI polida, relatório (`RELATORIO.md`)
