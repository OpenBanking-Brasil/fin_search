import argparse
import base64
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build


SCOPES = ["https://www.googleapis.com/auth/gmail.readonly"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Baixa anexos CSV da Nubank do Gmail com OAuth."
    )
    parser.add_argument(
        "--credentials",
        default="client_secret.json",
        help="Arquivo OAuth client secret baixado do Google Cloud.",
    )
    parser.add_argument(
        "--token",
        default="token.json",
        help="Arquivo de token OAuth (será criado automaticamente).",
    )
    parser.add_argument(
        "--output-dir",
        default="downloads",
        help="Pasta de saída para salvar os CSVs.",
    )
    parser.add_argument(
        "--query",
        default='from:nubank has:attachment filename:csv newer_than:90d',
        help="Consulta Gmail para filtrar e-mails.",
    )
    parser.add_argument(
        "--max-results",
        type=int,
        default=25,
        help="Quantidade máxima de mensagens para buscar.",
    )
    parser.add_argument(
        "--latest-only",
        action="store_true",
        help="Baixa apenas o CSV mais recente encontrado.",
    )
    return parser.parse_args()


def sanitize_filename(value: str) -> str:
    value = re.sub(r"[^\w.\-]+", "_", value.strip(), flags=re.UNICODE)
    return value[:180] or "arquivo.csv"


def authenticate(credentials_path: Path, token_path: Path) -> Credentials:
    creds: Optional[Credentials] = None
    if token_path.exists():
        creds = Credentials.from_authorized_user_file(str(token_path), SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                str(credentials_path), SCOPES
            )
            creds = flow.run_local_server(port=0)

        token_path.write_text(creds.to_json(), encoding="utf-8")

    return creds


def list_messages(service, query: str, max_results: int) -> List[Dict]:
    response = (
        service.users()
        .messages()
        .list(userId="me", q=query, maxResults=max_results)
        .execute()
    )
    return response.get("messages", [])


def collect_attachments(parts: List[Dict], output: List[Dict]) -> None:
    for part in parts:
        filename = part.get("filename", "")
        body = part.get("body", {})
        mime_type = part.get("mimeType", "")

        if part.get("parts"):
            collect_attachments(part["parts"], output)

        if filename and body.get("attachmentId"):
            output.append(
                {
                    "filename": filename,
                    "attachment_id": body["attachmentId"],
                    "mime_type": mime_type,
                }
            )


def extract_message_date(headers: List[Dict]) -> str:
    date_header = next(
        (h.get("value", "") for h in headers if h.get("name", "").lower() == "date"),
        "",
    )
    try:
        parsed = datetime.strptime(date_header[:25], "%a, %d %b %Y %H:%M:%S")
        return parsed.strftime("%Y-%m-%d")
    except ValueError:
        return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def download_attachment(service, message_id: str, attachment_id: str) -> bytes:
    attachment = (
        service.users()
        .messages()
        .attachments()
        .get(userId="me", messageId=message_id, id=attachment_id)
        .execute()
    )
    data = attachment.get("data", "")
    return base64.urlsafe_b64decode(data.encode("utf-8"))


def save_csv(content: bytes, output_dir: Path, prefix: str, filename: str) -> Path:
    cleaned = sanitize_filename(filename)
    if not cleaned.lower().endswith(".csv"):
        cleaned = f"{cleaned}.csv"

    target = output_dir / f"{prefix}_{cleaned}"
    counter = 1
    while target.exists():
        stem = target.stem
        suffix = target.suffix
        target = output_dir / f"{stem}_{counter}{suffix}"
        counter += 1

    target.write_bytes(content)
    return target


def main() -> int:
    args = parse_args()
    credentials_path = Path(args.credentials).resolve()
    token_path = Path(args.token).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if not credentials_path.exists():
        print(
            f"ERRO: arquivo de credenciais não encontrado em {credentials_path}. "
            "Baixe o OAuth Client JSON do Google Cloud e tente novamente."
        )
        return 1

    creds = authenticate(credentials_path, token_path)
    service = build("gmail", "v1", credentials=creds)
    messages = list_messages(service, args.query, args.max_results)

    if not messages:
        print("Nenhum e-mail encontrado para a consulta informada.")
        return 0

    saved_files: List[Path] = []
    for message_ref in messages:
        message_id = message_ref["id"]
        message = (
            service.users()
            .messages()
            .get(userId="me", id=message_id, format="full")
            .execute()
        )
        payload = message.get("payload", {})
        headers = payload.get("headers", [])
        prefix = extract_message_date(headers)

        attachments: List[Dict] = []
        collect_attachments(payload.get("parts", []), attachments)

        for item in attachments:
            filename = item["filename"]
            mime_type = item.get("mime_type", "").lower()
            if not filename.lower().endswith(".csv") and "csv" not in mime_type:
                continue

            binary = download_attachment(service, message_id, item["attachment_id"])
            path = save_csv(binary, output_dir, prefix, filename)
            saved_files.append(path)

            if args.latest_only:
                break

        if args.latest_only and saved_files:
            break

    if not saved_files:
        print("Nenhum anexo CSV foi encontrado nos e-mails retornados.")
        return 0

    print(f"CSV(s) salvo(s): {len(saved_files)}")
    for path in saved_files:
        print(f"- {path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
