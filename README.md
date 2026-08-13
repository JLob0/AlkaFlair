# AlkaFlair

Tags cosméticas (prefixo/sufixo equipável, comprável) + medalhas (badges multi-equip
por permissão) num único plugin, para a rede AlkaStudio (Paper 1.21.8 / Java 21) —
construído sobre o AlkaCore. Une o que seriam dois plugins separados (AlkaTags +
AlkaMedals) porque as duas coisas são a mesma ideia (identidade cosmética
equipável), sempre exibidas juntas na mesma linha de tab/chat.

## O que faz

- **Tags** — cosmético de prefixo/sufixo, uma equipada por vez. Não substitui o
  grupo do LuckPerms, só sobrepõe visualmente via placeholder. Compráveis (por
  qualquer moeda da AlkaEconomy) ou concedidas por permissão/admin. Categorias com
  ordenação, aliases pra equipar rápido (`/tags dragao`), cooldown configurável
  entre trocas, título/som de feedback ao trocar.
- **Medalhas** — badges, várias equipadas ao mesmo tempo (até um limite de slots
  configurável por jogador). Sem compra: desbloqueiam por permissão já concedida em
  algum grupo, ou por `/medals add`/voucher admin-dado.
- **Vouchers físicos** — item com PDC, clique direito resgata (tag individual,
  pacote de várias tags, ou medalha).
- **Prefixo/sufixo próprio** (`/tags setar`) — admin define um prefixo/sufixo
  customizado por jogador, fora do catálogo de tags (ex: para nomes especiais
  únicos).
- **API pública** (`AlkaFlairAPI`, `ServicesManager`) — pensada pra integrações
  futuras tipo "AlkaVips concede uma tag automaticamente na compra do VIP" sem o
  consumidor importar nenhuma classe interna daqui.
- **Placeholders** (`%alkaflair_tag%`, `%alkaflair_tag_prefix%`,
  `%alkaflair_tag_suffix%`, `%alkaflair_has_tag_<id>%`, `%alkaflair_medals%`,
  `%alkaflair_medal_slots%`, etc.) — consumidos pelo TAB/nChat. **AlkaFlair não
  controla nametag/tablist/holograma diretamente** — isso já é papel do TAB na
  rede, então o plugin só expõe os placeholders, nunca reimplementa a exibição.

## Dependências

- **AlkaCore** e **AlkaEconomy** (hard dependency) — GUI compartilhada e moeda de
  compra de tags.
- **PlaceholderAPI**, **LuckPerms** — soft-dependency.

## Limitações conhecidas (v1.0.0)

- Sem sub-comando `/tags forcar <jogador>` (abrir o menu remotamente em outro
  jogador) — fora de escopo desta versão, fácil de adicionar depois.
- Vouchers de tag/medalha não têm quantidade em pilha configurável além do simples
  `amount` no comando de dar.
- `%alkaflair_has_tag_<id>%`/`%alkaflair_has_medal_<id>%` e a checagem de
  permission-gate só são confiáveis para jogador **online** (permissão é lida ao
  vivo via `Player#hasPermission`, não há consulta ao LuckPerms offline).

## Origem

Especificação inicial em `Plugins_Antigos/AlkaTags_AlkaMedals_Spec.md` (dois
plugins separados) — refinada em conversa com o usuário para um único plugin
(nome escolhido: AlkaFlair) dado o alto acoplamento conceitual entre os dois
sistemas. Configs de referência (`tags.yml`/`medals.yml`/categorias/vouchers)
inspiradas na estrutura real de dois plugins comerciais equivalentes (LeafTags/
LeafMedals) encontrados em `Plugins_Antigos/AlkaFlair/` — **só os arquivos YAML de
config foram usados como referência de escopo/features**, nenhum código Java
desses plugins (pagos, sem licença de redistribuição, a maior parte sequer
descompilável de verdade — só um bootstrap loader) foi lido ou copiado. O sistema
de nametag/holograma/scoreboard-teams do LeafTags (a funcionalidade mais complexa
dele) foi deliberadamente deixado de fora — esse papel já é do TAB na rede.
