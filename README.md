# Finlytics

Aplicativo Android de controle financeiro pessoal, desenvolvido como Trabalho de Conclusão de Curso (TCC). Permite registrar receitas e despesas, acompanhar saldo e metas, e visualizar relatórios detalhados sobre os hábitos financeiros do usuário.

## Funcionalidades

### Autenticação
- Cadastro e login por e-mail/senha (Firebase Authentication)
- Recuperação de senha

### Dashboard (Início)
- Saudação personalizada e saldo consolidado
- Totais de receitas e despesas, com variação em relação ao período anterior
- Busca rápida entre os lançamentos
- Atalho para análise de gastos por forma de pagamento

### Transações
- Cadastro de receitas e despesas, com categoria, forma de pagamento, data e descrição
- Edição e exclusão de lançamentos
- Filtros por mês, tipo (receita/despesa), categoria e forma de pagamento

### Relatórios
- Resumo do período: total de receitas, despesas e saldo
- Gráfico mensal de receitas x despesas
- Distribuição de gastos por categoria (gráfico de pizza)
- Análise detalhada por forma de pagamento
- Insights automáticos: média de gasto diário, maior despesa/receita do mês, comparação com o mês anterior, variação por categoria
- Gasto por dia da semana
- Progresso das metas cadastradas

### Recorrências
- Cadastro de receitas e despesas fixas mensais (salário, aluguel, assinaturas etc.)
- Lançamento automático no dia configurado de cada mês

### Categorias
- Categorias de receita e despesa totalmente personalizáveis pelo usuário

### Metas
- Limite de gasto mensal, geral ou por categoria
- Meta de lucro mensal (receitas menos despesas)
- Acompanhamento do progresso em Relatórios

### Perfil e Configurações
- Edição do nome de usuário
- Tema claro, escuro ou automático (segue o sistema)

## Tecnologias

- **Kotlin** — linguagem principal
- **Android SDK** (min SDK 24, target/compile SDK 35)
- **Firebase Authentication** — autenticação de usuários
- **Firebase Realtime Database** — persistência dos dados
- **MPAndroidChart** — gráficos de barras e pizza
- **Material Components** — componentes visuais
- **View Binding / Fragments** — arquitetura de telas

## Estrutura do projeto

```
app/src/main/java/com/borgeiz/meutcc2026/
├── adapter/        # Adapters de RecyclerView
├── data/           # Repositórios de acesso ao Firebase
├── model/          # Modelos de dados (Transaction, Goal, RecurringItem...)
├── util/           # Funções utilitárias (gráficos, agregações, parsing)
├── *Activity.kt    # Telas de autenticação e edição
└── *Fragment.kt    # Telas principais (Dashboard, Transações, Relatórios, Perfil)
```

## Como rodar o projeto

1. Clone o repositório.
2. Crie um projeto no [Firebase Console](https://console.firebase.google.com/) com **Authentication** (e-mail/senha) e **Realtime Database** habilitados.
3. Baixe o arquivo `google-services.json` do seu projeto Firebase e coloque em `app/`.
4. Abra o projeto no Android Studio e sincronize o Gradle.
5. Rode em um emulador ou dispositivo físico com Android 7.0 (API 24) ou superior.

## Autor

Desenvolvido por [borgeizdev](https://github.com/borgeizdev) como Trabalho de Conclusão de Curso.
