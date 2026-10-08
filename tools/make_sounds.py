#!/usr/bin/env python3
"""Synthesises the game's sound effects into app/src/main/res/raw.

Run from the project root:  python3 tools/make_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def mix(into, samples, at=0.0):
    start = int(at * RATE)
    if len(into) < start + len(samples):
        into.extend([0.0] * (start + len(samples) - len(into)))
    for i, value in enumerate(samples):
        into[start + i] += value
    return into


def tone(freq, seconds, decay, harmonics=((1, 1.0),), attack=0.004, glide=0.0, vibrato=0.0):
    """Sum of harmonics with an exponential decay. `glide` bends the pitch over the note."""
    out = []
    phase = 0.0
    for i in range(int(RATE * seconds)):
        t = i / RATE
        f = freq * (1.0 + glide * t / seconds) * (1.0 + vibrato * math.sin(2 * math.pi * 6 * t))
        phase += 2 * math.pi * f / RATE
        envelope = min(1.0, t / attack) * math.exp(-t / decay)
        out.append(envelope * sum(a * math.sin(n * phase) for n, a in harmonics))
    return out


def noise(seconds, cutoff):
    """White noise through a one-pole low-pass; `cutoff` is a function of time (0..1) -> Hz."""
    rng = random.Random(7)
    out = []
    y = 0.0
    n = int(RATE * seconds)
    for i in range(n):
        alpha = min(1.0, 2 * math.pi * cutoff(i / n) / RATE)
        y += alpha * (rng.uniform(-1, 1) - y)
        out.append(y)
    return out


def save(name, samples, peak=0.85):
    top = max(abs(s) for s in samples) or 1.0
    fade = int(RATE * 0.006)
    frames = bytearray()
    for i, s in enumerate(samples):
        gain = peak / top * min(1.0, i / fade, (len(samples) - 1 - i) / fade)
        frames += struct.pack("<h", int(max(-1.0, min(1.0, s * gain)) * 32767))
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(bytes(frames))


BELL = ((1, 1.0), (2, 0.45), (3, 0.2), (4.2, 0.1))
BRASS = ((1, 1.0), (2, 0.6), (3, 0.4), (4, 0.25), (5, 0.15), (6, 0.08))

# ratchet click of the wheel and the needle
tick = [a * math.exp(-i / RATE / 0.0025) for i, a in enumerate(noise(0.03, lambda t: 6000))]
mix(tick, [0.5 * s for s in tone(2100, 0.03, 0.004)])
save("tick", tick, peak=0.7)

# soft pop for buttons
tap = tone(540, 0.09, 0.028, glide=-0.5)
mix(tap, [0.25 * s for s in tone(1300, 0.02, 0.004)])
save("tap", tap, peak=0.6)

# the screen sliding open or shut, ending in a little clack
slide_len = 0.5
slide = noise(slide_len, lambda t: 500 + 2600 * math.sin(math.pi * t))
slide = [s * math.sin(math.pi * i / len(slide)) ** 0.8 for i, s in enumerate(slide)]
mix(slide, [0.5 * s for s in tone(170, 0.09, 0.025)], at=slide_len - 0.06)
mix(slide, [0.3 * s for s in tick], at=slide_len - 0.06)
save("slide", slide, peak=0.6)

# 2 or 3 points: two rising bells
good = []
mix(good, tone(659.3, 0.6, 0.18, BELL))
mix(good, tone(987.8, 0.7, 0.22, BELL), at=0.11)
save("good", good)

# bullseye: a bright arpeggio with a sparkle on top
bullseye = []
for i, freq in enumerate((523.3, 659.3, 784.0, 1046.5)):
    mix(bullseye, tone(freq, 0.9, 0.22 if i < 3 else 0.4, BELL), at=i * 0.085)
mix(bullseye, [0.5 * s for s in tone(2093.0, 0.6, 0.2, BELL)], at=0.36)
mix(bullseye, [0.35 * s for s in tone(1568.0, 0.6, 0.2, BELL)], at=0.44)
save("bullseye", bullseye)

# miss: a sad two-note "wah wah"
miss = []
mix(miss, tone(233.1, 0.3, 0.2, BRASS, attack=0.02, glide=-0.05))
mix(miss, tone(196.0, 0.55, 0.3, BRASS, attack=0.02, glide=-0.09, vibrato=0.012), at=0.3)
save("miss", miss, peak=0.6)

# victory fanfare
win = []
for i, freq in enumerate((392.0, 523.3, 659.3, 784.0)):
    mix(win, tone(freq, 0.16, 0.12, BRASS, attack=0.01), at=i * 0.13)
for freq, gain in ((1046.5, 1.0), (784.0, 0.6), (659.3, 0.5), (523.3, 0.5)):
    mix(win, [gain * s for s in tone(freq, 1.3, 0.55, BRASS, attack=0.015, vibrato=0.004)], at=0.56)
mix(win, [0.4 * s for s in tone(2093.0, 0.8, 0.3, BELL)], at=0.62)
save("win", win)

print("wrote", sorted(os.listdir(OUT)))
