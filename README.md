# Sistema de Divisão de Times (Sistemas Distribuídos)

Projeto desenvolvido para a disciplina de Sistemas Distribuídos, abordando comunicação entre processos, sockets (TCP/UDP), streams customizados e representação externa de dados (JSON).

## Domínio

Sistema de ranqueamento anônimo de jogadores que, ao término do período de avaliação, gera times balanceados automaticamente.

## Pré-requisitos

- Sistema Operacional: Ubuntu (ou distribuição Linux baseada em Debian)
- Java Development Kit (JDK) 17 ou superior
- Git

## Estrutura do Projeto

```
sistema-divisao-times/
├── src/
│   ├── models/      # POJOs do domínio (Jogador, Time, Administrador...)
│   ├── streams/     # Streams customizados (JogadorOutputStream/InputStream)
│   ├── network/     # Servidor TCP, Cliente, Multicast UDP
│   └── utils/       # Utilitários de serialização (JSON)
└── README.md
```

## Como compilar e executar

*(Instruções de execução serão adicionadas conforme o desenvolvimento avança.)*

## Fases de Desenvolvimento

- [x] Fase 1 — Setup inicial e estrutura de diretórios
- [x] Fase 2 — Modelagem de dados (POJOs) e interfaces
- [x] Fase 3 — Streams customizados (`JogadorOutputStream` / `JogadorInputStream`)
- [x] Fase 4 — Sockets básicos e serialização manual
- [x] Fase 5 — Servidor multi-threaded (Unicast TCP)
- [ ] Fase 6 — Multicast UDP e representação externa (JSON)
