"""Package only the static UI, with an isolated configuration for public review."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
output = root / "_site"
if output.exists():
    shutil.rmtree(output)
shutil.copytree(root / "src/main/resources/static", output)
(output / "assets/js/config/firebase-config.js").write_text(
    'window.PANEL_CONFIG = Object.freeze({reviewOnly: true, apiBase: "", firebase: {}});\n',
    encoding="utf-8",
)
(output / ".nojekyll").touch()
print("Review site ready in _site (sample data only)")
