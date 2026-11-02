#!/usr/bin/env python3
"""Apply small, verified Kotlin source fixes to the extracted ALPH Media project."""

from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: apply-fixes.py <extracted-project-directory>")

project = Path(sys.argv[1]).resolve()
overrides = Path(__file__).resolve().parent

video_source = overrides / "VideoTransformerEngine.kt"
video_target = (
    project
    / "app/src/main/java/com/alphmedia/app/media/editor/VideoTransformerEngine.kt"
)
if not video_source.is_file() or not video_target.is_file():
    raise SystemExit("VideoTransformerEngine.kt source or target was not found.")
video_target.write_text(video_source.read_text(encoding="utf-8"), encoding="utf-8")

metadata_target = (
    project
    / "app/src/main/java/com/alphmedia/app/media/editor/MediaStudioEngines.kt"
)
if not metadata_target.is_file():
    raise SystemExit("MediaStudioEngines.kt was not found.")

source = metadata_target.read_text(encoding="utf-8")

old_image = """            tempFile.delete()
        }
    }

    suspend fun updateAudioMediaStoreAndId3Tags("""
new_image = """            tempFile.delete()
            Unit
        }
    }

    suspend fun updateAudioMediaStoreAndId3Tags("""

old_audio = """            context.contentResolver.update(audioUri, values, null, null)
        }
    }
}"""
new_audio = """            context.contentResolver.update(audioUri, values, null, null)
            Unit
        }
    }
}"""

if source.count(old_image) != 1:
    raise SystemExit("Expected image metadata return expression was not found exactly once.")
if source.count(old_audio) != 1:
    raise SystemExit("Expected audio metadata return expression was not found exactly once.")

source = source.replace(old_image, new_image, 1)
source = source.replace(old_audio, new_audio, 1)
metadata_target.write_text(source, encoding="utf-8")

print("Applied Kotlin source fixes successfully.")
