from app.application.workflow_runner import WorkflowRunner


def test_initial_state_contains_only_workflow_business_data():
    state = WorkflowRunner.build_initial_state(
        request_id="request-1",
        parcel_code="P-001",
        crop_season_id="123e4567-e89b-12d3-a456-426614174000",
        disease_report_id="123e4567-e89b-12d3-a456-426614174001",
        image_bytes=b"image",
        rag_question="What should I do?",
    )

    assert state["request_id"] == "request-1"
    assert state["image_ref"] == b"image"
    assert state["rag_question"] == "What should I do?"
    assert not {
        "db",
        "vision_tool",
        "field_context_tool",
        "treatment_tool",
        "rag_tool",
        "recommendation_writer",
    } & state.keys()
