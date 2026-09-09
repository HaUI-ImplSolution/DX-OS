from app.db.session import SessionLocal
from app.rag.rag_service import RAGService


def main():
    db = SessionLocal()

    try:
        rag = RAGService()

        result = rag.answer(
            db=db,
            question="Bệnh ghẻ táo có triệu chứng gì?",
            top_k=3,
        )

        print("\nQUESTION:")
        print(result["question"])

        print("\nANSWER:")
        print(result["answer"])

        print("\nSOURCES:")
        for source in result["sources"]:
            print(source)

    finally:
        db.close()


if __name__ == "__main__":
    main()