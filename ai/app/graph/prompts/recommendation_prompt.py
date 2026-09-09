# SPDX-License-Identifier: Apache-2.0
# TODO: Định nghĩa prompt template cho node generate_recommendation.
# Nguyên tắc bắt buộc:
# - LLM CHỈ diễn đạt lại facts đã được cung cấp (CV result, TreatmentRule, RAG context).
# - LLM KHÔNG ĐƯỢC tự sinh liều lượng, tên thuốc, số ngày cách ly (PHI) hay bất kỳ số liệu định lượng nào.
# - Output phải là văn bản tiếng Việt dễ hiểu cho kỹ sư nông nghiệp review.
