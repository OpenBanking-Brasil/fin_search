# Integração Gmail (Nubank CSV)

Este fluxo baixa automaticamente anexos `.csv` do Gmail usando OAuth.

## 1) Criar credenciais OAuth no Google Cloud

1. Abra o [Google Cloud Console](https://console.cloud.google.com/).
2. Crie (ou selecione) um projeto.
3. Ative a API **Gmail API**.
4. Vá em **APIs e Serviços > Credenciais**.
5. Crie credencial **OAuth Client ID** do tipo **Desktop app**.
6. Baixe o JSON e salve nesta pasta como `client_secret.json`.

## 2) Instalar dependências

No terminal, dentro de `tools/gmail_nubank`:

```powershell
pip install -r requirements.txt
```

## 3) Executar a coleta de CSV

```powershell
python fetch_nubank_csv.py --output-dir downloads --latest-only
```

Na primeira execução, abrirá o navegador para autorizar sua conta Google.

## 4) Consulta padrão

A consulta padrão é:

```text
from:nubank has:attachment filename:csv newer_than:90d
```

Para customizar:

```powershell
python fetch_nubank_csv.py --query "from:nubank has:attachment filename:csv newer_than:30d"
```

## 5) Próximo passo

Após baixar os arquivos, me envie o caminho de `downloads` e eu faço a análise financeira (30 dias vs 30 anteriores).
