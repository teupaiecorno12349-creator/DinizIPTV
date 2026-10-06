# Samsung Orsay Widget Installer

Aplicativo Android para selecionar uma pasta de pendrive/OTG, localizar pacotes .zip/.wgt de widgets Orsay, publicar um widgetlist.xml compatível e servir os pacotes pela rede local.

## Alvo

Samsung Orsay, incluindo a família J4290/J4200 legada. O método usado pelo Orsay é sincronização de aplicativos via um servidor HTTP local; não é instalação de APK na TV.

## Limitação importante

O protocolo de sincronização legado normalmente acessa HTTP na porta 80. Android 10 sem root não permite que um app comum escute diretamente nessa porta. Por isso o aplicativo tenta, nesta ordem:

1. porta 80 diretamente (quando o Android permite);
2. servidor em 8080 + redirecionamento 80→8080 quando o aparelho tem root e iptables;
3. 8080 apenas para teste pelo navegador, avisando que isso pode não funcionar com o User Application Synchronization do Orsay.

