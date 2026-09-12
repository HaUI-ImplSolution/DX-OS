import torch

from src.cv.class_names import CLASS_NAMES


def test_class_names_contains_38_classes():
    """Class mapping should contain exactly 38 classes."""

    assert len(CLASS_NAMES) == 38

    assert set(CLASS_NAMES.keys()) == set(range(38))


def test_class_names_contains_tomato_classes():
    """Tomato disease classes should be mapped correctly."""

    assert CLASS_NAMES[28] == "Tomato___Bacterial_spot"
    assert CLASS_NAMES[29] == "Tomato___Early_blight"
    assert CLASS_NAMES[30] == "Tomato___Late_blight"
    assert CLASS_NAMES[37] == "Tomato___healthy"


class FakeModel:
    """Small fake model returning deterministic logits."""

    def __call__(self, x):
        output = torch.zeros((x.shape[0], 38))

        # Class 29 has the highest score
        output[:, 29] = 10.0

        return output


def test_model_prediction_returns_expected_class():
    """Prediction should select the class with the highest logit."""

    model = FakeModel()

    image_tensor = torch.zeros((1, 3, 380, 380))

    with torch.no_grad():
        output = model(image_tensor)

    predicted_class = output.argmax(dim=1).item()

    assert predicted_class == 29
    assert CLASS_NAMES[predicted_class] == "Tomato___Early_blight"


def test_model_output_shape():
    """Model output should contain 38 class logits."""

    model = FakeModel()

    image_tensor = torch.zeros((1, 3, 380, 380))

    output = model(image_tensor)

    assert output.shape == (1, 38)


def test_model_prediction_is_valid_class():
    """Prediction should always map to a known class."""

    model = FakeModel()

    image_tensor = torch.zeros((1, 3, 380, 380))

    output = model(image_tensor)

    predicted_class = output.argmax(dim=1).item()

    assert predicted_class in CLASS_NAMES