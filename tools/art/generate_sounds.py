#!/usr/bin/env python3
"""Procedural placeholder OGGs for Mnemolith (Z1). Distinct tones, not AAA."""
import math, os, struct, subprocess, wave

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'mnemolith', 'sounds')
SR = 22050

def save_ogg(name, samples, stream=False):
    os.makedirs(OUT, exist_ok=True)
    path_wav = f'/tmp/{name}.wav'
    path_ogg = os.path.join(OUT, f'{name}.ogg')
    with wave.open(path_wav, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack('<h', int(v * 30000))
        w.writeframes(frames)
    q = '3' if stream else '4'
    subprocess.check_call(['ffmpeg', '-y', '-i', path_wav, '-c:a', 'libvorbis', '-q:a', q, path_ogg],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(name, os.path.getsize(path_ogg))

def tone(freq, dur, vol=0.5, attack=0.01, release=0.05, wave='sine'):
    n = int(SR * dur); out = []
    for i in range(n):
        t = i / SR
        env = 1.0
        if t < attack: env = t / attack
        elif t > dur - release: env = max(0.0, (dur - t) / release)
        phase = 2 * math.pi * freq * t
        if wave == 'sine': s = math.sin(phase)
        elif wave == 'square': s = 1.0 if math.sin(phase) >= 0 else -1.0
        elif wave == 'saw': s = 2 * ((freq * t) % 1.0) - 1.0
        else: s = math.sin(phase * 12.9898) * math.cos(phase * 78.233)
        out.append(s * vol * env)
    return out

def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, s in enumerate(t): out[i] += s
    return out

def pad(samples, total):
    return samples[:total] if len(samples) >= total else samples + [0.0] * (total - len(samples))

def music_loop(name, base_freq, seconds=12.0, dark=True):
    n = int(SR * seconds); out = []
    for i in range(n):
        t = i / SR
        lfo = 0.5 + 0.5 * math.sin(2 * math.pi * 0.08 * t)
        s = 0.0
        for mul, vol in ((1.0, 0.22), (1.5, 0.12), (2.0, 0.08), (0.5, 0.18)):
            s += math.sin(2 * math.pi * base_freq * mul * t) * vol * lfo
        if dark:
            s += math.sin(2 * math.pi * (base_freq * 0.25) * t) * 0.15
            s += math.sin(t * 1234.5) * math.cos(t * 987.6) * 0.04 * lfo
        edge = min(t, seconds - t, 1.5) / 1.5
        out.append(s * max(0.0, min(1.0, edge)))
    save_ogg(name, out, stream=True)

def main():
    save_ogg('mite_ambient', mix(tone(1800, 0.08, 0.35, 0.005, 0.03, 'square'),
        pad(tone(2400, 0.05, 0.25, 0.002, 0.02, 'sine'), int(0.18*SR)) + tone(2100, 0.06, 0.3, 0.002, 0.02, 'square')))
    save_ogg('mite_hurt', tone(900, 0.12, 0.45, 0.005, 0.04, 'saw'))
    save_ogg('witness_ambient', mix(tone(110, 0.55, 0.35, 0.08, 0.2), tone(165, 0.55, 0.18, 0.1, 0.2), tone(55, 0.55, 0.2, 0.05, 0.25, 'saw')))
    save_ogg('witness_hurt', mix(tone(80, 0.2, 0.5, 0.01, 0.08, 'saw'), tone(140, 0.15, 0.3, 0.01, 0.05, 'square')))
    save_ogg('stalker_ambient', mix(tone(60, 0.7, 0.4, 0.15, 0.25, 'saw'), tone(90, 0.7, 0.15, 0.2, 0.3, 'noise'), tone(45, 0.7, 0.25, 0.1, 0.3)))
    save_ogg('stalker_hurt', tone(70, 0.25, 0.55, 0.01, 0.1, 'saw'))
    save_ogg('stalker_death', mix(tone(50, 0.6, 0.55, 0.02, 0.35, 'saw'), tone(35, 0.6, 0.35, 0.05, 0.4)))
    save_ogg('scar_ambient', mix(tone(95, 0.8, 0.3, 0.2, 0.3), tone(190, 0.8, 0.12, 0.25, 0.3), tone(48, 0.8, 0.2, 0.1, 0.4, 'saw')))
    save_ogg('scar_hurt', tone(130, 0.2, 0.5, 0.01, 0.08, 'square'))
    save_ogg('scar_death', mix(tone(70, 0.7, 0.5, 0.02, 0.4, 'saw'), tone(40, 0.7, 0.35, 0.05, 0.45)))
    save_ogg('scar_cast', mix(tone(220, 0.45, 0.4, 0.05, 0.15), tone(110, 0.45, 0.35, 0.08, 0.2, 'saw'), tone(440, 0.2, 0.2, 0.02, 0.1)))
    save_ogg('storm_gather', mix(tone(55, 1.2, 0.45, 0.3, 0.4, 'saw'), tone(82, 1.2, 0.25, 0.4, 0.4), tone(30, 1.2, 0.3, 0.2, 0.5, 'noise')))
    save_ogg('storm_wave', mix(tone(100, 0.5, 0.4, 0.05, 0.2, 'saw'), tone(200, 0.35, 0.25, 0.05, 0.15)))
    save_ogg('relay_tie', mix(tone(660, 0.15, 0.35, 0.01, 0.05), tone(990, 0.1, 0.25, 0.01, 0.04)))
    save_ogg('relay_untie', mix(tone(520, 0.15, 0.35, 0.01, 0.05), tone(390, 0.12, 0.25, 0.01, 0.05)))
    save_ogg('relay_hop', mix(tone(880, 0.12, 0.4, 0.005, 0.04, 'square'), tone(1320, 0.08, 0.25, 0.005, 0.03)))
    save_ogg('relay_break', mix(tone(200, 0.25, 0.45, 0.01, 0.1, 'saw'), tone(100, 0.25, 0.3, 0.02, 0.12, 'noise')))
    save_ogg('vault_chime', mix(tone(523, 0.35, 0.35, 0.01, 0.25), tone(784, 0.3, 0.25, 0.02, 0.22)))
    save_ogg('vault_draw', mix(tone(392, 0.4, 0.35, 0.05, 0.2), tone(294, 0.4, 0.25, 0.08, 0.2, 'saw')))
    save_ogg('vault_rupture', mix(tone(150, 0.45, 0.5, 0.01, 0.2, 'saw'), tone(80, 0.45, 0.35, 0.02, 0.25, 'noise')))
    save_ogg('echo_wake', mix(tone(440, 0.25, 0.3, 0.02, 0.15), tone(660, 0.2, 0.2, 0.03, 0.12)))
    music_loop('music_storm_gathering', 55.0, 14.0, True)
    music_loop('music_scar_fight', 70.0, 12.0, True)
    music_loop('music_disc_recollection', 65.0, 16.0, True)

if __name__ == '__main__':
    main()
