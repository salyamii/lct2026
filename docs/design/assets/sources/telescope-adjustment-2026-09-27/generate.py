"""Generate an original, deterministic telescope adjustment SFX; no samples or dependencies.

Run with Python 3. Writes the lossless source next to this script. MP3 encoding is
documented in README.md and media-manifest.json, not performed by the app.
"""

import math
from pathlib import Path
import random
import struct
import wave


SAMPLE_RATE = 44100
DURATION_SECONDS = 1.25
SEED = 20260927
PEAK_DBFS = -9.0


def synthesize():
    rng = random.Random(SEED)
    samples = []
    # Irregular, damped detents: a hand turns a focusing knob, then settles it.
    detents = ((0.0, 0.74), (0.135, 0.47), (0.31, 0.59), (0.56, 0.42), (0.89, 0.64))
    filtered_noise = 0.0
    softer_noise = 0.0
    for index in range(round(SAMPLE_RATE * DURATION_SECONDS)):
        time = index / SAMPLE_RATE
        filtered_noise += 0.28 * (rng.uniform(-1.0, 1.0) - filtered_noise)
        softer_noise += 0.09 * (filtered_noise - softer_noise)
        value = 0.0
        for start, strength in detents:
            age = time - start
            if not 0 <= age < 0.28:
                continue
            # A 4 ms rounded attack and muted, non-harmonic resonances avoid a harsh impulse.
            attack = 1.0 - math.exp(-age / 0.004)
            body = (0.54 * math.sin(math.tau * 417 * age) * math.exp(-age / 0.054)
                    + 0.27 * math.sin(math.tau * 793 * age) * math.exp(-age / 0.035)
                    + 0.15 * math.sin(math.tau * 1327 * age) * math.exp(-age / 0.022))
            contact = filtered_noise * 0.72 * math.exp(-age / 0.042)
            value += strength * attack * (body + contact)
        # Quiet friction underneath the knob movement, without a pitched or musical bed.
        for start, length in ((0.015, 0.37), (0.48, 0.29), (0.80, 0.36)):
            phase = (time - start) / length
            if 0 < phase < 1:
                value += 0.35 * softer_noise * math.sin(math.pi * phase) ** 2
        # The last 60 ms are explicitly faded so encoding cannot end on a discontinuity.
        fade = min(1.0, max(0.0, (DURATION_SECONDS - time) / 0.06))
        samples.append(value * fade)
    mean = sum(samples) / len(samples)
    centered = [value - mean for value in samples]
    gain = 10 ** (PEAK_DBFS / 20) / max(abs(value) for value in centered)
    return [round(max(-1.0, min(1.0, value * gain)) * 32767) for value in centered]


def main():
    output = Path(__file__).with_name("telescope_adjustment.wav")
    samples = synthesize()
    with wave.open(str(output), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(SAMPLE_RATE)
        stream.writeframes(struct.pack(f"<{len(samples)}h", *samples))


if __name__ == "__main__":
    main()
