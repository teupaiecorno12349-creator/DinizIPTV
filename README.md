# DinizIPTV

Projeto Android com três aplicativos independentes:
- Diniz IPTV Player para TV Box, integrado ao painel público Lovable e ao catálogo Xtream Codes.
- Diniz IPTV Admin para gerenciamento de contas e catálogo.
- Diniz DNS para selecionar Google DNS ou Cloudflare DNS em TV Boxes Android.

## Diniz DNS

O aplicativo usa a API oficial `VpnService` do Android para criar uma interface VPN local, sem root. Somente os endereços dos resolvedores DNS selecionados são encaminhados pela interface; o restante do tráfego de internet continua na conexão normal. As consultas DNS UDP são enviadas diretamente aos IPs oficiais selecionados e as respostas são repassadas sem adulteração.

Na primeira ativação, o Android solicita autorização para a VPN local. O botão de desligar encerra a interface e devolve a resolução DNS à rede. O recurso não altera a configuração DHCP do roteador e não substitui o DNS privado/DoT do Android quando este estiver forçado pelo sistema.

## Compilação

O GitHub Actions compila os três APKs de depuração e publica o artefato `Diniz-Android-APKs`.

## Integração do player com o painel Lovable

O Diniz IPTV Player usa o endereço publicado `https://track-hop-coin-chaser.lovable.app`.
O login do cliente é validado por `POST /api/public/player/login`; TV, filmes, séries,
detalhes e temporadas são consultados por `POST /api/public/player/catalog`.
O APK recebe o endereço-base do fornecedor da resposta de login e monta as URLs de
reprodução Xtream com usuário, senha, ID e extensão retornada pelo fornecedor.

As credenciais ficam somente em memória durante a sessão do aplicativo e são apagadas
ao sair da conta. O login é revalidado antes de cada reprodução, ao voltar ao player e
periodicamente durante a reprodução.

O provedor HTTP atualmente configurado no painel é `86kef6r9mb.cloud`. O Android
permite tráfego sem TLS somente para esse domínio; prefira HTTPS no fornecedor. Caso
o domínio HTTP do fornecedor seja alterado, atualize também
`player/src/main/res/xml/network_security_config.xml`.
