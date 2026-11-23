# Code Rules & Guidelines de Arquitetura — MoviClub

**Stack:** Kotlin • Jetpack Compose (Material 3) • Navigation Compose (Type-Safe) • Hilt (DI) •
Room • WorkManager • Firebase Auth & Cloud Firestore

**Padrão Arquitetural:** MVVM + UDF (*Unidirectional Data Flow*) + *Offline-First* + Organização
*Feature-First*

---

## 1. Princípios Arquiteturais

1. **Feature-First com `core/` modularizado:** Código específico de uma jornada fica isolado dentro
   da sua feature; infraestrutura, regras de negócio compartilhadas, sensores e Design System ficam
   em `core/`.
2. **Single Source of Truth (Offline-First):** As telas nunca leem treinos ou perfil do usuário
   diretamente do Firestore. A UI observa `Flow`s do **Room**, enquanto repositórios e o *
   *WorkManager** sincronizam com a nuvem em segundo plano.
3. **Unidirectional Data Flow (UDF):** O estado desce (`ViewModel` $\rightarrow$ `View`) via
   `StateFlow<UiState>` imutável, e as interações sobem (`View` $\rightarrow$ `ViewModel`) via
   eventos/ações (`onAction`).
4. **Separação Stateful vs. Stateless:** Telas e componentes de apresentação (`View`) são **100%
   stateless** (fáceis de testar e renderizar em `@Preview`). A ligação com `ViewModel`, estado e
   navegação acontece exclusivamente no arquivo `Routes`.
5. **Zero lógica de hardware na UI:** Acelerômetro, detector de passos e GPS nunca são instanciados
   em Composables ou ViewModels; eles residem em `core/hardware` e expõem fluxos prontos (
   `StateFlow`).

---

## 2. Estrutura de Pastas Oficial

```text
com.moviclub.app/
├── MoviClubApplication.kt             # Inicialização (Hilt + WorkManager config)
├── MainActivity.kt                    # Host único do Jetpack Compose
├── navigation/                        # Orquestração global de navegação
│   ├── MoviClubNavHost.kt             # Registra os grafos de todas as features
│   ├── BottomNavItem.kt               # Definição das 4 abas (Início, Treinos, Social, Perfil)
│   └── MoviClubAppScaffold.kt         # Scaffold global + BottomBar + Banner de Treino Ativo
│
├── core/                              # Camada base compartilhada ("back-end" local e UI base)
│   ├── data/                          # Persistência local, remoto e repositórios
│   │   ├── local/
│   │   │   ├── MoviClubDatabase.kt    # Room Database
│   │   │   ├── dao/                   # WorkoutDao, UserDao, SyncQueueDao
│   │   │   └── entity/                # Entidades Room (@Entity)
│   │   ├── remote/
│   │   │   ├── FirestoreDataSource.kt # Leituras/escritas e transações no Firestore
│   │   │   └── GoogleAuthClient.kt    # Credential Manager + Firebase Auth
│   │   ├── mapper/                    # Conversores Entity <-> Domain <-> Firestore DTO
│   │   └── repository/                # WorkoutRepository, UserRepository, SocialRepository
│   │
│   ├── domain/                        # Regras de negócio puras (Kotlin puro, sem Android SDK)
│   │   ├── model/                     # Modelos de domínio (Workout, Modality, League, UserProfile)
│   │   └── calculator/                # PointsCalculator, StreakCalculator, ProfileScoreCalculator
│   │
│   ├── hardware/                      # Sensores, GPS e execução em segundo plano
│   │   ├── service/
│   │   │   └── WorkoutTrackingService.kt  # ForegroundService + Notificação persistente
│   │   ├── sensor/
│   │   │   ├── AccelerometerTracker.kt    # Leitura e agregação em janelas de 5s
│   │   │   ├── StepCounterTracker.kt      # TYPE_STEP_DETECTOR + fallback por picos
│   │   │   └── LocationTracker.kt         # FusedLocationProviderClient + filtro accuracy <= 25m
│   │   └── WorkoutSessionManager.kt       # Singleton que expõe StateFlow do treino ativo
│   │
│   ├── sync/                          # Engine Offline-First
│   │   ├── SyncWorkoutWorker.kt       # CoroutineWorker com transação idempotente (UUID)
│   │   ├── SyncScheduler.kt           # Agenda WorkManager (NetworkType.CONNECTED + Backoff)
│   │   └── NetworkMonitor.kt          # Observa conectividade em tempo real (Flow<Boolean>)
│   │
│   ├── designsystem/                  # Identidade Visual (Minimalismo Urbano)
│   │   ├── theme/                     # Color.kt, Type.kt, Shape.kt, Theme.kt
│   │   └── components/                # Botões pílula, Cards flat, Badges, SyncIndicator, StreakChip
│   │
│   ├── di/                            # Módulos de Injeção de Dependência (Hilt)
│   │   ├── DatabaseModule.kt
│   │   ├── FirebaseModule.kt
│   │   ├── HardwareModule.kt
│   │   └── RepositoryModule.kt
│   │
│   └── util/                          # Extensões, formatadores de data/tempo, DispatcherProvider
│
└── features/                          # Divisão estrita por Feature
    ├── auth/                          # Login Google + Onboarding
    ├── home/                          # Aba 1: Início
    ├── training/                      # Aba 2: Hub de Treinos e Histórico
    ├── workout/                       # Fluxo Imersivo: Gravação Ativa, Resumo Pós-Treino e Detalhe
    ├── social/                        # Aba 3: Rankings, Grupos (Privado/Público) e Amigos
    └── profile/                       # Aba 4: Perfil, Configurações, Sensores e Fila Offline
```

---

## 3. Padrão Interno de Cada Feature

Cada pasta dentro de `features/<nome>/` deve seguir exatamente a convenção dos **3 arquivos padrão
** (+ contrato de estado quando a tela crescer) e uma pasta `components/` para peças exclusivas
daquela feature:

```text
features/home/
├── HomeRoutes.kt          # Definição @Serializable da rota + NavGraph + Composable Stateful (HomeRoute)
├── HomeViewModel.kt       # Gerenciamento de StateFlow<HomeUiState>, ações e chamadas a repositórios
├── HomeView.kt            # UI 100% Stateless + @Preview(s)
├── HomeContract.kt        # (Opcional p/ telas grandes) HomeUiState, HomeAction, HomeEffect
└── components/            # Componentes exclusivos da feature Home
    ├── WeeklyGoalCard.kt
    ├── ActiveFriendsRow.kt
    └── quickstart/        # Subpasta opcional quando um componente complexo tiver View + StateHolder/VM
        ├── QuickStartView.kt
        └── QuickStartState.kt
```

> **Nota sobre features com múltiplas telas** (ex.: `workout` tem Gravação Ativa, Pós-Treino e
> Detalhe; `social` tem Social, Criar Grupo e Detalhe do Grupo): mantenha um único
`WorkoutRoutes.kt`
> registrando todas as rotas da feature e subpastas internas por tela (`active/`, `summary/`,
`detail/`) mantendo a tríade `ViewModel` + `View` + `components/` em cada uma.

### Responsabilidade de cada arquivo

| Arquivo               | Responsabilidade Permitida                                                                                                                                                                                                        | Estritamente Proibido                                                                                                       |
|:----------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------|
| **`XxxRoutes.kt`**    | Declarar `@Serializable` da rota, extensão `NavGraphBuilder.xxxGraph(...)`, instanciar `hiltViewModel()`, coletar estado via `collectAsStateWithLifecycle()`, observar efeitos únicos (`Snackbar`, navegação) e chamar `XxxView`. | Conter layout complexo de UI, regras de negócio ou cálculos de domínio.                                                     |
| **`XxxViewModel.kt`** | Combinar `Flow`s dos repositórios em `StateFlow<XxxUiState>`, processar `onAction(action)`, acionar `UseCases`/`Calculators` e emitir efeitos únicos via `Channel`.                                                               | Referenciar `Context`, `Activity`, `NavController`, `SensorManager` direto ou tipos de UI do Compose (`Color`, `Modifier`). |
| **`XxxView.kt`**      | Renderizar UI pura recebendo apenas `uiState` e callbacks (`onAction`), organizar layout e declarar `@Preview` (estado padrão, vazio e carregando).                                                                               | Instanciar `ViewModel`, chamar `Repository`, acessar `Firebase`/`Room` ou controlar `NavController`.                        |
| **`components/`**     | Encapsular blocos visuais exclusivos daquela feature para manter a `View` abaixo de ~250 linhas.                                                                                                                                  | Ser importado por outra feature (se uma segunda feature precisar dele, promova-o para `core/designsystem/components/`).     |

---

## 4. Regras para Componentes (Stateless vs. State Holder vs. ViewModel)

Para evitar "telas gigantes" cheias de parâmetros (*prop drilling*) sem cair em armadilhas de ciclo
de vida do Compose, os componentes devem seguir uma **hierarquia de 3 níveis**:

### Nível 1 — Componente Stateless (Regra Geral: ~90% dos casos)

* Recebe um objeto de estado enxuto (ex.: `WeeklyProgressUiModel`) e callbacks simples.
* **Vantagem:** Reutilizável, previsível, testável e com `@Preview` instantâneo.

### Nível 2 — Componente com State Holder Local (Lógica de UI)

* Quando o componente possui lógica puramente visual ou interativa (ex.: botão *"Segurar por 1.5s
  para finalizar"*, campo de busca com debounce, controle de câmera do mapa, validação visual de
  formulário), crie uma classe **State Holder** em Kotlin puro com `rememberXxxState()`.
* Mantém a tela pai completamente limpa, sem precisar de um `ViewModel` Android.

### Nível 3 — Componente Inteligente / *Feature Widget* (`ComponentView` + `ComponentViewModel`)

* **Quando é permitido:** Apenas para widgets **autocontidos, independentes do fluxo da tela e com
  instância única por tela** — por exemplo:
    * `ActiveWorkoutBanner` (lê `WorkoutSessionManager` no Scaffold global).
    * `SyncStatusBadge` na TopAppBar (observa `SyncQueueDao` + `NetworkMonitor`).
    * `SensorLiveMonitorWidget` (exibe leitura ao vivo na tela de gravação ou diagnóstico).
* **Como estruturar quando permitido:**
    1. Separe sempre em `WidgetView` (stateless, com `@Preview`) + `WidgetContainer` (que obtém o
       `WidgetViewModel`).
    2. Documente no topo do arquivo que se trata de um *Feature Widget* de instância única.
* **Quando é ESTRITAMENTE PROIBIDO usar `ViewModel` em componente:**
    * **Nunca dentro de `LazyColumn` / `LazyRow` ou listas:** no Compose, `hiltViewModel()` escopa à
      tela (`NavBackStackEntry`), não ao item — todos os itens da lista acabariam compartilhando a *
      *mesma instância** do ViewModel, causando bugs graves.
    * **Nunca em componentes repetidos na mesma tela** (ex.: 3 cards de modalidades diferentes
      usando o mesmo ViewModel sem isolamento).
    * **Nunca em componentes do `core/designsystem/`:** o Design System deve ser 100% agnóstico a
      domínio e ViewModels.

---

## 5. Regras da Camada `core/` (Sensores, Offline-First e Domínio)

### 5.1 Sensores e Execução em Segundo Plano (`core/hardware`)

* Todo monitoramento de treino roda dentro do `WorkoutTrackingService` (`ForegroundService` com
  `foregroundServiceType="location|health"`).
* O serviço atualiza um `MutableStateFlow<ActiveWorkoutState>` no singleton `WorkoutSessionManager`.
  Tanto a tela `WorkoutActiveViewModel` quanto o `ActiveWorkoutBanner` apenas observam esse fluxo.
* **Agregação obrigatória:** Leituras brutas do acelerômetro devem ser reduzidas em janelas de `5s`
  antes de chegar ao estado persistido. Nunca salve amostras frame-a-frame no Room ou Firestore.
* **Filtro de GPS:** Descartar coordenadas com `accuracy > 25f` metros e nunca ativar
  `LocationTracker` na modalidade `ACADEMIA`.

### 5.2 Offline-First e Sincronização (`core/data` + `core/sync`)

* **Escrita local primeiro:** Ao finalizar um treino, salve no Room com
  `id = UUID.randomUUID().toString()` e `syncStatus = SyncStatus.PENDING`, e imediatamente agende o
  `SyncWorkoutWorker` (`ExistingWorkPolicy.APPEND_OR_REPLACE`).
* **Idempotência obrigatória:** O `SyncWorkoutWorker` sempre grava no Firestore usando
  `.document(workout.id).set(...)` dentro de uma `runTransaction` que atualiza treino, ranking
  mensal (`monthKey` da data original do treino), grupos e estatísticas do perfil de forma atômica.
* **Tratamento de falhas no Worker:**
    * Erro de rede/temporário $\rightarrow$ `Result.retry()` (com `BackoffPolicy.EXPONENTIAL`).
    * Erro de validação/permissão irreversível $\rightarrow$ marcar como `SyncStatus.FAILED` no Room
      com `errorMessage` e retornar `Result.failure()`.
* **Reset Mensal Automático:** Toda leitura/escrita de ranking deriva a coleção dinamicamente via
  `YearMonth.now().toString()` (formato `YYYY-MM`). Nunca crie lógica manual para apagar ou zerar
  documentos antigos.

### 5.3 Domínio Puro (`core/domain`)

* `PointsCalculator`, `StreakCalculator`, `LeagueCalculator` e `ProfileScoreCalculator` devem ser
  classes **Kotlin puras** (zero import de `android.*` ou `com.google.firebase.*`).
* Toda alteração nas fórmulas de pontuação ou pesos (`1.0x`, `1.3x`, `1.6x`) deve ser acompanhada de
  testes unitários em `test/core/domain/`.

---

## 6. Code Rules de Jetpack Compose e Design System

### 6.1 Uso Estrito do Tema (Minimalismo Urbano)

* **Proibido hardcoded:** Nunca escreva `Color(0xFFF97316)` ou `TextStyle(fontFamily = ...)`
  diretamente em uma `View`. Use exclusivamente `MaterialTheme.colorScheme.*`,
  `MaterialTheme.typography.*` e tokens semânticos em `MoviClubTheme.colors` (ex.:
  `MoviClubTheme.colors.success`, `MoviClubTheme.colors.warning`).
* **Tipografia de Telemetria:** Todo número dinâmico (cronômetro, distância, passos, aceleração,
  pontuação, posição no ranking) **deve** usar o estilo baseado em `JetBrains Mono` com
  `fontFeatureSettings = "tnum"` (numerais tabulares).
* **Flat Design:** Prefira bordas sutis (`BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`) e
  `RoundedCornerShape(16.dp)` em vez de `shadowElevation` elevada.

### 6.2 Boas Práticas de Performance e API Compose

* **`Modifier` primeiro:** Todo componente reutilizável deve aceitar `modifier: Modifier = Modifier`
  como **primeiro parâmetro opcional** e aplicá-lo apenas ao container raiz.
* **Coleta segura de lifecycle:** Sempre usar `collectAsStateWithLifecycle()` (nunca
  `collectAsState()` puro) para pausar coletas quando o app for para segundo plano.
* **Estados imutáveis:** Classes de `UiState` devem ser `data class` com propriedades `val`, tipos
  primitivos ou `List` (nunca `MutableList` ou `var`). Quando listas forem passadas frequentemente
  em recomposições de sensores, anotar o estado com `@Immutable`.
* **Chaves estáveis em listas:** Todo `items(...)` em `LazyColumn`/`LazyRow` **deve** informar
  `key = { it.id }` e, quando houver múltiplos tipos de card, `contentType`.
* **Efeitos colaterais controlados:** Nunca dispare funções de repositório ou `viewModel.loadData()`
  diretamente no corpo de um Composable; inicialize carregamentos no `init` do ViewModel ou em
  `LaunchedEffect` com chave explícita.
* **Prevenção de re-emissão de eventos únicos:** Navegação e `Snackbar` devem trafegar por
  `Channel<XxxEffect>(Channel.BUFFERED)` convertido em `receiveAsFlow()`, nunca como booleanos
  soltos dentro do `UiState`.
* **Acessibilidade em movimento:** Botões principais (especialmente na tela de gravação) devem ter
  altura mínima de `52.dp`–`56.dp` e `contentDescription` preenchido em todos os ícones sem texto.

---

## 7. Convenções de Código Kotlin & Nomenclatura

### 7.1 Nomenclatura Padrão

| Elemento                       | Padrão                                                           | Exemplo                                                |
|:-------------------------------|:-----------------------------------------------------------------|:-------------------------------------------------------|
| Pacotes                        | `lowercase` sem underline                                        | `com.moviclub.app.features.workout.active`             |
| Rotas (`@Serializable`)        | `object` / `data class` com sufixo `Destination` ou nome da tela | `data class WorkoutDetailRoute(val workoutId: String)` |
| Composable de Tela (Stateful)  | `<Nome>Route`                                                    | `HomeRoute(onNavigateToWorkout: ...)`                  |
| Composable de Tela (Stateless) | `<Nome>View`                                                     | `HomeView(uiState: HomeUiState, onAction: ...)`        |
| Composable de Componente       | Substantivo descritivo em `PascalCase`                           | `StreakBadge`, `ModalityLevelCard`                     |
| ViewModel                      | `<Nome>ViewModel`                                                | `WorkoutActiveViewModel`                               |
| UiState / Action / Effect      | `<Nome>UiState` / `<Nome>Action` / `<Nome>Effect`                | `SocialUiState`, `SocialAction.JoinGroup`              |
| Entidade Room vs. Domínio      | Sufixo `Entity` no Room; nome limpo no domínio                   | `WorkoutEntity` $\leftrightarrow$ `Workout`            |
| DTO Firestore                  | Sufixo `Dto`                                                     | `WorkoutDto`, `RankingEntryDto`                        |

### 7.2 Kotlin, Coroutines e Tratamento de Erros

* **Coroutines:** Nunca usar `GlobalScope` nem `runBlocking` em código de produção. Sempre injetar
  `DispatcherProvider` (`io`, `default`, `main`) em vez de chamar `Dispatchers.IO` hardcoded (
  facilita testes).
* **Zero `!!`:** O operador *not-null assertion* (`!!`) é proibido; use `?.let`, `?:`,
  `requireNotNull(...)` com mensagem clara ou modelagem de estado seguro (`sealed interface`).
* **Tratamento de exceções em `suspend`:** Nunca capture `Exception` engolindo
  `CancellationException` em coroutines. Prefira retornar `Result<T>` ou um `AppResult<T>` selado
  nos repositórios.
* **Constantes mágicas:** Multiplicadores de modalidade, tempo mínimo de treino (`5 min` em
  release / `1 min` via `BuildConfig.DEBUG`), faixas de liga e limiares do acelerômetro devem viver
  centralizados em `core/domain/GameRules.kt`.
* **Formatação:** Seguir o guia oficial Kotlin (*Kotlin Official Style Guide*), linha máxima de
  `120` caracteres e imports sem wildcard (`*`), exceto em DSLs de Compose quando necessário.

---

## 8. Checklist Rápido de Revisão (Antes de Commitar / Abrir PR)

- [ ] A tela segue a separação `Routes` (stateful) + `ViewModel` + `View` (stateless)?
- [ ] A `View` e os componentes stateless possuem `@Preview` funcionando sem depender de ViewModel
  ou Firebase?
- [ ] Nenhum `ViewModel` foi instanciado dentro de itens de `LazyColumn` ou componentes de
  `core/designsystem`?
- [ ] Todos os números dinâmicos de telemetria/ranking usam `JetBrains Mono` com `tnum` e cores do
  `MaterialTheme`?
- [ ] A tela funciona graciosamente offline (lendo do Room / exibindo estado claro de sem rede)?
- [ ] Nenhuma feature importa arquivos internos de outra feature (comunicação feita apenas via
  `navigation` e `core`)?
- [ ] As regras de pontuação/streak/ligas alteradas possuem testes unitários passando em
  `core/domain`?