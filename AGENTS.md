# AGENTS.md — Ledger

## Projeto
- Aplicativo pessoal de finanças, mobile-only e sem publicação planejada.
- Backend: Java 21, Spring Boot, Maven e PostgreSQL.
- Frontend: React Native, Expo e TypeScript.
- Priorize as convenções e a arquitetura já existentes no repositório; não introduza padrões ou dependências sem justificar.

## Regras de trabalho
- Antes de agir, inspecione apenas os arquivos necessários para a tarefa.
- Faça uma tarefa por vez e mantenha as alterações estritamente dentro do escopo solicitado.
- Explique brevemente o diagnóstico e o plano antes de mudanças que afetem vários arquivos ou camadas.
- Não altere arquivos, banco de dados, migrations, dependências ou configuração fora do escopo autorizado.
- Nunca leia, exponha ou modifique segredos, tokens ou credenciais. Preserve arquivos `.env`.
- Preserve alterações preexistentes no working tree. Não descarte, sobrescreva ou reorganize trabalho não relacionado.
- Não execute `git add`, `commit`, `checkout`, `reset`, `clean`, rebase ou push sem autorização explícita.
- Não execute migrations nem comandos destrutivos no banco sem autorização explícita.
- Não invente resultados de testes. Informe exatamente quais comandos foram executados e quais testes passaram, falharam ou não foram executados.
- Ao concluir, apresente resumo, arquivos alterados, testes executados e limitações conhecidas.

## Regras de domínio financeiro
- Use `BigDecimal` para valores monetários; não use `float` ou `double` para cálculos financeiros.
- O saldo atual da conta é a referência autoritativa.
- Compras no cartão de crédito são despesas, mas não reduzem o saldo da conta no momento da compra; o saldo é reduzido no pagamento da fatura.
- Débito e Pix reduzem o saldo da conta.
- Transações importadas do Open Finance são bloqueadas para edição manual; transações manuais podem ser editadas.
- Transferências para terceiros são despesas.
- Exclusões e cancelamentos devem respeitar o histórico/audit trail e as regras existentes para transações relacionadas a contas excluídas.
- Não altere essas regras de negócio por inferência. Se houver ambiguidade, apresente a dúvida antes de implementar.

## Qualidade e validação
- Siga a separação de responsabilidades e os padrões já adotados entre domínio, aplicação, infraestrutura e apresentação.
- Prefira mudanças pequenas e reutilização coerente; evite duplicação e abstrações desnecessárias.
- Atualize ou crie testes para o comportamento alterado.
- Execute primeiro os testes mais específicos e, quando viável, a suíte relevante.
- Se a suíte completa estiver bloqueada por falhas preexistentes ou dependências externas, identifique o bloqueio e não o atribua às mudanças sem evidência.
- Não amplie o escopo para corrigir problemas preexistentes sem autorização.


## Contexto para a evolução da v1.0 para a v2.0

- O Ledger é um app pessoal de gestão financeira. A v1.0 cobre os fluxos manuais. O objetivo da v2.0 é integrar Open Finance. O README da raiz descreve uma fase anterior e está desatualizado quanto ao frontend e às recorrências. Confira a implementação atual antes de usar exemplos antigos como contrato.
- Backend em `backend/src/main/java/com/pedro/ledger`. Os controladores REST e DTOs ficam em `infrastructure/web`, os serviços que coordenam os casos de uso em `application` e as regras e interfaces de repositório em `domain`. Os adaptadores de persistência em `infrastructure/persistence` usam mapeadores, entidades e Spring Data JPA. O PostgreSQL tem seu esquema em `backend/src/main/resources/schema.sql`.
- Contas, categorias, transações, recorrências e cartões têm módulos próprios. `Money` encapsula `BigDecimal` e moeda. `TransactionApplicationService` coordena a persistência e `TransactionProcessor` aplica, ajusta e estorna os efeitos no saldo. Transferências internas movimentam duas contas. O estorno registra `REVERSED` e segue as regras de contas inativas. As operações que alteram saldo usam bloqueio pessimista de conta.
- `RecurrenceScheduler`, `RecurrenceApplicationService` e `RecurrenceProcessor` são as entradas para a geração de lançamentos recorrentes. O fluxo de cartão separa cartão, compra, parcela, fatura e pagamento. `CreditCardPurchaseApplicationService` trata compras e `CreditCardInvoiceApplicationService` debita a conta no pagamento da fatura.
- `TransactionSource` já distingue `MANUAL` e `OPEN_FINANCE`. O domínio já tem proteção contra edição de importadas, e o contrato TypeScript já representa a origem. A integração com instituições e a ingestão automática são trabalho da v2.0. Qualquer mudança na forma de aplicar o saldo em uma importação deve respeitar as regras financeiras acima e ser definida antes da implementação.
- Frontend em `frontend/src`, com React Native, Expo Router e TypeScript. Rotas e navegação ficam em `app`, o cliente HTTP e os contratos REST em `services`, os provedores de estado em `contexts`, o comportamento reutilizável em `hooks` e as funções de cálculo e formatação em `utils`. O layout raiz monta os provedores. O código atual também contém uma camada desktop em `components/desktop`, selecionada no layout raiz, embora o escopo do produto seja mobile. Consulte `frontend/AGENTS.md` antes de alterar APIs do Expo.
- Para mudanças no backend, procure os testes por módulo em `backend/src/test/java`, incluindo domínio, serviços, persistência, REST e integração. Para mudanças no frontend, confira os `*.test.ts` e o script `test` em `frontend/package.json`, que executa uma lista explícita de arquivos.
