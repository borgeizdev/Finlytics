<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Ícone do Finlytics" />

# Finlytics

**Controle financeiro pessoal na palma da mão.**

Registre receitas e despesas, acompanhe metas e visualize relatórios detalhados sobre seus hábitos financeiros.

[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=flat&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue)](#)
[![License](https://img.shields.io/badge/status-TCC-informational)](#)

</div>

---

## Sumário

- [Funcionalidades](#-funcionalidades)
- [Tecnologias](#-tecnologias)
- [Estrutura do projeto](#-estrutura-do-projeto)
- [Como rodar o projeto](#-como-rodar-o-projeto)
- [Autor](#-autor)

---

## 📱 Funcionalidades

### 🔐 Autenticação
- Cadastro e login por e-mail/senha (Firebase Authentication)
- Recuperação de senha

### 🏠 Dashboard (Início)
- Saudação personalizada e saldo consolidado
- Totais de receitas e despesas, com variação em relação ao período anterior
- Busca rápida entre os lançamentos
- Atalho para análise de gastos por forma de pagamento

### 💸 Transações
- Cadastro de receitas e despesas, com categoria, forma de pagamento, data e descrição
- Edição e exclusão de lançamentos
- Filtros por mês, tipo (receita/despesa), categoria e forma de pagamento

### 📊 Relatórios
- Resumo do período: total de receitas, despesas e saldo
- Gráfico mensal de receitas x despesas
- Distribuição de gastos por categoria (gráfico de pizza)
- Análise detalhada por forma de pagamento
- Insights automáticos: média de gasto diário, maior despesa/receita do mês, comparação com o mês anterior, variação por categoria
- Gasto por dia da semana
- Progresso das metas cadastradas

### 🔁 Recorrências
- Cadastro de receitas e despesas fixas mensais (salário, aluguel, assinaturas etc.)
- Lançamento automático no dia configurado de cada mês

### 🏷️ Categorias
- Categorias de receita e despesa totalmente personalizáveis pelo usuário

### 🎯 Metas
- Limite de gasto mensal, geral ou por categoria
- Meta de lucro mensal (receitas menos despesas)
- Acompanhamento do progresso em Relatórios

### ⚙️ Perfil e Configurações
- Edição do nome de usuário
- Tema claro, escuro ou automático (segue o sistema)

---

## 🛠️ Tecnologias

| Categoria         | Stack |
|--------------------|-------|
| Linguagem          | Kotlin |
| Plataforma         | Android SDK (min 24 · target/compile 35) |
| Autenticação       | Firebase Authentication |
| Banco de dados     | Firebase Realtime Database |
| Gráficos           | MPAndroidChart |
| UI                 | Material Components, Fragments |

---

## 📂 Estrutura do projeto

```
app/src/main/java/com/borgeiz/meutcc2026/
├── adapter/        # Adapters de RecyclerView
├── data/           # Repositórios de acesso ao Firebase
├── model/          # Modelos de dados (Transaction, Goal, RecurringItem...)
├── util/           # Funções utilitárias (gráficos, agregações, parsing)
├── *Activity.kt    # Telas de autenticação e edição
└── *Fragment.kt    # Telas principais (Dashboard, Transações, Relatórios, Perfil)
```

---

## 🚀 Como rodar o projeto

1. Clone o repositório.
2. Crie um projeto no [Firebase Console](https://console.firebase.google.com/) com **Authentication** (e-mail/senha) e **Realtime Database** habilitados.
3. Baixe o arquivo `google-services.json` do seu projeto Firebase e coloque em `app/`.
4. Abra o projeto no Android Studio e sincronize o Gradle.
5. Rode em um emulador ou dispositivo físico com Android 7.0 (API 24) ou superior.

---

## 👤 Autor

Desenvolvido por [**borgeizdev**](https://github.com/borgeizdev) como Trabalho de Conclusão de Curso.
