# SPDX-License-Identifier: Apache-2.0
# TODO: Định nghĩa FarmState — state object duy nhất được truyền qua tất cả nodes của LangGraph.
# State chỉ là dữ liệu TẠM THỜI của workflow, KHÔNG phải database.
# Dữ liệu lâu dài phải được persist xuống PostgreSQL qua repositories.
#
# from typing import TypedDict, Optional
#
# class FarmState(TypedDict):
#     # --- Input ---
#     request_id: str           # ID duy nhất cho mỗi request
#     actor_id: str             # ID của user thực hiện (từ JWT)
#     actor_role: str           # FARMER | ENGINEER | ADMIN
#     parcel_code: str          # Mã thửa ruộng
#     crop_season_id: str       # ID vụ mùa hiện tại
#     image_ref: str            # Path hoặc URL tới ảnh bệnh
#
#     # --- CV Step Output ---
#     cv_result: Optional[dict]         # {disease_code, disease_name, confidence, model_name, model_version}
#
#     # --- Data Step Output ---
#     field_context: Optional[dict]     # Parcel + CropSeason + Diagnosis + Treatment hiện tại
#     treatment_rule: Optional[dict]    # Disease -> TreatmentRule -> Pesticide (deterministic lookup)
#     rag_context: Optional[list]       # Danh sách context chunks từ pgvector
#
#     # --- LLM Step Output ---
#     recommendation: Optional[str]    # Văn bản khuyến nghị do LLM sinh (chỉ diễn đạt facts)
#
#     # --- Approval State ---
#     approval_status: Optional[str]    # PENDING | APPROVED | REJECTED
#     application_status: Optional[str] # None | APPLIED
#
#     # --- Final Output ---
#     response: Optional[dict]          # Kết quả trả về cho Backend Spring Boot
