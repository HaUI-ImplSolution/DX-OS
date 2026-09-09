from app.rag.embedding_service import EmbeddingService

service = EmbeddingService()

vector = service.embed_text(
    "Bệnh đốm nâu trên cây lúa"
)

print(len(vector))