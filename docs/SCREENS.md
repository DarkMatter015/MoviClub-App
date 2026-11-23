# Especificação de Fluxos de Telas e Navegação — MoviClub

---

## 1. Arquitetura de Navegação Geral

O aplicativo utiliza um único `NavHost` no Jetpack Compose dividido em **3 grupos de rotas**:
1. **Fluxo de Autenticação (Sem Bottom Bar):** Acesso inicial e configuração de primeiro uso.
2. **Fluxo Principal (Com Bottom Bar de 4 abas):** `Início`, `Treinos`, `Social` e `Perfil`. A navegação entre abas deve preservar o estado (`saveState = true`, `restoreState = true`, `launchSingleTop = true`).
3. **Fluxos Imersivos e Secundários (Sem Bottom Bar):** Gravação ativa de treino, pós-treino, detalhes, criação de grupos e diagnóstico de hardware.

### Mapa de Navegação

```text
[Splash / Session Check]
   ├── (Não autenticado) ──> [Login Google] ──> (1º acesso) ──> [Onboarding] ──┐
   └── (Autenticado) ──────────────────────────────────────────────────────────┤
                                                                               ▼
┌────────────────────────────────── BOTTOM BAR ─────────────────────────────────┐
│  [1. Início]            [2. Treinos]           [3. Social]         [4. Perfil]│
│     │                      │                      │                   │       │
│     ├─> [Gravação Ativa]   ├─> [Gravação Ativa]   ├─> [Criar Grupo]   ├─> [Editar Perfil]
│     ├─> (Aba Social)       ├─> [Detalhe Treino]   ├─> [Detalhe Grupo] ├─> [Sensores/Calib.]
│     └─> (Aba Treinos)      │                      └─> [Convite/Busca] ├─> [Fila Offline]
│                            │                                          ├─> [Metas/Notif.]
│                            ▼                                          └─> [Ajuda / Sobre]
│                  [Gravação Ativa] ──> [Resumo Pós-Treino] ──> (Volta p/ Início/Treinos)
└───────────────────────────────────────────────────────────────────────────────┘
```

### Comportamento Global: Banner de Treino Ativo
* Se o usuário iniciar um treino e minimizar a tela de gravação (via botão de recolher ou voltar), o `ForegroundService` continua ativo e um **banner fixo em Laranja (`#F97316`)** aparece imediatamente acima da `NavigationBar` em todas as abas principais, exibindo: `[Ícone Modalidade] Em andamento • MM:SS • Toque para retornar`.

---

## 2. Fluxo de Autenticação e Onboarding

### 2.1 Tela de Splash / Roteamento Inicial
* **Rota:** `splash`
* **Objetivo:** Validar sessão ativa no Firebase Auth e verificar se há treino interrompido inesperadamente no Room (`status = RECORDING`).
* **Fluxo:**
  1. Exibe logo minimalista **MoviClub** sobre fundo `#FAFAFA`.
  2. Verifica `FirebaseAuth.currentUser`:
     * **Nulo:** redireciona para `login`.
     * **Autenticado, mas perfil incompleto no Firestore/Room (sem `@username`):** redireciona para `onboarding`.
     * **Autenticado com perfil completo:** carrega cache local do Room e redireciona para `home`.
  3. **Recuperação de falha:** Caso exista uma sessão salva no Room com `status = RECORDING` (ex.: bateria acabou durante o treino), exibe um `AlertDialog` na `home` oferecendo **"Retomar gravação"** ou **"Finalizar e salvar o que foi registrado"**.

### 2.2 Tela de Login
* **Rota:** `login`
* **Objetivo:** Autenticação rápida e exclusiva via conta Google (*Credential Manager* + Firebase Auth).
* **Hierarquia Visual:**
  1. Identidade visual (Logotipo + subtítulo: *"Seu clube em constante movimento"*).
  2. Ilustração *flat* minimalista ou prévia limpa dos pilares (Sensores, Constância e Rankings).
  3. Botão primário em formato pílula: **"Continuar com Google"** (`52.dp` de altura).
  4. Nota discreta de privacidade informando uso apenas para perfil e rankings.
* **Fluxo de Ações:**
  1. Usuário toca em **"Continuar com Google"**.
  2. Exibe estado de carregamento (*loading indicator* no botão).
  3. **Sucesso:**
     * Se `uid` já existe no Firestore: sincroniza dados do perfil para o Room e navega para `home` (limpando a pilha de navegação).
     * Se é o primeiro acesso: navega para `onboarding`.
  4. **Erro / Cancelamento:** Exibe `Snackbar` informativa (ex.: *"Sem conexão com a internet para login inicial"* ou *"Login cancelado"*).

### 2.3 Tela de Onboarding Inicial (Primeiro Acesso)
* **Rota:** `onboarding`
* **Objetivo:** Configurar identidade social, meta semanal inicial e justificar permissões do Android antes dos pop-ups do sistema.
* **Fluxo em 3 Passos (Stepper Horizontal):**
  * **Passo 1 — Identidade no Clube:**
    * Pré-preenche nome e foto vindos da conta Google.
    * Campo obrigatório para definir `@username` único (validação em tempo real contra duplicidade no Firestore; apenas letras minúsculas, números e `_`).
  * **Passo 2 — Sua Meta Semanal:**
    * Seletor simples de objetivo semanal de treinos (ex.: `3x`, `4x`, `5x` por semana ou meta em pontos) para ativar a gamificação desde o primeiro dia.
  * **Passo 3 — Permissões de Monitoramento:**
    * Cartões explicativos transparentes antes de disparar os pedidos nativos:
      * *Reconhecimento de Atividade (`ACTIVITY_RECOGNITION`):* contagem precisa de passos.
      * *Localização (`ACCESS_FINE_LOCATION`):* mapeamento de trajetos e distância em Caminhada/Corrida.
      * *Notificações (`POST_NOTIFICATIONS`):* exibir o treino ativo em segundo plano e lembrar da ofensiva (*streak*).
    * Botão **"Permitir e Começar"** (ou link discreto *"Configurar depois"* para respeitar o usuário).
* **Conclusão:** Salva perfil inicial no Room e Firestore, e navega para `home`.

---

## 3. Fluxo Principal (Telas da Bottom Bar)

### 3.1 Tela Início (Home)
* **Rota:** `home`
* **Objetivo:** Central diária de engajamento, visualização de constância e atalho imediato para iniciar treinos.
* **Hierarquia Visual (de cima para baixo):**
  1. **TopAppBar:**
     * Saudação curta (*"Olá, [Primeiro Nome]"*).
     * **Chip de Ofensiva (Streak):** `🔥 X dias` em Laranja `#F97316` (se ainda não treinou hoje, fica em estilo *outlined* indicando que o dia está aberto; ao treinar, fica preenchido).
     * **Ícone de Sincronização:** Nuvem verde (`Sincronizado`) ou badge âmbar (`N pendente(s)`) — tocar abre a tela `sync_queue`.
  2. **Card Hero — Iniciar Treinamento:**
     * Card de destaque em Grafite `#0F172A` com texto branco.
     * Botão principal Laranja `#F97316`: **"Iniciar Treino"**.
     * 3 *chips* rápidos abaixo: `Caminhada` | `Corrida` | `Academia`.
  3. **Card — Progresso Semanal e Resumo do Dia:**
     * Pontos feitos hoje e tempo ativo do dia (em `JetBrains Mono`).
     * Barra de progresso da **Meta Semanal** (ex.: `2 de 4 treinos • +100 pts de bônus ao concluir`).
     * **Timeline da Semana:** 7 círculos (`S T Q Q S S D`) preenchidos em Laranja nos dias com treino válido e contorno pontilhado no dia atual.
  4. **Card — Posição no Ranking:**
     * Exibe Liga atual (ex.: *Liga Prata*), posição no mês (`#4`) e texto motivacional (*"Faltam 85 pts para alcançar o #3"*).
  5. **Seção — Amigos Hoje:**
     * Faixa horizontal compacta com avatares dos amigos que já registraram treino hoje (ex.: *"Ana, Lucas e +2 treinaram hoje"*) ou convite para adicionar amigos caso a lista esteja vazia.
  6. **Card — Último Treino:**
     * Resumo da atividade mais recente (modalidade, data, pontos e ícone de sync).
* **Fluxo de Ações:**
  * Tocar em **"Iniciar Treino"** $\rightarrow$ navega para `training` com foco na seleção de modalidade (ou abre *Bottom Sheet* rápido de escolha de modalidade para ir direto à `workout_active`).
  * Tocar em um dos 3 *chips* rápidos $\rightarrow$ vai direto para `workout_active/{modality}`.
  * Tocar no card de **Ranking** $\rightarrow$ troca para a aba `social` na sub-aba `Rankings`.
  * Tocar na seção **Amigos** $\rightarrow$ troca para a aba `social` na sub-aba `Amigos`.
  * Tocar no **Último Treino** $\rightarrow$ abre `workout_detail/{workoutId}`.
* **Estado Offline:** Funciona integralmente usando dados do Room; os blocos de Ranking e Amigos exibem o último snapshot em cache com selo discreto *"Offline • Atualizado às HH:MM"*.

---

### 3.2 Tela Treinos (Training Hub)
* **Rota:** `training`
* **Objetivo:** Gerenciar modalidades, visualizar evolução/nível em cada categoria, iniciar gravações e consultar histórico completo.
* **Hierarquia Visual:**
  1. **Seção — Modalidades e Níveis:**
     * 3 cards selecionáveis (carrossel horizontal ou coluna compacta):
       * **Caminhada** (Peso `$1.0\times$`) — Nível atual + barra de XP + ícones de sensores ativos (GPS + Passos + Acelerômetro).
       * **Academia** (Peso `$1.3\times$`) — Nível atual + barra de XP + ícones de sensores ativos (Acelerômetro + Timer).
       * **Corrida** (Peso `$1.6\times$`) — Nível atual + barra de XP + ícones de sensores ativos (GPS + Passos + Acelerômetro).
     * Botão de ação primário logo abaixo: **"Iniciar [Modalidade Selecionada]"** + ícone secundário de atalho para `sensor_diagnostics`.
  2. **Resumo de Estatísticas:**
     * Seletor segmentado: `Semana` | `Mês` | `Total`.
     * 3 cartões numéricos em `JetBrains Mono`: `Treinos`, `Pontos`, `Minutos Ativos`.
  3. **Histórico de Treinos:**
     * *Chips* de filtro: `Todos`, `Caminhada`, `Corrida`, `Academia`.
     * Lista vertical (`LazyColumn`) ordenada do mais recente ao mais antigo.
     * Cada item mostra: ícone da categoria, título/descrição (ou nome da modalidade), data/hora, duração, distância (se houver), pontos (`+145 pts`) e badge de status (`Sincronizado` ou `Aguardando rede`).
* **Fluxo de Ações:**
  * Selecionar um card de modalidade atualiza o texto do botão principal e destaca a borda do card em Laranja `#F97316`.
  * Tocar em **"Iniciar [Modalidade]"** $\rightarrow$ executa verificação de permissões e navega para `workout_active/{modality}`.
  * Tocar em qualquer item do histórico $\rightarrow$ navega para `workout_detail/{workoutId}`.
* **Estado Vazio (Sem treinos):** Exibe cartão limpo incentivando o primeiro treino para desbloquear o Nível 1 na modalidade escolhida.

---

### 3.3 Tela Social
* **Rota:** `social?tab={rankings|groups|friends}`
* **Objetivo:** Concentrar competitividade (ligas e rankings), comunidade (grupos privados/públicos) e rede de amigos.
* **Estrutura Fixa:** `SecondaryTabRow` no topo com 3 sub-abas: **Rankings**, **Grupos** e **Amigos**.

#### Sub-aba A: Rankings
* **Hierarquia Visual:**
  1. *Chips* de escopo: `Minha Liga` | `Amigos` | `Global`.
  2. **Banner do Ciclo Mensal:** Exibe o mês vigente (ex.: *Novembro 2026*), contagem regressiva para zeragem (*"Zera em 8 dias"*) e link **"Histórico"** (abre *Bottom Sheet* com posição final dos meses anteriores).
  3. **Indicador de Liga (quando em "Minha Liga"):** Mostra a faixa atual (*Bronze*, *Prata*, *Ouro* ou *Diamante*) e a barra de pontos para a próxima liga.
  4. **Pódio (Top 3):** Destaque visual minimalista para 1º, 2º e 3º lugares.
  5. **Lista Classificatória:** Posição (`#4`, `#5`...), foto, nome, quantidade de treinos no mês e pontuação em `JetBrains Mono`.
  6. **Rodapé Fixo (Sticky Row):** Caso a linha do usuário logado esteja fora da tela visível, fixa um card destacado com a posição atual dele na base da lista.
* **Fluxo de Ações:**
  * Alternar entre `Minha Liga`, `Amigos` e `Global` recarrega a lista correspondente (com *Pull-to-Refresh*).
  * Tocar em **"Histórico"** exibe os resultados consolidados dos meses passados (`monthly_rankings/{YYYY-MM}` anteriores).

#### Sub-aba B: Grupos
* **Hierarquia Visual:**
  1. **Barra de Ações Rápidas:** Dois botões lado a lado: **"+ Criar Grupo"** (preenchido) e **"Entrar com Código"** (contorno).
  2. **Seção — Meus Grupos:** Lista de grupos onde o usuário é membro. Cada card mostra nome do grupo, badge **`Privado`** (ícone de cadeado) ou **`Público`** (ícone de globo), número de membros e posição atual do usuário no grupo.
  3. **Seção — Explorar Grupos Públicos:** Campo de busca por nome + listagem de grupos abertos a todos com botão direto **"Participar"**.
* **Fluxo de Ações:**
  * Tocar em **"+ Criar Grupo"** $\rightarrow$ navega para `create_group`.
  * Tocar em **"Entrar com Código"** $\rightarrow$ abre `ModalBottomSheet` para digitar o código de 6 caracteres (ex.: `MOVI42`), valida no Firestore e adiciona o usuário ao grupo (privado ou público).
  * Tocar em **"Participar"** em um grupo público $\rightarrow$ vincula o `uid` ao grupo imediatamente e o move para "Meus Grupos".
  * Tocar em um card de "Meus Grupos" $\rightarrow$ navega para `group_detail/{groupId}`.

#### Sub-aba C: Amigos
* **Hierarquia Visual:**
  1. **Busca e Convite:** Campo para buscar usuários por `@username` + botão **"Convidar para o MoviClub"** (abre *Android Share Sheet* com texto de convite e `@username` do usuário).
  2. **Solicitações Pendentes (condicional):** Se houver pedidos de amizade recebidos, exibe seção no topo com botões **"Aceitar"** e **"Recusar"**.
  3. **Lista de Amigos:** Foto, nome, `@username`, **Nota do Perfil** (`Nota 84`), *streak* atual (`🔥 5d`) e indicador verde se treinou hoje.
* **Fluxo de Ações:**
  * Digitar na busca filtra usuários no Firestore; tocar em **"Adicionar"** envia solicitação de amizade.
  * Tocar no menu de três pontos (`⋮`) de um amigo permite **"Remover amigo"**.
* **Estado Offline na Tela Social:** Exibe dados em cache quando disponíveis; ações que exigem escrita imediata na nuvem (criar grupo, adicionar amigo, buscar grupos públicos) ficam desabilitadas com aviso claro *"Conecte-se à internet para usar recursos sociais"*.

---

### 3.4 Tela Perfil (Menu e Configurações)
* **Rota:** `profile`
* **Objetivo:** Exibir a identidade e reputação de longo prazo do atleta, além de centralizar todas as configurações e ferramentas técnicas do app.
* **Hierarquia Visual:**
  1. **Card do Atleta:**
     * Foto, nome, `@username` e ícone de edição (`edit_profile`).
     * **Destaque da Nota do Perfil (0 a 100):** Gráfico circular minimalista (*circular gauge*) com legenda *"Baseada na sua constância e intensidade nos últimos 30 dias"*.
     * 3 indicadores rápidos abaixo: `Melhor Streak`, `Total de Treinos`, `Liga Atual`.
  2. **Seção — Treino & Hardware:**
     * **Metas e Preferências de Treino** $\rightarrow$ abre `workout_preferences`.
     * **Sensores e Calibração** $\rightarrow$ abre `sensor_diagnostics`.
     * **Sincronização Offline** (mostra subtítulo dinâmico: *"Tudo sincronizado"* ou *"2 treinos na fila"*) $\rightarrow$ abre `sync_queue`.
  3. **Seção — Aplicativo:**
     * **Notificações** (switches rápidos para lembrete diário de *streak* e alertas de ranking).
     * **Privacidade** (switch para exibir ou ocultar perfil no Ranking Global).
     * **Ajuda e Sobre o MoviClub** (versão, explicação da fórmula de pontuação e pesos por modalidade).
  4. **Ação de Saída:**
     * Botão de texto/contorno em Vermelho `#DC2626`: **"Sair da Conta"**.
* **Regra de Segurança no Logout:**
  * Antes de desconectar do Firebase Auth, o app verifica se há itens `PENDING` no Room.
  * Se houver treinos não sincronizados, exibe alerta: *"Você tem X treino(s) salvo(s) apenas neste dispositivo. Sair agora descartará dados locais não enviados. Deseja sincronizar antes?"*.

---

## 4. Fluxo Crítico de Treino (Sem Bottom Bar)

### 4.1 Pré-Validação ao Iniciar Treino
Antes de abrir `workout_active/{modality}`, o ViewModel executa checagens automáticas:
1. **Modalidades Externas (`Caminhada` e `Corrida`):**
   * Verifica `ACCESS_FINE_LOCATION` e se o GPS do aparelho está ligado.
   * **Se o usuário negar GPS ou o GPS estiver desligado:** exibe diálogo oferecendo **"Ativar GPS"** ou **"Continuar apenas com Acelerômetro"** (degradação graciosa: registra tempo, passos e intensidade, com distância zerada).
2. **Modalidade Interna (`Academia`):**
   * Não exige GPS; verifica apenas se o acelerômetro está disponível.

### 4.2 Tela de Gravação Ativa (Monitoramento)
* **Rota:** `workout_active/{modality}`
* **Objetivo:** Registrar o exercício com máxima legibilidade em movimento, baixíssimo risco de toque acidental e suporte completo a segundo plano (`ForegroundService`).
* **Hierarquia Visual:**
  1. **Cabeçalho Minimalista:**
     * Botão discreto de minimizar (`⌄`) no canto esquerdo (volta para `home` mantendo o banner ativo).
     * Tag da modalidade (ex.: `CORRIDA • 1.6x`).
     * Indicadores de sinal no canto direito: ícone de GPS (Verde = preciso $\le 25\text{m}$, Âmbar = buscando sinal, Cinza = desligado na Academia) + botão **"Ver Sensores"** (alterna para um *overlay/card* técnico em tempo real).
  2. **Bloco Central — Telemetria em Tempo Real (`JetBrains Mono` com `tnum`):**
     * **Cronômetro Gigante** (`00:18:42`) no centro superior.
     * **Grade de Métricas (2x2):**
       * *Caminhada / Corrida:* Distância (`km`), Passos, Intensidade de Movimento (`m/s²` média da janela), Pontos Estimados.
       * *Academia:* Tempo Ativo, Nível de Movimento (barra visual dinâmica), Intensidade Média, Pontos Estimados.
  3. **Área Visual Alternável (Mapa ou Monitor de Sensores):**
     * Em *Caminhada/Corrida*: Mini-mapa mostrando a polilinha azul/laranja com os pontos GPS marcados até o momento.
     * Em *Academia* (ou ao clicar em "Ver Sensores"): Gráfico de barras/linha em tempo real exibindo a magnitude do acelerômetro ($a = \sqrt{x^2 + y^2 + z^2}$) nos últimos 60 segundos.
  4. **Controles Inferiores (Alvos de toque grandes $\ge 56\text{dp}$):**
     * **Estado `RECORDING`:** Botão central largo **"Pausar"** (Grafite `#0F172A`).
     * **Estado `PAUSED`:** Dois botões lado a lado: **"Retomar"** (Laranja `#F97316`) e **"Segurar para Finalizar"** (Vermelho `#DC2626` com preenchimento circular progressivo de `1.5s` ao manter pressionado, prevenindo encerramentos acidentais).
* **Regras Técnicas durante a Tela:**
  * A tela não bloqueia a coleta ao desligar o visor: o `ForegroundService` exibe notificação fixa com cronômetro e botão "Pausar".
  * A cada 20 segundos, faz *checkpoint* no Room para proteção contra fechamento abrupto.
  * **Validação de duração mínima:** Ao tentar finalizar um treino com menos de 5 minutos (ou 1 minuto em build `DEBUG` para apresentação acadêmica), exibe diálogo: *"Treinos abaixo do tempo mínimo não pontuam para o ranking nem contam para a ofensiva. Deseja descartar ou continuar treinando?"*.

### 4.3 Tela de Resumo Pós-Treino (Recompensa)
* **Rota:** `workout_summary/{workoutId}`
* **Objetivo:** Registrar a descrição do usuário, celebrar a conquista (fator Duolingo) e confirmar o salvamento/enfileiramento.
* **Hierarquia Visual:**
  1. **Cabeçalho de Conquista:** Badge de modalidade + pontuação total ganha em destaque (`+215 PTS` em Laranja e `JetBrains Mono`).
  2. **Detalhamento dos Pontos (Transparência):**
     * Mostra brevemente a composição: Tempo Ativo + Distância (se houver) + Intensidade $\times$ Peso da Modalidade (`1.0x`, `1.3x` ou `1.6x`).
  3. **Feedback de Gamificação:**
     * Animação da barra de **Nível da Categoria** subindo (com aviso *"Subiu para o Nível 4!"* quando aplicável).
     * Card de confirmação de **Streak** (`🔥 Ofensiva atualizada para 7 dias!`) e avanço na **Meta Semanal**.
  4. **Entrada do Usuário:**
     * Campo de texto opcional: **"Descrição do treino"** (ex.: *"Corrida leve no parque com chuva"*).
  5. **Status de Armazenamento:**
     * Banner discreto informando: *"Salvo no dispositivo e sincronizado com a nuvem"* (Verde) ou *"Salvo offline — será enviado automaticamente quando houver internet"* (Âmbar).
  6. **CTA Final:** Botão pílula **"Concluir e Voltar ao Início"**.
* **Fluxo de Ações:**
  * Ao tocar em **"Concluir"**, atualiza a descrição no Room, garante que o `SyncWorkoutWorker` foi enfileirado no WorkManager e navega para `home` (removendo `workout_active` e `workout_summary` da pilha para impedir que o botão "Voltar" reabra o treino encerrado).

### 4.4 Tela de Detalhe do Treino
* **Rota:** `workout_detail/{workoutId}`
* **Objetivo:** Inspeção completa de qualquer treino passado a partir da Home ou do Histórico em Treinos.
* **Hierarquia Visual:**
  1. **TopAppBar:** Modalidade + data/hora + menu (`⋮`) com opções **"Editar descrição"** e **"Excluir treino"**.
  2. **Mapa do Trajeto (Caminhada/Corrida):** Exibe o percurso completo com marcador de início, marcador de fim e pontos coletados.
  3. **Grade Completa de Métricas:** Duração, Pontos, Distância, Passos, Intensidade Média e Peso aplicado.
  4. **Gráfico de Intensidade:** Curva das janelas de 5 segundos do acelerômetro mostrando os momentos de maior esforço.
  5. **Descrição do Treino** e **Selo de Sincronização** (`Sincronizado em DD/MM HH:MM` ou `Pendente na fila offline` com botão *"Tentar enviar agora"*).

---

## 5. Fluxos Secundários Sociais

### 5.1 Tela Criar Grupo
* **Rota:** `create_group`
* **Objetivo:** Permitir criação de grupos de classificação privados ou públicos.
* **Campos e Controles:**
  1. **Nome do Grupo** (obrigatório, 3 a 40 caracteres).
  2. **Descrição curta** (opcional, até 140 caracteres).
  3. **Tipo de Visibilidade (Seletor em 2 cards claros):**
     * **Privado (Apenas convidados):** Não aparece na busca; entrada exclusiva via código ou link de convite.
     * **Público (Aberto a todos):** Visível na aba *Explorar Grupos Públicos*; qualquer usuário pode participar.
  4. Botão primário: **"Criar Grupo"**.
* **Fluxo:**
  1. Valida conectividade (exige internet).
  2. Gera `groupId` e `inviteCode` aleatório único de 6 caracteres.
  3. Cria documento em `groups/{groupId}` com o criador como `ownerUid` e primeiro membro.
  4. Navega diretamente para `group_detail/{groupId}` exibindo um diálogo pronto para compartilhar o código de convite.

### 5.2 Tela Detalhe do Grupo
* **Rota:** `group_detail/{groupId}`
* **Objetivo:** Visualizar classificação exclusiva do grupo e gerenciar participação.
* **Hierarquia Visual:**
  1. **Cabeçalho do Grupo:** Nome, descrição, badge (`Privado` ou `Público`), total de membros e botão **"Convidar"** (copia ou compartilha o código de convite via *Share Sheet*).
  2. **Seletor de Ciclo:** `Mês Atual` | `Mês Anterior` (histórico congelado).
  3. **Ranking dos Membros:** Lista ordenada por pontos do mês no grupo, com destaque na linha do usuário logado.
  4. **Menu (`⋮`):**
     * Para membros: **"Sair do grupo"**.
     * Para o criador (`ownerUid`): **"Editar grupo"** e **"Encerrar grupo"**.

---

## 6. Fluxos Secundários de Perfil, Hardware e Offline

### 6.1 Tela Diagnóstico e Calibração de Sensores
* **Rota:** `sensor_diagnostics`
* **Objetivo:** Atender ao requisito de configuração/visualização de sensores e servir como ferramenta prática de teste e validação técnica.
* **Hierarquia Visual:**
  1. **Card — Acelerômetro:**
     * Status do sensor (`Sensor.TYPE_LINEAR_ACCELERATION` ativo ou fallback `TYPE_ACCELEROMETER`).
     * Leituras ao vivo em `JetBrains Mono`: Eixo `X`, Eixo `Y`, Eixo `Z` e Magnitude resultante `$a$`.
     * Barra dinâmica que reage imediatamente ao chacoalhar/mover o celular.
  2. **Card — Detector de Passos:**
     * Informa se usa sensor de hardware (`TYPE_STEP_DETECTOR`) ou algoritmo de picos no acelerômetro.
     * Contador de teste ao vivo com botão **"Zerar teste"**.
  3. **Card — GPS / Localização:**
     * Status da permissão e do provedor.
     * Latitude, Longitude e **Precisão atual em metros** (ex.: `± 8m • Sinal Bom`).
  4. **Preferência de Sensibilidade:** Seletor simples (`Padrão` / `Alta`) para calibrar o limiar de detecção caso o aparelho do usuário tenha acelerômetro menos sensível.

### 6.2 Tela Central de Sincronização Offline
* **Rota:** `sync_queue`
* **Objetivo:** Dar visibilidade total à arquitetura *offline-first* (Room + WorkManager) e permitir sincronização manual sob demanda.
* **Hierarquia Visual:**
  1. **Banner de Conectividade:** `Online — Conectado ao Firebase` (Verde) ou `Offline — Salvando apenas localmente` (Âmbar).
  2. **Lista de Itens Pendentes (`syncStatus = PENDING` ou `FAILED`):**
     * Exibe cada treino aguardando envio com data, pontos e número de tentativas já realizadas pelo WorkManager.
  3. **Botão Principal:** **"Sincronizar Agora"** (dispara execução imediata do `SyncWorkoutWorker`).
  4. **Rodapé Informativo:** Exibe data/hora da última sincronização bem-sucedida.

### 6.3 Telas Auxiliares de Configuração
* **Editar Perfil (`edit_profile`):** Permite alterar nome de exibição e `@username` (com validação de unicidade).
* **Metas e Preferências (`workout_preferences`):** Ajustar meta semanal de treinos/pontos, ativar/desativar avisos sonoros ou vibração ao iniciar/pausar treino e manter tela ligada durante gravação (*Keep Screen On*).
* **Ajuda e Sobre (`about`):** Explica de forma visual a fórmula de pontuação, os multiplicadores (`1.0x`, `1.3x`, `1.6x`), as faixas de ligas mensais e o cálculo da Nota do Perfil.

---

## 7. Matriz Resumida de Tratamento de Exceções (Edge Cases)

| Cenário | Comportamento Esperado no App |
| :--- | :--- |
| **Treino gravado 100% em modo avião** | Grava normalmente no Room; calcula pontos, XP e *streak* localmente; entra na fila `PENDING`; assim que a rede voltar, o WorkManager envia ao Firestore via transação idempotente usando o `workoutId` (UUID). |
| **Treino offline feito no dia 30/11 e sincronizado só em 02/12** | O `SyncWorkoutWorker` utiliza a chave `monthKey` da data original do treino (`2026-11`), creditando corretamente no mês em que o exercício ocorreu sem distorcer o novo ciclo (`2026-12`). |
| **Perda de sinal GPS no meio de uma corrida** | O serviço continua gravando tempo, passos e acelerômetro normalmente; ignora leituras GPS com `accuracy > 25m` e exibe indicador âmbar *"Sinal GPS fraco"* sem interromper o treino. |
| **App fechado pelo sistema durante gravação** | O `ForegroundService` reduz drasticamente a chance de morte pelo Android; se ainda assim ocorrer (ex.: bateria zerada), o último *checkpoint* no Room (`status = RECORDING`) aciona o diálogo de recuperação na próxima abertura. |
| **Virada do mês com o app aberto** | As queries de ranking sempre derivam dinamicamente da chave atual `YYYY-MM`; ao mudar o mês, a aba Social já consulta a nova coleção zerada e move o mês anterior para o seletor "Histórico". |