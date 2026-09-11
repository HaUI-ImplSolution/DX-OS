from app.rag.embedding_service import EmbeddingService


def main() -> None:
    service = EmbeddingService()
    vector = service.embed_text("Bệnh đốm nâu trên cây lúa")
    print(len(vector))


if __name__ == "__main__":
    main()
