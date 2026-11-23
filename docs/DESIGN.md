# Identidade Visual — MoviClub

O MoviClub combina a base editorial e esportiva de clubes de corrida (estilo minimalista e flat design com cores claras e neutras) com pontos focais de alta energia para sustentar a gamificação competitiva. A regra de ouro da interface é 85% neutro, 10% grafite estrutural e 5% laranja de destaque, garantindo leitura rápida durante treinos sem poluição visual.

## Paleta de Cores (Minimalismo Urbano — Pronta para Material 3)

| Papel no App (Compose / M3) | Nome | Hex | Uso Recomendado |
| :--- | :--- | :--- | :--- |
| **background** | Off-White Clean | #FAFAFA | Fundo principal das telas (reduz fadiga visual frente ao branco puro) |
| **surface** | Branco Puro | #FFFFFF | Cards de resumo diário, timeline e linhas de ranking |
| **surfaceVariant / outline** | Cinza Gelo / Borda | #F1F5F9 / #E2E8F0 | Campos de input, divisores e bordas sutis (1.dp) substituindo sombras pesadas |
| **primary / onBackground** | Grafite Profundo | #0F172A | Títulos, ícones ativos, textos principais e botão secundário sólido |
| **onSurfaceVariant** | Ardósia Médio | #475569 | Legendas, rótulos de sensores, datas e metadados |
| **secondary (Accent)** | Laranja Competição | #F97316 | CTA principal ("Iniciar Treino"), streak ativo, posição do usuário e barras de progresso |
| **Semântico — Positivo** | Verde Constância | #16A34A | Meta semanal concluída, subida no ranking, status "Sincronizado" |
| **Semântico — Atenção / Ouro** | Âmbar Liga | #D97706 | Selos de ligas, alertas de modo offline/fila pendente |
| **Semântico — Erro / Parar** | Vermelho Ação | #DC2626 | Botão de encerrar/descartar treino, queda de liga ou erro de sensor |

## Tipografia e Estilo de Componentes

*   **Inter (UI e Navegação):** Títulos em Bold/SemiBold com leve aperto no espaçamento de letras (-0.5sp), corpo em Regular (14sp–16sp) e rótulos em Medium (12sp).
*   **JetBrains Mono (Telemetria e Gamificação):** Exclusiva para cronômetro, distância, contagem de passos, leitura do acelerômetro, pontuação e posição no ranking. Use `fontFeatureSettings = "tnum"` no `TextStyle` do Compose para manter largura fixa nos dígitos e evitar que o layout vibre durante atualizações em tempo real.
*   **Geometria Flat:** Cards com `RoundedCornerShape(16.dp)` e borda de 1.dp (#E2E8F0) em vez de elevação pesada; botões principais em formato pílula (`CircleShape`) com altura mínima de 52.dp para facilitar o toque em movimento.
