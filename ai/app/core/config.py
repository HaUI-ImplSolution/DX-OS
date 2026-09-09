# SPDX-License-Identifier: Apache-2.0
# TODO: Implement centralized settings using pydantic-settings
# Load từ .env file, tập trung mọi config của AI Service tại đây.
#
# Ví dụ:
# from pydantic_settings import BaseSettings
#
# class Settings(BaseSettings):
#     DATABASE_URL: str
#     OLLAMA_BASE_URL: str = "http://localhost:11434"
#     OLLAMA_MODEL: str = "qwen2.5:1.5b"
#     HF_REPO_ID: str = "Khawajaa/plant-disease-detector"
#     WEIGHTS_FILENAME: str = "best_model.pth"
#     CV_NUM_CLASSES: int = 38
#     CV_IMG_SIZE: int = 224
#     LOG_LEVEL: str = "INFO"
#
#     class Config:
#         env_file = ".env"
#
# settings = Settings()
