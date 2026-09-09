# SPDX-License-Identifier: Apache-2.0
# TODO: Tách FastAPI lifespan context manager ra khỏi main.py
# Khi implement, di chuyển logic từ main.py vào đây:
#
# from contextlib import asynccontextmanager
# from fastapi import FastAPI
# from app.cv.loader import download_model
#
# @asynccontextmanager
# async def lifespan(app: FastAPI):
#     app.state.plant_model = download_model()
#     yield
#     app.state.plant_model = None
