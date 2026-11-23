# Requisitos e Especificação Técnica

## 2. Requisitos Funcionais e Não Funcionais (Especificação Final)

### Requisitos Funcionais (RF)

*   **RF01 – Autenticação via Google:** Permitir acesso exclusivo por meio de conta Google (via Credential Manager + Firebase Auth), seguindo a premissa de login social único e vinculando o usuário a um uid único no sistema.
*   **RF02 – Tela Inicial (Dashboard):** Exibir atalho principal para iniciar gravação de treino, resumo diário, timeline de treinos da semana, progresso da meta semanal, contador de sequência de dias (streak), card de liga/posição atual no ranking e navegação para as demais áreas.
*   **RF03 – Gravação e Monitoramento de Treino:** Permitir selecionar a modalidade (Caminhada, Corrida ou Academia), adicionar descrição do treino, configurar e visualizar leituras de sensores em tempo real, controlar a sessão (iniciar, pausar, retomar, finalizar) e visualizar o trajeto com pontos marcados no mapa nas modalidades externas.
*   **RF04 – Armazenamento Offline-First e Fila de Sincronização:** Salvar localmente no dispositivo os dados consolidados da sessão (métricas de sensores, pontos de GPS, usuário e data/hora) e gerenciar uma fila automática de envio para o Firebase Cloud Firestore, indicando visualmente sessões pendentes de sincronização.
*   **RF05 – Pontuação, Nota do Perfil e Gamificação:** Calcular a pontuação de cada sessão aplicando pesos diferenciados por modalidade, atualizar a sequência de dias (streak) e o progresso das metas semanais, manter histórico de atividades e recalcular a nota/média contínua do perfil com base na frequência e intensidade dos treinos.
*   **RF06 – Rankings Mensais e Ligas:** Apresentar listagem de classificação individual com alternância entre Global, Amigos e Ligas. Consolidar a somatória mensal com histórico preservado e zeragem automática a cada virada de mês, mantendo intacta a pontuação média do perfil.
*   **RF07 – Amigos e Grupos de Classificação:** Listar amigos cadastrados no app, permitir adicionar amigos (via busca por @username/código ou link de convite) e criar ou participar de grupos personalizados de ranking — tanto privados (amigos) quanto abertos a outros usuários online.
*   **RF08 – Menu e Configurações:** Centralizar edição de perfil, preferências de treino e metas semanais, notificações (lembretes de streak e ranking), calibração/status dos sensores, ajuda/sobre e logout.

### Requisitos Não Funcionais (RNF)

*   **RNF01 – Plataforma e Stack:** Aplicativo nativo Android desenvolvido em Kotlin com Jetpack Compose (Material 3), persistência local via Room e backend em Firebase (Auth + Cloud Firestore).
*   **RNF02 – Execução em Segundo Plano:** A captura de acelerômetro e GPS deve rodar em um Foreground Service com notificação persistente, impedindo que o Android interrompa o treino com a tela bloqueada.
*   **RNF03 – Eficiência de Dados e Bateria:** Leituras brutas de sensores devem ser agregadas em janelas temporais antes de persistir na nuvem, evitando exceder o limite de 1 MB por documento no Firestore e reduzindo consumo de bateria/rede.
*   **RNF04 – Idempotência na Sincronização:** Cada sessão gerada offline deve possuir um identificador único (UUID) criado no cliente, garantindo que reenvios após quedas de conexão nunca dupliquem treinos ou pontos.
*   **RNF05 – Permissões e Transparência:** Solicitar permissões em tempo de execução (ACCESS_FINE_LOCATION, ACTIVITY_RECOGNITION e POST_NOTIFICATIONS) com explicação clara antes do pedido, degradando graciosamente caso o usuário negue GPS (permitindo gravar apenas com acelerômetro).

## 3. Lógica de Monitoramento, Sensores e Pontuação

### Captura e Tratamento de Sensores

Para manter a implementação enxuta, confiável e com bom rendimento acadêmico, vale separar a coleta em três fontes do Android:

*   **Movimento (Acelerômetro):** Usar `Sensor.TYPE_LINEAR_ACCELERATION` (que já remove a gravidade $9{,}8\text{ m/s}^2$ via fusão do sistema, com fallback para `TYPE_ACCELEROMETER` + filtro passa-alta caso o aparelho não possua giroscópio). Em cada amostra, calcula-se a magnitude do vetor:
    $$a = \sqrt{x^2 + y^2 + z^2}$$
    Em vez de gravar 50 leituras por segundo, acumule os valores em janelas de 5 segundos e calcule a média de intensidade da janela. Isso gera apenas 12 pontos por minuto — leve o suficiente para gerar gráficos na tela e salvar no banco.
*   **Passos:** Priorizar `Sensor.TYPE_STEP_DETECTOR` (dispara um evento a cada passo real com baixíssimo consumo de bateria). Caso o dispositivo/emulador não tenha o sensor de hardware, usar detecção de picos na magnitude $a$ (cruzamento de limiar + intervalo mínimo de 300ms entre passos).
*   **GPS e Pontos Marcados:** Usar `FusedLocationProviderClient` apenas em Caminhada e Corrida, configurado com `Priority.PRIORITY_HIGH_ACCURACY`, intervalo de 3000ms e deslocamento mínimo de 5m (`setMinUpdateDistanceMeters(5f)`). Descarte pontos com `accuracy > 25f` para evitar "zigue-zagues" falsos no mapa. Na modalidade Academia, o GPS permanece desligado.

### Regras de Pontuação por Modalidade e Gamificação

Para que quem faz musculação possa competir de forma justa com corredores, cada modalidade usa um multiplicador de peso ($W$) e métricas compatíveis:

| Modalidade | Sensores Ativos | Peso (W) | Base do Cálculo da Sessão |
| :--- | :--- | :--- | :--- |
| **Caminhada** | GPS + Passos + Acelerômetro | 1.0 | Duração ativa + distância percorrida + passos |
| **Academia** | Acelerômetro + Timer Ativo | 1.3 | Duração ativa + índice de intensidade de movimento |
| **Corrida** | GPS + Passos + Acelerômetro | 1.6 | Duração ativa + distância percorrida + cadência/intensidade |

Uma fórmula simples, transparente e fácil de defender na banca é:
$$\text{Pontos} = \text{round}\Big(\big(\text{MinAtivos} \times 10 + \text{DistKm} \times 25 + \text{IntensidadeMédia} \times 5\big) \times W\Big)$$
(Na modalidade Academia, $\text{DistKm} = 0$, mas o peso $1{,}3$ somado à duração ativa e à intensidade média das séries equilibra a pontuação sem exigir deslocamento).

*   **Validação mínima:** Apenas treinos com duração $\ge 5\text{ min}$ (ou $\ge 1\text{ min}$ em modo debug para apresentação) contabilizam para streak, metas e ranking.
*   **Sequência de Dias (Streak):** Armazene `lastWorkoutDate` (formato YYYY-MM-DD no fuso local). Ao concluir um treino: se a data for igual à última, mantém o streak; se for o dia seguinte, incrementa +1; se passou mais de 1 dia, reinicia para 1.
*   **Metas Semanais:** Meta padrão configurável (ex.: 3 treinos ou 500 pontos de segunda a domingo). Ao atingir 100%, o usuário ganha um bônus fixo (ex.: +100 pts) no ranking mensal.
*   **Ligas e Nota do Perfil:**
    *   **Ligas Mensais:** Faixas automáticas pela soma do mês (ex.: Bronze 0–499, Prata 500–1499, Ouro 1500–2999, Diamante 3000+).
    *   **Nota do Perfil (0 a 100):** Média ponderada simples da constância nos últimos 30 dias — por exemplo, combinando percentual de dias ativos no mês (60%) e média de pontos por treino frente a um teto de referência (40%). Assim, mesmo quando o ranking mensal zera, o perfil reflete o histórico real do atleta.

## 4. Arquitetura Offline-First e Regras de Sincronização

Para não depender de servidores próprios nem do plano pago (Blaze) do Firebase Cloud Functions, a arquitetura abaixo resolve offline-first, fila confiável e reset mensal automático usando apenas Room + WorkManager + Firestore.

### Fluxo de Dados (Single Source of Truth)

A interface em Jetpack Compose nunca lê diretamente do Firestore para treinos do próprio usuário — ela observa Flows vindos do Room. Isso garante abertura instantânea e uso 100% offline.

*   **Durante o treino (ForegroundService):** A cada 15–30 segundos, faz checkpoint dos dados parciais no Room (`status = RECORDING`). Se a bateria acabar ou o app fechar, o treino pode ser recuperado.
*   **Ao finalizar:** O app calcula o resumo (pontos, duração, distância, passos, intensidade média e lista enxuta de coordenadas GPS ou Encoded Polyline), muda o registro local para `syncStatus = PENDING` com um `workoutId = UUID.randomUUID()` e dispara imediatamente um `OneTimeWorkRequest` no WorkManager com restrição `NetworkType.CONNECTED` e política `BackoffPolicy.EXPONENTIAL`.

**Envio pela Fila (SyncWorkoutWorker):**
1. Busca no Room todos os treinos `PENDING` ordenados por timestamp crescente.
2. Para cada treino, executa uma Firestore Transaction atômica que faz três escritas de uma vez:
    *   Grava o documento em `workouts/{workoutId}` (usar `.set()` com o UUID garante que um retry nunca crie duplicatas).
    *   Incrementa a pontuação mensal do usuário em `monthly_rankings/{YYYY-MM}/entries/{uid}` usando o mês da data do treino.
    *   Atualiza o documento `users/{uid}` (novo streak, profileScore, weeklyProgress e totalWorkouts).
    *   Se o usuário pertencer a grupos de classificação, também incrementa os respectivos documentos em `groups/{groupId}/rankings/{YYYY-MM}/entries/{uid}` dentro do mesmo lote.
3. Só após confirmação do Firestore o Room marca o treino como `syncStatus = SYNCED`.

**Reset Mensal sem Cron Job:** Como os rankings são particionados pela chave do mês (YYYY-MM, ex.: 2026-11), na virada para 2026-12 o app simplesmente passa a consultar e gravar na coleção 2026-12. O mês anterior permanece congelado automaticamente como histórico para consulta, sem precisar de nenhum script agendado no servidor para zerar contadores.

### Resumo das Coleções no Firestore

*   `users/{uid}` — nome, foto, @username, profileScore, currentStreak, lastWorkoutDate, weeklyGoal.
*   `workouts/{workoutId}` — uid, modality, description, startedAt, durationSec, distanceMeters, steps, avgIntensity, points, monthKey, routePolyline.
*   `monthly_rankings/{YYYY-MM}/entries/{uid}` — uid, displayName, photoUrl, points, workoutsCount, league.
*   `groups/{groupId}` — name, isPublic, inviteCode, ownerUid, memberUids (com subcoleção `rankings/{YYYY-MM}/entries/{uid}`).
