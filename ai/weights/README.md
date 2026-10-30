# Model files

| File | Origin | License |
|---|---|---|
| `dx-farm-efficientnetv2m/` | Fine-tuned by the team from the Torchvision `EfficientNet_V2_M_Weights`; see its [README](./dx-farm-efficientnetv2m/README.md) | Apache-2.0 for the fine-tune; pretrained terms of Torchvision |
| `crop_classifier.onnx` | Hugging Face `RaiyanKhaan/KrishokTech-Models`, file `01_crop_classifier/crop_classifier.onnx` at commit `c339988a6eceb5e22943ee27e4731a1a1dc9160a` | AGPL-3.0 |

`crop_classifier.onnx` is the upstream file without changes.
Its SHA-256 is `b9c34acf76a3d8a04820d7f26c8318736478f54c568a655c09a73f55190837a5`, the same as the file at that commit.
The AI service rejects a file that does not match `AI_CROP_CLASSIFIER_SHA256` in `deploy/.env`.

The model card on Hugging Face says Apache-2.0, but the model was made with the Ultralytics library, which is AGPL-3.0.
The stricter AGPL-3.0 is applied to this file, the same license as the rest of `ai/`; see [`COPYRIGHT.txt`](../../COPYRIGHT.txt).
