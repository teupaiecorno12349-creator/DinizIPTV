# DinizIPTV

Projeto Android com três aplicativos independentes:
- Diniz IPTV Player para TV Box.
- Diniz IPTV Admin para gerenciamento de contas e catálogo.
- Diniz DNS para selecionar Google DNS ou Cloudflare DNS em TV Boxes Android.

## Diniz DNS

O aplicativo usa a API oficial `VpnService` do Android para criar uma interface VPN local, sem root. Somente os endereços dos resolvedores DNS selecionados são encaminhados pela interface; o restante do tráfego de internet continua na conexão normal. As consultas DNS UDP são enviadas diretamente aos IPs oficiais selecionados e as respostas são repassadas sem adulteração.

Na primeira ativação, o Android solicita autorização para a VPN local. O botão de desligar encerra a interface e devolve a resolução DNS à rede. O recurso não altera a configuração DHCP do roteador e não substitui o DNS privado/DoT do Android quando este estiver forçado pelo sistema.

## Compilação

O GitHub Actions compila os três APKs de depuração e publica o artefato `Diniz-Android-APKs`.
