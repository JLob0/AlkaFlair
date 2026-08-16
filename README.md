<div align="center">

# AlkaFlair

### Identidade cosmética: tags e medalhas num só lugar

Prefixos/sufixos equipáveis e badges colecionáveis, sempre exibidos juntos —
construído sobre o **AlkaCore**.

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)
![Version](https://img.shields.io/badge/Version-1.0.1-blue)
![License](https://img.shields.io/badge/License-Proprietary-red)

</div>

---

## 📋 Sobre o Projeto

O **AlkaFlair** une em um único plugin o que normalmente seriam dois: tags
cosméticas (prefixo/sufixo equipável) e medalhas (badges colecionáveis). As
duas coisas compartilham a mesma ideia — identidade visual do jogador — e
por isso ficam sempre exibidas juntas na mesma linha de tab/chat.

## ✨ Funcionalidades Principais

- 🏷️ **Tags** — prefixo/sufixo cosmético, uma equipada por vez. Compráveis
  em qualquer moeda do AlkaEconomy ou concedidas por permissão, com
  categorias organizadas, aliases rápidos e cooldown entre trocas.
- 🎖️ **Medalhas** — badges colecionáveis, várias equipadas ao mesmo tempo até
  um limite configurável de slots por jogador.
- 🎁 **Vouchers físicos** — itens resgatáveis com um clique, seja pra uma tag
  individual, um pacote de tags ou uma medalha.
- ✍️ **Prefixo/sufixo personalizado** — administradores podem definir um
  visual único por jogador, fora do catálogo padrão de tags.
- 🔌 **API pública** — pronta pra integrações futuras, como outros plugins
  concedendo tags automaticamente por eventos (ex.: compra de VIP).
- 🔤 **Placeholders completos** — expostos pra qualquer plugin de
  tablist/chat (TAB, nChat) exibir tags e medalhas sem esforço extra.

## 🔗 Integrações

Construído sobre o **AlkaCore** e o **AlkaEconomy** (moeda de compra de
tags). Expõe placeholders consumidos por **PlaceholderAPI**, **TAB** e
**nChat**. Compatível com **LuckPerms**.

## 🔧 Tecnologias Utilizadas

- **Java 21** · **Paper API 1.21.8**
- **AlkaCore** (banco de dados e GUI compartilhados)
- **AlkaEconomy** (moeda de compra)

## ⚙️ Instalação

1. Instale o **AlkaCore** e o **AlkaEconomy** antes (dependências obrigatórias).
2. Coloque `AlkaFlair.jar` na pasta `plugins/` do servidor.
3. Reinicie o servidor.
4. Configure tags, medalhas e categorias em `tags.yml`/`medals.yml`.

## 🎮 Comandos

| Comando | Descrição |
| --- | --- |
| `/tags` | Abre o menu de tags |
| `/tags <alias>` | Equipa uma tag rapidamente pelo apelido |
| `/tags setar` | Define um prefixo/sufixo personalizado (admin) |
| `/medals` | Abre o menu de medalhas |
| `/medals add` | Concede uma medalha a um jogador (admin) |

## 📝 Licença

> ⚠️ **Projeto proprietário da AlkaStudio.**
>
> Código fonte destinado exclusivamente ao uso interno da rede `Alka*`.
> Reprodução, distribuição ou uso não autorizado não são permitidos.

## 🎯 Créditos

- **Desenvolvido por**: MestreDEV — AlkaStudio
- **Parte do ecossistema**: `Alka*`

---

<div align="center">

**Desenvolvido com ❤️ pela AlkaStudio**

[![AlkaStudio](https://img.shields.io/badge/AlkaStudio-JLob0-blue)](https://github.com/JLob0)

</div>
