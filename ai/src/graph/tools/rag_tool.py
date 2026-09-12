# SPDX-License-Identifier: Apache-2.0
from src.rag.retriever import retrieve_knowledge


def retrieve(db, query, disease_code=None, top_k=3):
    return retrieve_knowledge(db, query, disease_code, top_k)
