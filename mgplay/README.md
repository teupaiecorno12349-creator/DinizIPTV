# MG Play

Aplicativo Android TV independente, com acesso local permanente, reprodução Media3 e painel HTML incorporado.

## Recursos desta primeira versão

- Interface MG Play adaptada para controle remoto e telas de TV.
- Player Media3 para URLs HTTP/HTTPS de canais, filmes e episódios.
- Cadastro de fontes Xtream Codes e M3U/M3U8 pelo painel HTML.
- Leitura de canais, filmes e séries Xtream; leitura de entradas M3U.
- Favoritos locais.
- Identificador MAC virtual persistente e chave individual do aparelho.
- Painel HTML servido pelo próprio Android na porta TCP 8765.
- Credenciais das listas e cache de catálogo criptografados com Android Keystore.
- Sem contador de teste ou data de expiração implementados no MG Play.

## Primeiro uso

1. Instale e abra o MG Play na TV Box.
2. Entre em **Configurações** e anote o MAC MG Play e a chave do dispositivo.
3. No celular ou computador conectado à mesma rede, abra `http://IP-DA-TV-BOX:8765/`.
4. Entre com o MAC e a chave exibidos no aplicativo.
5. Cadastre uma fonte Xtream Codes ou M3U e salve.
6. Volte ao MG Play e selecione **Atualizar listas**.

O painel é hospedado localmente pelo próprio aplicativo. Nesta versão, o navegador e a TV Box precisam estar na mesma rede, e o processo do MG Play precisa estar ativo. A porta 8765 não deve ser encaminhada para a internet.

O MAC exibido é um identificador virtual estável do MG Play, não o endereço físico do adaptador Wi-Fi/Ethernet, que o Android moderno restringe aos aplicativos comuns.

## Compilação

O workflow `.github/workflows/build-mgplay.yml` compila `app-debug.apk` no GitHub Actions e publica o APK e o pacote HTML como o artefato `MG-Play-Android-TV`.

## Limitações conhecidas da primeira versão

- A interface e o player são uma implementação independente; não reutilizam bibliotecas proprietárias do IBO Pro.
- Categorias e listas Xtream dependem das ações padrão disponibilizadas pelo servidor do provedor.
- Algumas URLs M3U com cabeçalhos HTTP especiais podem exigir suporte adicional.
- O painel é local à rede; hospedagem pública com sincronização entre redes exige um backend externo e domínio/infraestrutura próprios.