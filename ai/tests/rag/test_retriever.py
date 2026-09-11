from app.db.session import SessionLocal
from app.rag.retriever import RAGRetriever


def main():
    db = SessionLocal()

    try:
        retriever = RAGRetriever()

        query = "Bệnh ghẻ táo có triệu chứng gì?"

        results = retriever.retrieve(
            db=db,
            query=query,
            top_k=5,
        )

        print(f"\nQuery: {query}\n")

        for i, item in enumerate(results, start=1):
            print("=" * 70)
            print(f"TOP {i}")
            print(f"Similarity: {item['similarity']:.4f}")
            print(item["content"])

    finally:
        db.close()


if __name__ == "__main__":
    main()
