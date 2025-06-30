# Simulador BitTorrent P2P

Simulação do protocolo BitTorrent em Java com tracker centralizado e peers que compartilham arquivos usando algoritmos como tit-for-tat e rarest-first.

## 🚀 Como Executar

### 1. Configurar e Iniciar o Tracker

1. Abra o projeto no **VS Code**
2. Configure a porta no arquivo `TrackerMain.java` linha 11
3. Execute o método `main` da classe `TrackerMain`

### 2. Configurar e Iniciar Peers

1. Configure IP e porta do tracker no arquivo `PeerMain.java` linhas 11 e 12
2. Execute o método `main` da classe `PeerMain`
3. Repita para criar múltiplos peers (execute várias vezes)

## 📁 Gerenciamento de Arquivos

### Estrutura de Pastas
- Arquivos dos peers ficam em: `peerFiles/<NomeDoPeer>/`
- Cada peer só acessa arquivos de sua própria pasta

### Usando o FileUtils

O `FileUtils.java` ajuda a gerar arquivos de teste rapidamente:

1. Execute o método `main` da classe `FileUtils`
2. **Importante**: Os arquivos são gerados em `peerFiles/PEER_1/` (pasta de exemplo)
3. **Mova manualmente** os arquivos gerados para a pasta do peer que deseja testar
   - Exemplo: de `peerFiles/PEER_1/` para `peerFiles/SeuPeer/`

## ⚙️ Funcionalidades

- **Tracker**: Coordena a rede, registra peers, facilita descoberta
- **Peers**: Compartilham arquivos com algoritmos BitTorrent (tit-for-tat, rarest-first, choking/unchoking)

## 📝 Resumo dos Passos

1. **VS Code**: Abra o projeto
2. **TrackerMain**: Configure porta → Execute main
3. **PeerMain**: Configure IP/porta do tracker → Execute main
4. **FileUtils**: Gere arquivos → Mova para pasta correta do peer
5. **Teste**: Execute múltiplos peers para ver o compartilhamento

Pronto! Os peers se conectarão automaticamente e começarão a compartilhar arquivos.