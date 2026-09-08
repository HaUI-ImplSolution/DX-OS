import torch
from PIL import Image
from torchvision import transforms

from src.models.download_model_ENB4C import download_model


# 1. Load model
model = download_model()

# 2. Load ảnh test
image = Image.open("test_image.jpg").convert("RGB")

# 3. Preprocess ảnh
transform = transforms.Compose([
    transforms.Resize((380, 380)),
    transforms.ToTensor(),
    transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225],
    ),
])

image_tensor = transform(image).unsqueeze(0)


# 4. Inference
with torch.no_grad():
    output = model(image_tensor)

# 5. Lấy class có xác suất cao nhất
predicted_class = output.argmax(dim=1).item()

print("Predicted class:", predicted_class)
print("Output shape:", output.shape)