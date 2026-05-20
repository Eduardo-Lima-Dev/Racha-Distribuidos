# Relatório — Trabalho 2 (RMI)

**Disciplina:** Sistemas Distribuídos — QXD0043
**Curso:** UFC Quixadá (SI / ES / RC / CC / EC)
**Professor:** Rafael Braga
**Tema:** Sistema de ranqueamento e divisão de times de futebol via Remote Method Invocation
**Repositório:** https://github.com/Eduardo-Lima-Dev/Racha-Distribuidos

---

## 1. Visão geral do serviço

O sistema reimplementa a 4ª questão do Trabalho 1 sobre **Java RMI**, com a comunicação cliente-servidor organizada em torno do **protocolo requisição-resposta descrito por Coulouris** (seção 5.2, Fig. 5.2-5.4). Nenhum `Socket` / `DatagramSocket` / `ServerSocket` é usado no caminho ativo — toda a comunicação atravessa o RMI Registry do JDK.

O fluxo de uso é o mesmo do Trabalho 1:

1. Cada usuário (administrador ou jogador) autentica-se contra o servidor.
2. Durante a janela aberta, jogadores avaliam outros jogadores com notas de 0 a 10. Avaliações duplicadas e auto-avaliações são rejeitadas.
3. O administrador pode adicionar/remover jogadores e enviar avisos.
4. Ao encerrar as avaliações, o servidor balanceia os times via *snake draft* (ordena por média decrescente e distribui em zigue-zague) e entrega o resultado a todos os clientes conectados.

A novidade é a forma como tudo isso é **transportado**: as três primitivas `doOperation` / `getRequest` / `sendReply` foram materializadas como uma camada visível, com mensagens estruturadas conforme a Fig. 5.4 do livro.

---

## 2. Decisões de projeto

### 2.1 Por que Java RMI puro

A disciplina permite RPC ou RMI. Escolhemos **Java RMI puro** porque:

- Casa diretamente com a regra "não crie sockets" — o transporte é responsabilidade do JDK.
- Permite expor objetos remotos diferentes (`ServicoRacha`, `SessaoRemota`, `NotificadorCliente`) e demonstrar de forma natural a passagem por referência.
- Não exige dependências externas — todo o JSON manual já existia no Trabalho 1 e foi expandido.

A alternativa gRPC + Protocol Buffers foi descartada para não introduzir build-tool e geração de código no escopo de um trabalho de fim de semestre.

### 2.2 Por que JSON manual

O enunciado sugere protobuf mas aceita XML/JSON. Optamos por **JSON UTF-8 manual** para:

- Reaproveitar e expandir `JsonMensagem` do Trabalho 1.
- Manter o código legível (a inspeção de `args`/`result` durante o desenvolvimento é trivial).
- Provar que conseguimos implementar a representação externa de dados do zero — o `Marshaller` e o `utils.Json` cabem em poucas centenas de linhas.

O parser foi escrito **duas vezes** durante o projeto (uma vez em `marshalling/Json.java`, outra em `utils/Json.java`) porque o trabalho foi dividido em duas frentes paralelas que convergiram tarde. Ambos são funcionalmente equivalentes; manter os dois facilitou a integração sem rebases dolorosos.

### 2.3 Por que envelope `{messageType, requestId, ok, result|erro}`

A Fig. 5.4 define o envelope da Request (`messageType`, `requestId`, `objectReference`, `methodId`, `arguments`). Para a Reply, espelhamos a estrutura e tomamos duas decisões:

- **`messageType=1`** explicitamente, mesmo sendo redundante com o sentido do retorno — para refletir literalmente o que o livro descreve.
- **`requestId` ecoado** pelo servidor e validado pelo cliente. Divergência (resposta atrasada de uma chamada antiga) dispara `RemoteException` em `ClienteRMI.doOperation`. Cobre o cenário descrito na seção "Identificadores de mensagem" do livro.

O campo de payload (`arguments` na Fig. 5.4) foi quebrado em dois sub-campos no envelope JSON:

- `result` — quando a operação fluiu sem erro de protocolo.
- `erro` — quando o protocolo em si falhou (envelope mal-formado, `methodId` inválido, etc.).

Erros de **negócio** (avaliação duplicada, sistema encerrado, autorização) ficam dentro de `result` com `ok=false` e uma chave `erro`. Isso simplifica o caminho feliz no cliente: `doOperation` sempre devolve `result`; o chamador lê `ok`/`erro` no próprio payload.

### 2.4 Por que três objetos remotos

A passagem por referência exigida pelo enunciado precisa de **mais de um** objeto `Remote` para ser demonstrada de forma significativa. Adotamos:

| Objeto              | Demonstração                                                                                  |
|---------------------|-----------------------------------------------------------------------------------------------|
| `ServicoRacha`      | O cliente recebe o stub via `Naming.lookup("ServicoRacha")`.                                  |
| `SessaoRemota`      | O LOGIN devolve o nome `"Sessao:N"`; o cliente faz novo `lookup` e passa a usá-lo. Stub vive enquanto a sessão existir; o LOGOUT desexporta. |
| `NotificadorCliente`| O cliente exporta `NotificadorImpl` localmente e passa o stub via `SessaoRemota.registrarNotificador(stub)`. Substitui o multicast UDP. |

### 2.5 Por que callback em vez de multicast

O Trabalho 1 usava multicast UDP para entregar avisos e times finalizados. UDP/multicast usa `DatagramSocket`, vetado pelo enunciado. A solução foi inverter o sentido da invocação remota: **o servidor invoca o cliente** quando há algo a divulgar. Cada sessão guarda o stub `NotificadorCliente` registrado pelo seu dono; o `SessaoRemotaImpl.notificarTodos(...)` itera os stubs vivos e chama `notificar(byte[])`. Stubs mortos (cliente que caiu) são descartados quando a chamada lança `RemoteException`.

---

## 3. Modelagem de domínio

```
Usuario  ──(é-um)──→  Jogador  (implements Avaliavel)
                  └→  Administrador

Time     ──(tem-uma)──→ List<Jogador>
Racha    ──(tem-uma)──→ List<Time>           ← agregação em cadeia
Sessao   ──(tem-um)──→  Usuario              ← identidade do cliente autenticado
```

Atende às exigências do enunciado:

- **Entidades (≥ 4):** `Usuario`, `Jogador`, `Administrador`, `Time`, `Racha`, `Avaliacao`, `Sessao` — total de 7.
- **Composição "é-um" (≥ 2):** `Jogador extends Usuario`, `Administrador extends Usuario`.
- **Composição "tem-um" (≥ 2):** `Time` tem `List<Jogador>`, `Racha` tem `List<Time>`, `Sessao` tem `Usuario`.

`Sessao` é a entidade introduzida especificamente no Trabalho 2 — guarda o usuário autenticado, instante de criação e o último `requestId` observado (futura validação de duplicatas se o transporte mudar de TCP para UDP).

---

## 4. Métodos para invocação remota

Total: **8 métodos lógicos** identificados por `methodId` (excede os 4 mínimos), distribuídos entre dois objetos remotos. Mais um método remoto Java direto (`registrarNotificador`) para o callback.

| `methodId` | Objeto         | Método             | Perfil   |
|-----------:|----------------|--------------------|----------|
| 1          | `ServicoRacha` | `LOGIN`            | qualquer |
| 2          | `SessaoRemota` | `LISTAR_JOGADORES` | qualquer |
| 3          | `SessaoRemota` | `AVALIAR`          | jogador  |
| 4          | `SessaoRemota` | `ADICIONAR_JOGADOR`| admin    |
| 5          | `SessaoRemota` | `REMOVER_JOGADOR`  | admin    |
| 6          | `SessaoRemota` | `ENVIAR_AVISO`     | admin    |
| 7          | `SessaoRemota` | `ENCERRAR_AVALIACOES` | admin |
| 8          | `SessaoRemota` | `LOGOUT`           | qualquer |
| (direto)   | `SessaoRemota` | `registrarNotificador(NotificadorCliente)` | qualquer |

A escolha de concentrar quase tudo em `SessaoRemota` (e não no `ServicoRacha`) materializa o fluxo "sessão depois do login" típico de sistemas autenticados, e separa naturalmente o que **exige** identidade (qualquer método de sessão) do que **não** exige (apenas o LOGIN).

---

## 5. Detalhamento do protocolo requisição-resposta

### 5.1 As três primitivas

| Primitiva (livro)            | Onde está no código                                                                 |
|------------------------------|-------------------------------------------------------------------------------------|
| `doOperation(ref, mId, args)`| `client/ClienteRMI.java:doOperation` — empacota, invoca o stub, valida `requestId`, devolve `result`. |
| `getRequest()`               | `server/ServicoRachaImpl.invocar` / `SessaoRemotaImpl.invocar` — desempacota `RequestMessage`. |
| `sendReply(reply, host, port)`| `Marshaller.empacotarReply` chamado ao final de cada handler do dispatcher.         |

A assinatura original do livro foi adaptada (o RMI já carrega o endereço/porta do cliente automaticamente; explicitá-los seria redundante), mas o **papel** das três primitivas continua claro no código.

### 5.2 Estrutura das mensagens

Implementada literalmente em `rmi/RequestMessage.java`:

```java
public final byte    messageType;       // 0 = Request
public final int     requestId;         // sequência incrementada pelo cliente
public final String  objectReference;   // "ServicoRacha" ou "Sessao:N"
public final int     methodId;          // identificador do método lógico
public final byte[]  arguments;         // payload UTF-8 (JSON)
```

E em `rmi/ReplyMessage.java`:

```java
public final byte    messageType;       // 1 = Reply
public final int     requestId;         // ecoado da Request
public final boolean ok;
public final byte[]  arguments;         // payload UTF-8 do "result" (sucesso)
                                        // ou string-motivo (erro de protocolo)
```

Empacotamento JSON:

```json
{ "messageType": 0,
  "requestId": 42,
  "objectReference": "Sessao:7",
  "methodId": 3,
  "args": { "idAvaliado": 2, "nota": 8.5 } }

{ "messageType": 1,
  "requestId": 42,
  "ok": true,
  "result": { "ok": true } }
```

### 5.3 Identificador único e validação

O cliente gera `requestId` com um `AtomicInteger.getAndIncrement()` ao montar a `RequestMessage`. O servidor copia o valor no `ReplyMessage`. O cliente confere em `ClienteRMI.doOperation`:

```java
if (reply.requestId != requestId)
    throw new RemoteException("requestId divergente: enviado=" + requestId
            + " recebido=" + reply.requestId);
```

Isso atende ao requisito do livro sobre "verificar se uma mensagem de resposta é o resultado da requisição atual e não de uma chamada anterior atrasada".

### 5.4 Modelo de falhas

Como o RMI usa **TCP** por baixo, várias preocupações do protocolo sobre UDP não se aplicam aqui (livro, p. 191):

- **Retransmissão** — TCP cuida.
- **Histórico de respostas** — desnecessário; sem retransmissão, sem duplicatas no servidor.
- **Filtragem de duplicatas** — idem.
- **Timeouts** — herdam o timeout do socket TCP do RMI; `RemoteException` é lançada naturalmente.

Apenas **falhas de processo** (cliente cai com stub registrado) precisam de tratamento específico: o servidor remove o `NotificadorCliente` morto na primeira `RemoteException` recebida no callback.

### 5.5 Estilo de troca

É **RR (Request-Reply)**. A resposta do servidor serve como acknowledgement implícito da requisição, conforme descrição do livro.

---

## 6. Passagem de parâmetros

| Tipo                    | Mecanismo                                                                     | Exemplo                                                                 |
|-------------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Objeto remoto           | Stub RMI (`Remote`) — passagem por **referência**                              | `ServicoRacha`, `SessaoRemota`, `NotificadorCliente`                    |
| Tipo primitivo / String | Representação externa em JSON UTF-8 — passagem por **valor**                   | `nome`, `nota`, `qtdTimes` dentro de `args`                             |
| Entidade de domínio     | Convertida para um sub-objeto JSON antes de enviar; o cliente parseia de volta | `Jogador → {id, nome, posicao, media, qtdAvaliacoes}`                   |

Não se usa `java.io.Serializable` em parte alguma — a representação externa é totalmente controlada pelo `Marshaller` / `utils.Json`. Atende ao requisito de que "a passagem de parâmetro por valor deve utilizar a representação externa de dados".

---

## 7. Como compilar e executar

**Pré-requisito:** JDK 17+.

```bat
compile.bat                  :: compila tudo em out/
servidor.bat                 :: sobe Registry na 1099 e exporta ServicoRacha
cliente.bat                  :: abre a CLI (rodar várias vezes em terminais separados)
servidor.bat 5099            :: porta customizada
cliente.bat   localhost 5099 :: cliente apontado para porta customizada
```

A CLI do cliente cuida do LOGIN, oferece menus diferentes para Administrador e Jogador, e abre uma seção dedicada para AVALIAR / ENCERRAR conforme o perfil. Notificações por callback (`AVISO`, `TIMES`) interrompem o menu com uma caixa de uma única linha.

---

## 8. Testes

Três suítes auto-contidas (sem JUnit, sem dependências):

| Suíte                          | Asserts | Cobre                                                                 |
|--------------------------------|--------:|-----------------------------------------------------------------------|
| `testes.TesteMarshalling`      | 41      | Parser/writer JSON, escape, round-trip Request/Reply, ok vs erro.     |
| `testes.TesteRMIDummy`         | 7       | `ClienteRMI.doOperation` contra um servidor *dummy* in-process — valida lookup, marshalling, `requestId` monotônico e tratamento de ref desconhecida. |
| `testes.TesteIntegracao`       | 34      | Fluxo ponta-a-ponta contra o `ServicoRachaImpl` real: LOGIN (admin/jogador/inválido), LISTAR (omissão do próprio), AVALIAR (sucesso/duplicado/auto), ADICIONAR/REMOVER (admin), AVISO + callback, ENCERRAR (Racha + 2 times), LOGOUT, autorização (jogador não pode admin), avaliação após encerramento. |

Execução:

```bat
java -cp out testes.TesteMarshalling
java -cp out testes.TesteRMIDummy
java -cp out testes.TesteIntegracao
```

Todas terminam com **0 falhas**.

---

## 9. Conformidade com o enunciado

| Exigência                                                           | Onde                                                                 |
|---------------------------------------------------------------------|----------------------------------------------------------------------|
| Comunicação via RPC ou RMI                                           | Java RMI puro                                                       |
| **"Não crie sockets nesse trabalho"**                                | Caminho ativo sem `Socket`/`DatagramSocket`/`ServerSocket`           |
| Protocolo requisição-resposta (seção 5.2 do livro)                   | `client/ClienteRMI.java`, `server/*Impl.invocar`                     |
| Estrutura `{messageType, requestId, objectReference, methodId, arguments}` | `rmi/RequestMessage.java`, `rmi/ReplyMessage.java`            |
| `objectReference` e `methodID` como Strings/inteiros                 | `RemoteObjectRef` (String), `methodId` (int)                         |
| ≥ 4 entidades                                                        | 7 entidades em `models/`                                             |
| ≥ 2 "é-um"                                                            | `Jogador`, `Administrador` estendem `Usuario`                        |
| ≥ 2 "tem-um"                                                          | `Time` → `Jogador`, `Racha` → `Time`, `Sessao` → `Usuario`           |
| ≥ 4 métodos para invocação remota                                    | 8 `methodId`s + `registrarNotificador`                               |
| Passagem por referência para objetos remotos                         | Três interfaces `Remote` (ver §2.4)                                  |
| Passagem por valor com representação externa                          | JSON UTF-8 via `Marshaller` / `utils.Json`                           |

---

## 10. Estrutura de pastas

```
.
├── CONTRATO.md         contrato entre as duas frentes de trabalho
├── PLANO_TRABALHO2.md  plano detalhado e divisão de responsabilidades
├── RELATORIO.md        este documento
├── README.md           visão geral e instruções
├── compile.bat
├── servidor.bat
├── cliente.bat
├── src/
│   ├── models/         entidades de domínio
│   ├── rmi/            interfaces Remote + mensagens do protocolo
│   ├── marshalling/    Marshaller + Json (writer/parser)
│   ├── utils/          Json adicional usado no servidor
│   ├── server/         dispatcher, estado e bootstrap
│   ├── client/         ClienteRMI + CLI + NotificadorImpl
│   ├── network/        Trabalho 1 (TCP/UDP) — preservado para referência
│   ├── streams/        streams customizados do Trabalho 1
│   └── testes/         TesteMarshalling, TesteRMIDummy, TesteIntegracao
└── out/                .class compiladas (gerado por compile.bat)
```

---

## 11. Divisão do trabalho

A entrega foi dividida em duas frentes, conforme `PLANO_TRABALHO2.md`:

- **Pessoa A — Infraestrutura RMI + marshalling.** Mensagens, interfaces `Remote`, `Marshaller`, `Json`, `ClienteRMI.doOperation`, scripts, suítes de teste.
- **Pessoa B — Domínio + aplicação.** Entidades novas (`Sessao`, `Racha`), `EstadoServidor`, `ServicoRachaImpl`, `SessaoRemotaImpl`, `MainServidor`, `ClienteInterativo`, `NotificadorImpl`.

O `CONTRATO.md` (formalizado antes da implementação) fixou as assinaturas das interfaces remotas, o formato JSON dos envelopes e a tabela `methodId → método lógico`, o que permitiu que as duas frentes evoluíssem em paralelo sem bloqueio.
