#!/usr/bin/env python3
"""Synthesizes thaimer's sound cues into app/src/main/res/raw/*.wav.

Everything is generated (no samples), so the sounds are reproducible and license-free:
    python3 tools/gen_sounds.py

bell      – one strike of a boxing-ring bell: round start
bell_end  – three strikes: round over / workout over
clapper   – two wooden knocks: 10 seconds left in the round
pip       – soft short blip: 3-2-1 countdown
go        – soft "ding": next interval inside a round
burst     – double referee-whistle blast: explosion!
"""
import pathlib
import wave

import numpy as np

RATE = 44100
OUT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"
rng = np.random.default_rng(7)


def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def ramp(signal, attack=0.003, release=0.02):
    """Click-free edges."""
    n_a, n_r = int(attack * RATE), int(release * RATE)
    env = np.ones_like(signal)
    if n_a:
        env[:n_a] = np.linspace(0, 1, n_a)
    if n_r:
        env[-n_r:] = np.linspace(1, 0, n_r)
    return signal * env


def band_noise(seconds, center, width):
    """White noise shaped by a Gaussian band around `center` Hz."""
    n = int(seconds * RATE)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    spectrum *= np.exp(-0.5 * ((freqs - center) / width) ** 2)
    noise = np.fft.irfft(spectrum, n)
    return noise / (np.max(np.abs(noise)) + 1e-9)


def place(total_seconds, *events):
    """Mix (offset_seconds, signal) events into one buffer."""
    out = np.zeros(int(total_seconds * RATE))
    for offset, sig in events:
        start = int(offset * RATE)
        end = min(len(out), start + len(sig))
        out[start:end] += sig[: end - start]
    return out


def normalize(signal, peak):
    return signal * (peak / np.max(np.abs(signal)))


def bell_strike(seconds=2.4, f0=920.0):
    """Struck brass dome: inharmonic partials, each a slightly detuned pair so it shimmers."""
    t = t_axis(seconds)
    partials = [  # (ratio, amplitude, decay seconds)
        (1.00, 1.00, 1.9),
        (2.32, 0.55, 1.1),
        (4.25, 0.32, 0.55),
        (6.63, 0.16, 0.30),
        (9.38, 0.08, 0.16),
    ]
    sig = np.zeros_like(t)
    for ratio, amp, decay in partials:
        f = f0 * ratio
        env = amp * np.exp(-t / decay)
        sig += env * (np.sin(2 * np.pi * f * t) + 0.45 * np.sin(2 * np.pi * f * 1.0032 * t + 1.1))
    strike = band_noise(seconds, 3500, 1800) * np.exp(-t / 0.004) * 0.6
    return ramp(sig / 1.45 + strike, attack=0.0015, release=0.25)


def bell():
    return normalize(bell_strike(), 0.92)


def bell_end():
    strikes = [(0.00, 0.92), (0.30, 1.00), (0.60, 0.96)]
    mix = place(3.0, *[(at, bell_strike() * gain) for at, gain in strikes])
    return normalize(ramp(mix, release=0.3), 0.92)


def knock(seconds=0.12):
    """Wood block: a few fast-decaying modes plus a click."""
    t = t_axis(seconds)
    modes = [(870, 1.0, 0.030), (1640, 0.7, 0.020), (2470, 0.5, 0.012), (3910, 0.3, 0.007)]
    sig = sum(a * np.exp(-t / d) * np.sin(2 * np.pi * f * t) for f, a, d in modes)
    click = band_noise(seconds, 2500, 1500) * np.exp(-t / 0.0025)
    return ramp(sig + 0.8 * click, attack=0.0005, release=0.01)


def clapper():
    return normalize(place(0.32, (0.0, knock()), (0.15, knock() * 0.95)), 0.95)


def tone(seconds, freq, attack=0.004, release=0.03):
    t = t_axis(seconds)
    sig = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(2 * np.pi * 2 * freq * t) + 0.08 * np.sin(2 * np.pi * 3 * freq * t)
    return ramp(sig, attack, release)


def struck(seconds, freq, decay, partials=((1.0, 1.0), (2.0, 0.12), (3.0, 0.03))):
    """A tone that is hit and dies away, like a small marimba bar: no shrill sustained beep."""
    t = t_axis(seconds)
    sig = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * r / decay) for r, a in partials)
    return ramp(sig, attack=0.006, release=0.03)


def pip():
    return normalize(struck(0.16, 880.0, 0.07), 0.55)  # A5, a soft blip


def go():
    # Two quick notes a fifth apart (E5 → B5): "next!" without the ear-piercing sustain.
    first = struck(0.22, 659.3, 0.11)
    second = struck(0.45, 987.8, 0.16)
    return normalize(place(0.55, (0.0, first * 0.8), (0.09, second)), 0.6)


def whistle_blast(seconds):
    """Pea whistle: ~3 kHz tone warbled by the pea, with breath noise."""
    t = t_axis(seconds)
    trill = 27.0
    freq = 3050 + 170 * np.sin(2 * np.pi * trill * t)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    am = 0.72 + 0.28 * np.sin(2 * np.pi * trill * t + 0.6)
    sig = am * (np.sin(phase) + 0.18 * np.sin(2 * phase))
    breath = band_noise(seconds, 3100, 900) * 0.22
    return ramp(sig + breath, attack=0.012, release=0.045)


def burst():
    return normalize(place(0.72, (0.0, whistle_blast(0.22)), (0.29, whistle_blast(0.42))), 0.85)


LEAD_IN = 0.04


def write(name, signal):
    # A sliver of silence up front: when the audio output wakes from standby its first
    # milliseconds can be swallowed or pop, and that must not be the start of the beep.
    pcm = np.clip(np.concatenate([np.zeros(int(LEAD_IN * RATE)), signal]), -1, 1)
    data = (pcm * 32767).astype("<i2").tobytes()
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)
    rms = 20 * np.log10(np.sqrt(np.mean(pcm**2)) + 1e-9)
    print(f"{path.name:13s} {len(pcm) / RATE:5.2f}s  {path.stat().st_size // 1024:4d} KB  rms {rms:6.1f} dBFS")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("bell", bell), ("bell_end", bell_end), ("clapper", clapper), ("pip", pip), ("go", go), ("burst", burst)]:
        write(name, make())
