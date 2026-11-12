# DinizIPTV

Projeto Android com quatro aplicativos independentes:
- Diniz IPTV Player para TV Box.
- Diniz IPTV Admin para gerenciamento de contas e catálogo.
- Diniz DNS para selecionar Google DNS ou Cloudflare DNS em TV Boxes Android.
- Diniz XCloud TV, um cliente de tela cheia para abrir o Xbox Cloud Gaming em TV Boxes.

## Diniz DNS

O aplicativo usa a API oficial `VpnService` do Android para criar uma interface VPN local, sem root. Somente os endereços dos resolvedores DNS selecionados são encaminhados pela interface; o restante do tráfego de internet continua na conexão normal. As consultas DNS UDP são enviadas diretamente aos IPs oficiais selecionados e as respostas são repassadas sem adulteração.

Na primeira ativação, o Android solicita autorização para a VPN local. O botão de desligar encerra a interface e devolve a resolução DNS à rede. O recurso não altera a configuração DHCP do roteador e não substitui o DNS privado/DoT do Android quando este estiver forçado pelo sistema.

## Diniz XCloud TV

O aplicativo abre `https://play.xbox.com/` em uma WebView Android em tela cheia, com aceleração de hardware, cookies persistentes, armazenamento local e suporte à navegação por controle remoto/controle Xbox. O login, catálogo, permissões, fila e início de cada jogo permanecem sob controle do serviço oficial Xbox.

O cliente não define a resolução nem garante 60 FPS: esses parâmetros dependem da sessão Xbox, da rede, do dispositivo e do decodificador disponível. O aplicativo não coleta credenciais Microsoft nem implementa endpoints privados de catálogo ou autenticação Xbox.

## Compilação

- O workflow `Build Android APKs` compila os três APKs originais.
- O workflow `Build Diniz XCloud TV` compila separadamente `Diniz-XCloud-TV.apk`.
