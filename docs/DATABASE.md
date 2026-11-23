# Modelagem de Banco de Dados, Domain Models e DTOs — MoviClub

**Stack de Dados:** Room (SQLite) • Preferences DataStore • Firebase Cloud Firestore • WorkManager

**Estratégia:** *Offline-First* com separação estrita em 3 camadas:
1. **Local (`Entity`):** Tabelas do Room (`core/data/local/entity`) — fonte única da verdade (*Single Source of Truth*) para a UI.
2. **Domínio (`Model`):** Classes Kotlin puras (`core/domain/model`) — usadas pelos *Calculators*, *Repositories* e *ViewModels*.
3. **Remoto (`Dto`):** Documentos e coleções do Cloud Firestore (`core/data/remote/dto`) — espelho otimizado para consultas e custo mínimo de leitura/escrita.

---

## 1. Enums e Constantes de Domínio (`core/domain/model`)

```kotlin
enum class Modality(val weight: Double, val usesGps: Boolean) {
    WALKING(weight = 1.0, usesGps = true),
    GYM(weight = 1.3, usesGps = false),
    RUNNING(weight = 1.6, usesGps = true)
}

enum class League(val minPoints: Int, val maxPoints: Int?) {
    BRONZE(0, 499),
    SILVER(500, 1499),
    GOLD(1500, 2999),
    DIAMOND(3000, null)
}

enum class WorkoutStatus {
    RECORDING,   // Sessão ativa em ForegroundService
    PAUSED,      // Pausada pelo usuário
    COMPLETED,   // Finalizada e válida para pontuação
    DISCARDED    // Cancelada ou abaixo do tempo mínimo
}

enum class SyncStatus {
    PENDING,     // Salvo no Room, aguardando WorkManager
    SYNCING,     // Em envio no momento
    SYNCED,      // Confirmado no Firestore
    FAILED       // Erro permanente (ex.: permissão/validação)
}

enum class GroupVisibility {
    PRIVATE,     // Apenas convidados (via código)
    PUBLIC       // Listado em "Explorar Grupos Públicos"
}

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

enum class WeeklyGoalType {
    WORKOUTS_COUNT, // Ex.: 4 treinos na semana
    POINTS          // Ex.: 600 pontos na semana
}

enum class SensorSensitivity(val stepThreshold: Float) {
    STANDARD(stepThreshold = 1.8f),
    HIGH(stepThreshold = 1.2f)
}
```

---

## 2. Domain Models (`core/domain/model`)

Classes Kotlin puras (sem anotações de Room ou Firestore), usadas na camada de negócio e mapeadas para `UiState`.

```kotlin
data class UserProfile(
    val uid: String,
    val displayName: String,
    val username: String,              // Único, lowercase (ex.: "joao_silva")
    val photoUrl: String?,
    val profileScore: Int,             // Nota contínua 0..100 (últimos 30 dias)
    val currentStreak: Int,
    val bestStreak: Int,
    val lastWorkoutDate: String?,      // "YYYY-MM-DD" no fuso local
    val weeklyGoalType: WeeklyGoalType,
    val weeklyGoalTarget: Int,
    val currentMonthKey: String,       // "YYYY-MM"
    val currentMonthPoints: Int,
    val currentLeague: League,
    val totalWorkouts: Int,
    val totalPoints: Long,
    val totalActiveMinutes: Long,
    val modalityProgress: Map<Modality, ModalityProgress>,
    val isPublicOnGlobalRanking: Boolean,
    val createdAt: Long
)

data class ModalityProgress(
    val modality: Modality,
    val xp: Long,                      // Acumulado de pontos nessa modalidade
    val level: Int,                    // Calculado a partir do XP (ex.: a cada 500 XP)
    val workoutsCount: Int,
    val totalMinutes: Long,
    val totalDistanceMeters: Double
)

data class Workout(
    val id: String,                    // UUID v4 gerado no cliente (idempotência)
    val uid: String,
    val modality: Modality,
    val description: String,
    val startedAt: Long,               // Epoch millis
    val endedAt: Long?,
    val durationSec: Long,             // Tempo total decorrido
    val activeDurationSec: Long,       // Tempo sem pausas
    val distanceMeters: Double,        // 0.0 para GYM
    val steps: Int,
    val avgIntensity: Float,           // Média da magnitude do acelerômetro (m/s²)
    val maxIntensity: Float,
    val intensitySamples: List<Float>, // Janelas agregadas de 5 em 5 segundos
    val routePolyline: String?,        // Google Encoded Polyline (leve para BD/nuvem)
    val modalityWeight: Double,
    val points: Int,
    val monthKey: String,              // "YYYY-MM" derivado de startedAt
    val weekKey: String,               // "YYYY-Www" (ISO week, ex.: "2026-W47")
    val status: WorkoutStatus,
    val syncStatus: SyncStatus,
    val syncAttempts: Int,
    val lastSyncError: String?,
    val syncedAt: Long?
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

data class RankingEntry(
    val uid: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val points: Int,
    val workoutsCount: Int,
    val league: League,
    val position: Int = 0              // Calculado na consulta/ordenação
)

data class MonthlySummary(
    val monthKey: String,              // "YYYY-MM"
    val points: Int,
    val workoutsCount: Int,
    val finalLeague: League,
    val activeMinutes: Long
)

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val visibility: GroupVisibility,
    val inviteCode: String,            // 6 caracteres alfanuméricos (ex.: "MOVI42")
    val ownerUid: String,
    val membersCount: Int,
    val isCurrentUserMember: Boolean,
    val currentUserRank: Int?,         // Posição cacheada para exibir no card
    val createdAt: Long
)

data class Friend(
    val uid: String,
    val displayName: String,
    val username: String,
    val photoUrl: String?,
    val profileScore: Int,
    val currentStreak: Int,
    val lastWorkoutDate: String?,      // Permite calcular "treinou hoje" localmente
    val trainedToday: Boolean
)

data class FriendRequest(
    val id: String,
    val fromUid: String,
    val fromDisplayName: String,
    val fromUsername: String,
    val fromPhotoUrl: String?,
    val toUid: String,
    val status: FriendRequestStatus,
    val createdAt: Long
)
```

---

## 3. Modelagem Local — Room Database (`core/data/local`)

O banco local `moviclub.db` armazena **duas categorias de dados**:
* **Dados Autoritativos Offline-First:** Perfil do usuário logado, treinos, amostras temporárias de gravação e histórico mensal (nunca se perdem sem rede).
* **Tabelas de Cache Social:** Amigos, grupos do usuário e último snapshot de ranking para que a *Home* e a aba *Social* abram instantaneamente mesmo offline.

### Diagrama Relacional Simplificado (Room)

```
[user_profile] (1) ──────< (N) [workouts] (1) ──────< (N) [recording_route_points]
      │                             │
      ├──< (N) [modality_progress]  └──< (N) [recording_intensity_samples]
      ├──< (N) [monthly_history]
      ├──< (N) [cached_friends]
      ├──< (N) [cached_groups]
      └──< (N) [cached_rankings]
```

> **Por que tabelas temporárias `recording_*`?**
> Durante o treino (`ForegroundService`), em vez de reescrever uma string gigante de JSON/Polyline a cada 5 segundos no registro `workouts`, fazemos `INSERT` leve de pontos GPS e janelas de aceleração nas tabelas temporárias. Ao finalizar o treino, compactamos os pontos em `routePolyline` e `intensitySamplesJson` no `WorkoutEntity` e limpamos as tabelas temporárias daquela sessão.

### 3.1 Entidades Room (`core/data/local/entity`)

#### `UserProfileEntity` (`user_profile`)
| Coluna | Tipo SQLite / Kotlin | Restrições / Descrição |
| :--- | :--- | :--- |
| `uid` | `TEXT` (`String`) | **PK** — UID do Firebase Auth |
| `display_name` | `TEXT` (`String`) | Nome de exibição |
| `username` | `TEXT` (`String`) | `@username` único |
| `photo_url` | `TEXT` (`String?`) | URL do avatar Google |
| `profile_score` | `INTEGER` (`Int`) | Nota 0..100 |
| `current_streak` | `INTEGER` (`Int`) | Dias consecutivos atuais |
| `best_streak` | `INTEGER` (`Int`) | Recorde histórico de dias |
| `last_workout_date` | `TEXT` (`String?`) | `"YYYY-MM-DD"` |
| `weekly_goal_type` | `TEXT` (`String`) | `WORKOUTS_COUNT` ou `POINTS` |
| `weekly_goal_target` | `INTEGER` (`Int`) | Valor alvo semanal |
| `current_month_key` | `TEXT` (`String`) | `"YYYY-MM"` |
| `current_month_points` | `INTEGER` (`Int`) | Pontuação do mês corrente |
| `current_league` | `TEXT` (`String`) | `BRONZE` / `SILVER` / `GOLD` / `DIAMOND` |
| `total_workouts` | `INTEGER` (`Int`) | Contador total |
| `total_points` | `INTEGER` (`Long`) | Soma histórica |
| `total_active_minutes` | `INTEGER` (`Long`) | Soma histórica de minutos ativos |
| `is_public_ranking` | `INTEGER` (`Boolean`) | Visibilidade no Ranking Global |
| `created_at` | `INTEGER` (`Long`) | Timestamp criação |
| `updated_at` | `INTEGER` (`Long`) | Última atualização local |

#### `ModalityProgressEntity` (`modality_progress`)
| Coluna | Tipo | Restrições / Descrição |
| :--- | :--- | :--- |
| `uid` | `TEXT` | **PK composta** (FK $\rightarrow$ `user_profile.uid`) |
| `modality` | `TEXT` | **PK composta** (`WALKING`, `GYM`, `RUNNING`) |
| `xp` | `INTEGER` (`Long`) | XP acumulado na categoria |
| `level` | `INTEGER` (`Int`) | Nível atual na categoria |
| `workouts_count` | `INTEGER` (`Int`) | Total de treinos na categoria |
| `total_minutes` | `INTEGER` (`Long`) | Minutos totais |
| `total_distance_meters` | `REAL` (`Double`) | Distância total acumulada |

#### `WorkoutEntity` (`workouts`)
*Índices:* `(uid, started_at DESC)`, `(sync_status)`, `(month_key)`, `(week_key)`

| Coluna | Tipo | Restrições / Descrição |
| :--- | :--- | :--- |
| `id` | `TEXT` (`String`) | **PK** — UUID v4 gerado no cliente |
| `uid` | `TEXT` (`String`) | Dono do treino |
| `modality` | `TEXT` (`String`) | `WALKING`, `GYM`, `RUNNING` |
| `description` | `TEXT` (`String`) | Texto opcional pós-treino |
| `started_at` | `INTEGER` (`Long`) | Epoch millis início |
| `ended_at` | `INTEGER` (`Long?`) | Epoch millis fim |
| `duration_sec` | `INTEGER` (`Long`) | Duração total em segundos |
| `active_duration_sec` | `INTEGER` (`Long`) | Tempo efetivo em exercício |
| `distance_meters` | `REAL` (`Double`) | Distância calculada via GPS |
| `steps` | `INTEGER` (`Int`) | Contagem de passos |
| `avg_intensity` | `REAL` (`Float`) | Intensidade média ($\text{m/s}^2$) |
| `max_intensity` | `REAL` (`Float`) | Pico de intensidade |
| `intensity_samples_json` | `TEXT` (`String`) | Array JSON compacto de janelas de 5s (ex.: `[1.2, 3.4, 2.9]`) |
| `route_polyline` | `TEXT` (`String?`) | Polilinha codificada (nulo para `GYM`) |
| `modality_weight` | `REAL` (`Double`) | Peso aplicado (`1.0`, `1.3`, `1.6`) |
| `points` | `INTEGER` (`Int`) | Pontos finais calculados |
| `month_key` | `TEXT` (`String`) | `"YYYY-MM"` da data do treino |
| `week_key` | `TEXT` (`String`) | `"YYYY-Www"` da semana do treino |
| `status` | `TEXT` (`String`) | `RECORDING`, `PAUSED`, `COMPLETED`, `DISCARDED` |
| `sync_status` | `TEXT` (`String`) | `PENDING`, `SYNCING`, `SYNCED`, `FAILED` |
| `sync_attempts` | `INTEGER` (`Int`) | Tentativas executadas pelo WorkManager |
| `last_sync_error` | `TEXT` (`String?`) | Mensagem de erro (para diagnóstico) |
| `synced_at` | `INTEGER` (`Long?`) | Timestamp da confirmação no Firestore |

#### `RecordingRoutePointEntity` (`recording_route_points`) — *Buffer de Checkpoint*
| Coluna | Tipo | Restrições / Descrição |
| :--- | :--- | :--- |
| `id` | `INTEGER` (`Long`) | **PK** (`autoGenerate = true`) |
| `workout_id` | `TEXT` (`String`) | **FK** $\rightarrow$ `workouts.id` (`ON DELETE CASCADE`) |
| `latitude` | `REAL` (`Double`) | Latitude validada |
| `longitude` | `REAL` (`Double`) | Longitude validada |
| `accuracy_meters` | `REAL` (`Float`) | Precisão ($\le 25\text{m}$) |
| `timestamp` | `INTEGER` (`Long`) | Momento da coleta |

#### `RecordingIntensitySampleEntity` (`recording_intensity_samples`) — *Buffer de Checkpoint*
| Coluna | Tipo | Restrições / Descrição |
| :--- | :--- | :--- |
| `id` | `INTEGER` (`Long`) | **PK** (`autoGenerate = true`) |
| `workout_id` | `TEXT` (`String`) | **FK** $\rightarrow$ `workouts.id` (`ON DELETE CASCADE`) |
| `window_index` | `INTEGER` (`Int`) | Índice sequencial da janela de 5s |
| `avg_magnitude` | `REAL` (`Float`) | Média da magnitude $a = \sqrt{x^2 + y^2 + z^2}$ |
| `timestamp` | `INTEGER` (`Long`) | Timestamp do fechamento da janela |

#### `MonthlyHistoryEntity` (`monthly_history`)
| Coluna | Tipo | Restrições / Descrição |
| :--- | :--- | :--- |
| `uid` | `TEXT` | **PK composta** |
| `month_key` | `TEXT` | **PK composta** (`"YYYY-MM"`) |
| `points` | `INTEGER` | Pontuação consolidada do mês |
| `workouts_count` | `INTEGER` | Treinos no mês |
| `final_league` | `TEXT` | Liga alcançada |
| `active_minutes` | `INTEGER` | Minutos ativos no mês |

#### Tabelas de Cache Social (Leitura Rápida Offline)
* **`CachedFriendEntity` (`cached_friends`):** `friend_uid` (PK), `display_name`, `username`, `photo_url`, `profile_score`, `current_streak`, `last_workout_date`, `cached_at`.
* **`CachedGroupEntity` (`cached_groups`):** `group_id` (PK), `name`, `description`, `visibility`, `invite_code`, `owner_uid`, `members_count`, `is_member`, `current_user_rank`, `cached_at`. *(Essencial também para o `SyncWorkoutWorker` saber quais grupos precisam ter o ranking incrementado ao sincronizar um treino).*
* **`CachedRankingEntryEntity` (`cached_rankings`):** `scope_key` (PK composta — ex.: `"GLOBAL:2026-11"`, `"LEAGUE_SILVER:2026-11"`, `"FRIENDS:2026-11"`, `"GROUP_<id>:2026-11"`), `uid` (PK composta), `display_name`, `username`, `photo_url`, `points`, `workouts_count`, `league`, `position`, `cached_at`.

### 3.2 Principais DAOs (`core/data/local/dao`)

* **`WorkoutDao`:**
  * `observeCompletedWorkouts(uid, modalityFilter?): Flow<List<WorkoutEntity>>`
  * `observeWorkoutsByWeek(uid, weekKey): Flow<List<WorkoutEntity>>` (alimenta a timeline `S T Q Q S S D` e meta semanal na Home)
  * `observePendingSyncCount(): Flow<Int>` (alimenta o badge de fila offline na TopAppBar)
  * `getPendingSyncWorkouts(): List<WorkoutEntity>` (usado pelo `SyncWorkoutWorker` ordenado por `started_at ASC`)
  * `getActiveOrInterruptedWorkout(uid): WorkoutEntity?` (busca `status IN ('RECORDING', 'PAUSED')` na Splash para recuperação de falha)
  * `updateSyncStatus(id, status, attempts, error, syncedAt)`
* **`UserDao`:**
  * `observeProfile(uid): Flow<UserProfileEntity?>`
  * `observeModalityProgress(uid): Flow<List<ModalityProgressEntity>>`
  * `observeMonthlyHistory(uid): Flow<List<MonthlyHistoryEntity>>`
* **`SocialCacheDao`:**
  * `observeFriends(): Flow<List<CachedFriendEntity>>`
  * `observeMyGroups(): Flow<List<CachedGroupEntity>>`
  * `getMyGroupIds(): List<String>` (lido pelo Worker para atualizar rankings de grupos)
  * `observeRanking(scopeKey): Flow<List<CachedRankingEntryEntity>>`

### 3.3 Preferences DataStore (`core/data/local/prefs`)

Configurações puramente locais do dispositivo (não precisam de SQL nem Firestore):

| Chave | Tipo | Valor Padrão | Uso |
| :--- | :--- | :--- | :--- |
| `sensor_sensitivity` | `String` | `"STANDARD"` | Calibração de passos/acelerômetro |
| `keep_screen_on` | `Boolean` | `true` | Manter tela ativa durante gravação |
| `workout_feedback_haptics` | `Boolean` | `true` | Vibração ao iniciar/pausar/finalizar |
| `notify_streak_reminder` | `Boolean` | `true` | Lembrete diário local (WorkManager) |
| `notify_ranking_alerts` | `Boolean` | `true` | Avisos de competição |
| `last_social_sync_millis` | `Long` | `0L` | Exibe *"Atualizado às HH:MM"* offline |

---

## 4. Modelagem Remota — Cloud Firestore (`core/data/remote`)

A estrutura do Firestore foi projetada para **evitar Cloud Functions (plano gratuito Spark)**, suportar **reset mensal automático sem cron jobs** e **minimizar leituras** por tela.

### Mapa de Coleções

```
firestore-root/
├── usernames/{username}                              # Garantia O(1) de unicidade de @username
├── users/{uid}                                       # Perfil público/privado + gamificação
│   ├── friends/{friendUid}                           # Lista de amigos aceitos
│   └── monthly_history/{YYYY-MM}                     # Histórico mensal do usuário
│
├── friend_requests/{requestId}                       # Solicitações de amizade (PENDING/ACCEPTED)
│
├── workouts/{workoutId}                              # Treinos individuais (ID = UUID do Room)
│
├── monthly_rankings/{YYYY-MM}                        # Documento pai do ciclo mensal
│   └── entries/{uid}                                 # Ranking Global e por Liga daquele mês
│
├── group_invite_codes/{inviteCode}                   # Mapeamento seguro código -> groupId
└── groups/{groupId}                                  # Grupos privados e públicos
    └── rankings/{YYYY-MM}
        └── entries/{uid}                             # Ranking mensal exclusivo daquele grupo
```

### 4.1 Detalhamento dos DTOs (`core/data/remote/dto`)

> **Regra de serialização no Firestore:** Todos os DTOs devem possuir valores padrão em todos os parâmetros (construtor vazio exigido pelo SDK do Firestore).

#### 1. `UsernameLookupDto` — `usernames/{username}`
```kotlin
data class UsernameLookupDto(
    val uid: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
```

#### 2. `UserProfileDto` — `users/{uid}`
```kotlin
data class UserProfileDto(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val profileScore: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastWorkoutDate: String? = null,             // "YYYY-MM-DD"
    val weeklyGoalType: String = "WORKOUTS_COUNT",
    val weeklyGoalTarget: Int = 3,
    val currentMonthKey: String = "",                // "YYYY-MM"
    val currentMonthPoints: Int = 0,
    val currentLeague: String = "BRONZE",
    val totalWorkouts: Int = 0,
    val totalPoints: Long = 0L,
    val totalActiveMinutes: Long = 0L,
    val modalityStats: Map<String, ModalityStatsDto> = emptyMap(), // "WALKING", "GYM", "RUNNING"
    val isPublicOnGlobalRanking: Boolean = true,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

data class ModalityStatsDto(
    val xp: Long = 0L,
    val level: Int = 1,
    val workoutsCount: Int = 0,
    val totalMinutes: Long = 0L,
    val totalDistanceMeters: Double = 0.0
)
```

#### 3. `UserMonthlyHistoryDto` — `users/{uid}/monthly_history/{YYYY-MM}`
```kotlin
data class UserMonthlyHistoryDto(
    val monthKey: String = "",
    val points: Int = 0,
    val workoutsCount: Int = 0,
    val finalLeague: String = "BRONZE",
    val activeMinutes: Long = 0L,
    val updatedAt: Timestamp = Timestamp.now()
)
```

#### 4. `WorkoutDto` — `workouts/{workoutId}`
```kotlin
data class WorkoutDto(
    val id: String = "",                             // Mesmo UUID do Room
    val uid: String = "",
    val userDisplayName: String = "",                // Requisito: nome do usuário no registro remoto
    val modality: String = "WALKING",
    val description: String = "",
    val startedAt: Timestamp = Timestamp.now(),
    val endedAt: Timestamp = Timestamp.now(),
    val durationSec: Long = 0L,
    val activeDurationSec: Long = 0L,
    val distanceMeters: Double = 0.0,
    val steps: Int = 0,
    val avgIntensity: Double = 0.0,
    val maxIntensity: Double = 0.0,
    val intensitySamples: List<Double> = emptyList(), // ~12 floats/min -> 1h = ~720 valores (~6 KB)
    val routePolyline: String? = null,                // Compactado (~2 a 15 KB por corrida)
    val modalityWeight: Double = 1.0,
    val points: Int = 0,
    val monthKey: String = "",                        // "YYYY-MM"
    val weekKey: String = "",                         // "YYYY-Www"
    val createdAt: Timestamp = Timestamp.now()
)
```
> **Estimativa de tamanho do documento:** Um treino de 1 hora de corrida ocupa cerca de **15 KB a 25 KB** (menos de 3% do limite de 1 MB do Firestore).

#### 5. `RankingEntryDto` — `monthly_rankings/{YYYY-MM}/entries/{uid}`
```kotlin
data class RankingEntryDto(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val points: Int = 0,
    val workoutsCount: Int = 0,
    val league: String = "BRONZE",
    val isPublic: Boolean = true,                    // Filtro para Ranking Global
    val updatedAt: Timestamp = Timestamp.now()
)
```

#### 6. `GroupDto` — `groups/{groupId}` e `GroupInviteCodeDto` — `group_invite_codes/{inviteCode}`
```kotlin
data class GroupDto(
    val id: String = "",
    val name: String = "",
    val nameLowercase: String = "",                  // Facilita busca por prefixo em grupos públicos
    val description: String = "",
    val isPublic: Boolean = false,                   // false = Privado, true = Público
    val inviteCode: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),      // Query: whereArrayContains("memberUids", uid)
    val membersCount: Int = 1,
    val createdAt: Timestamp = Timestamp.now()
)

data class GroupInviteCodeDto(
    val inviteCode: String = "",
    val groupId: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
```
*(A subcoleção `groups/{groupId}/rankings/{YYYY-MM}/entries/{uid}` reutiliza exatamente o mesmo `RankingEntryDto`).*

#### 7. `FriendDto` — `users/{uid}/friends/{friendUid}` e `FriendRequestDto` — `friend_requests/{requestId}`
```kotlin
data class FriendLinkDto(
    val friendUid: String = "",
    val since: Timestamp = Timestamp.now()
)

data class FriendRequestDto(
    val id: String = "",                             // Padrão determinístico: "${fromUid}_${toUid}"
    val fromUid: String = "",
    val fromDisplayName: String = "",
    val fromUsername: String = "",
    val fromPhotoUrl: String? = null,
    val toUid: String = "",
    val status: String = "PENDING",                  // PENDING, ACCEPTED, DECLINED
    val createdAt: Timestamp = Timestamp.now()
)
```

---

## 5. Transação Atômica de Sincronização (`SyncWorkoutWorker`)

Quando o `SyncWorkoutWorker` processa um `WorkoutEntity` com `syncStatus = PENDING`, ele executa uma **Firestore Transaction** com verificação de idempotência para impedir pontuação duplicada caso a conexão caia no meio do envio:

1. **Leitura de Idempotência:** Lê `workouts/{workout.id}`.
   * **Se o documento já existir:** o envio anterior já foi concluído no servidor e apenas o ACK falhou na rede $\rightarrow$ **aborta as escritas**, marca o registro local como `SYNCED` e encerra com sucesso (evita somar pontos em dobro!).
2. **Leitura dos Contadores Atuais:**
   * Lê `users/{uid}`
   * Lê `monthly_rankings/{workout.monthKey}/entries/{uid}`
   * Lê `users/{uid}/monthly_history/{workout.monthKey}`
   * Lê `groups/{groupId}/rankings/{workout.monthKey}/entries/{uid}` para cada `groupId` em que o usuário é membro.
3. **Cálculo e Escrita Atômica (no mesmo `transaction`):**
   * `set` em `workouts/{workout.id}` com `WorkoutDto`.
   * `set(..., merge)` em `monthly_rankings/{workout.monthKey}/entries/{uid}` somando `+workout.points`, `+1` treino e recalculando `league`.
   * `set(..., merge)` em `users/{uid}/monthly_history/{workout.monthKey}` com os novos totais daquele mês.
   * `set(..., merge)` em cada `groups/{groupId}/rankings/{workout.monthKey}/entries/{uid}`.
   * `set(..., merge)` em `users/{uid}` atualizando `currentStreak`, `bestStreak`, `lastWorkoutDate`, `profileScore`, `modalityStats`, `totalWorkouts`, `totalPoints` e `currentMonthPoints` (apenas se `workout.monthKey == currentMonthKey`).
4. **Confirmação Local:** Somente após `transaction.await()` retornar sucesso, o Worker atualiza `workouts.sync_status = SYNCED` e `synced_at = now` no Room.

---

## 6. Índices Compostos do Firestore (`firestore.indexes.json`)

Para as consultas da aba **Social** funcionarem sem erro no Firebase, configure os seguintes índices compostos:

| Coleção / Subcoleção | Campos e Ordenação | Consulta Atendida |
| :--- | :--- | :--- |
| `entries` (Collection Group ou `monthly_rankings/{m}/entries`) | `isPublic ASC`, `points DESC` | Ranking Global do mês |
| `entries` (`monthly_rankings/{m}/entries`) | `league ASC`, `points DESC` | Ranking "Minha Liga" |
| `groups` | `isPublic ASC`, `membersCount DESC` | Explorar Grupos Públicos (populares) |
| `groups` | `isPublic ASC`, `nameLowercase ASC` | Busca por nome em Grupos Públicos |
| `friend_requests` | `toUid ASC`, `status ASC`, `createdAt DESC` | Solicitações de amizade recebidas |
| `workouts` | `uid ASC`, `startedAt DESC` | Restauração de histórico ao logar em outro celular |

---

## 7. Regras de Segurança do Firestore (`firestore.rules`)

Regras prontas para proteger dados privados, impedir que um usuário edite treinos/pontos de terceiros e manter grupos privados invisíveis a não-membros:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() {
      return request.auth != null;
    }

    function isOwner(uid) {
      return isSignedIn() && request.auth.uid == uid;
    }

    // Unicidade de @username
    match /usernames/{username} {
      allow read: if isSignedIn();
      allow create: if isSignedIn()
                    && request.resource.data.uid == request.auth.uid
                    && !exists(/databases/$(database)/documents/usernames/$(username));
      allow delete: if isSignedIn() && resource.data.uid == request.auth.uid;
    }

    // Perfis de usuário
    match /users/{uid} {
      allow read: if isSignedIn();
      allow write: if isOwner(uid);

      match /monthly_history/{monthKey} {
        allow read: if isSignedIn();
        allow write: if isOwner(uid);
      }

      match /friends/{friendUid} {
        allow read: if isSignedIn();
        // Permite criar/remover vínculo pelos dois lados envolvidos na amizade
        allow write: if isOwner(uid) || isOwner(friendUid);
      }
    }

    // Solicitações de amizade
    match /friend_requests/{requestId} {
      allow read: if isSignedIn()
                  && (resource.data.fromUid == request.auth.uid
                      || resource.data.toUid == request.auth.uid);
      allow create: if isSignedIn()
                    && request.resource.data.fromUid == request.auth.uid
                    && request.resource.data.status == 'PENDING';
      allow update: if isSignedIn()
                    && resource.data.toUid == request.auth.uid;
      allow delete: if isSignedIn()
                    && (resource.data.fromUid == request.auth.uid
                        || resource.data.toUid == request.auth.uid);
    }

    // Treinos individuais (imutáveis após criação para evitar adulteração de pontos)
    match /workouts/{workoutId} {
      allow read: if isSignedIn() && resource.data.uid == request.auth.uid;
      allow create: if isSignedIn()
                    && request.resource.data.uid == request.auth.uid
                    && request.resource.data.points >= 0;
      allow update: if isSignedIn()
                    && resource.data.uid == request.auth.uid
                    // Permite editar apenas a descrição pós-treino
                    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['description']);
      allow delete: if isSignedIn() && resource.data.uid == request.auth.uid;
    }

    // Rankings mensais (Global / Liga / Amigos)
    match /monthly_rankings/{monthKey}/entries/{uid} {
      allow read: if isSignedIn();
      allow write: if isOwner(uid) && request.resource.data.uid == uid;
    }

    // Mapeamento de códigos de convite (permite entrar em grupo privado via código)
    match /group_invite_codes/{code} {
      allow get: if isSignedIn();
      allow list: if false; // Impede enumerar códigos de grupos privados
      allow create: if isSignedIn();
    }

    // Grupos privados e públicos
    match /groups/{groupId} {
      allow read: if isSignedIn()
                  && (resource.data.isPublic == true
                      || request.auth.uid in resource.data.memberUids);
      allow create: if isSignedIn()
                    && request.resource.data.ownerUid == request.auth.uid
                    && request.auth.uid in request.resource.data.memberUids;
      allow update: if isSignedIn()
                    && (resource.data.ownerUid == request.auth.uid
                        // Membro entrando ou saindo do grupo (altera apenas memberUids e membersCount)
                        || request.resource.data.diff(resource.data).affectedKeys()
                            .hasOnly(['memberUids', 'membersCount']));
      allow delete: if isSignedIn() && resource.data.ownerUid == request.auth.uid;

      match /rankings/{monthKey}/entries/{uid} {
        allow read: if isSignedIn()
                    && (get(/databases/$(database)/documents/groups/$(groupId)).data.isPublic == true
                        || request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberUids);
        allow write: if isOwner(uid)
                     && request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberUids;
      }
    }
  }
}
```

---

## 8. Regras de Mapeamento (`core/data/mapper`)

Para manter a arquitetura limpa conforme as *Code Rules*:
* `WorkoutEntity.toDomain()` / `Workout.toEntity()` / `Workout.toDto(userDisplayName)`
* `UserProfileEntity.toDomain(modalityProgressList)` / `UserProfileDto.toEntity()`
* `RankingEntryDto.toCacheEntity(scopeKey, position)` / `CachedRankingEntryEntity.toDomain()`
* **Polilinha GPS:** Criar um utilitário `PolylineCodec` em `core/util/` (algoritmo padrão de codificação de polilinhas do Google, ~40 linhas em Kotlin) para converter `List<RoutePoint>` $\leftrightarrow$ `String` ao finalizar ou visualizar um treino.